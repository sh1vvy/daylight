package com.music.bitchord

import com.music.bitchord.data.BoundedRequestCache
import com.music.bitchord.data.SearchRequestKey
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.YtMusicRepository.SearchPage
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.Locale

/** Search latency, memory budgets and identity isolation without live network calls. */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchRequestCacheTest {
    @Test
    fun `in flight identities stop matching when the account scope or language settles`() {
        val oldCookie = Innertube.cookie
        val oldLanguage = Innertube.appLanguage
        try {
            Innertube.appLanguage = { "en" }
            val initial = SearchRequestKey.current("daylight")
            assertEquals(true, initial.isCurrent())
            Innertube.cookie = "SAPISID=daylight-scope-test-placeholder"
            assertEquals(false, initial.isCurrent())
            val scoped = SearchRequestKey.current("daylight")
            assertEquals(true, scoped.isCurrent())
            Innertube.appLanguage = { "hi" }
            assertEquals(false, scoped.isCurrent())
            assertEquals(true, SearchRequestKey.current("daylight").isCurrent())
        } finally {
            Innertube.cookie = oldCookie
            Innertube.appLanguage = oldLanguage
        }
    }

    private fun key(query: String, scope: Long = 1L, language: String = "en", filter: SearchFilter = SearchFilter.ALL) =
        SearchRequestKey.create(scope, language, query, filter)

    private fun TestScope.cache(maxEntries: Int = 12, maxRows: Int = 600, ttlMs: Long = 120_000L) =
        BoundedRequestCache<SearchRequestKey, SearchPage>(
            backgroundScope, ttlMs, maxEntries, maxRows, { it.rows.size }, { testScheduler.currentTime },
        )

    private fun page(vararg ids: String, continuation: String? = null) = SearchPage(
        ids.map { SearchResult.Track(Song(videoId = it, title = it, artist = "Artist", thumbnailUrl = null)) }, continuation,
    )

    @Test
    fun `backspacing and returning to a filter reuse rows immediately with no new round trip`() = runTest {
        val cache = cache()
        var calls = 0
        suspend fun read(query: String, filter: SearchFilter = SearchFilter.ALL) = cache.get(key(query, filter = filter)) {
            calls++
            delay(300)
            page("$query-${filter.name}", continuation = "next-${filter.name}")
        }
        val typed = read("taylor")
        read("taylor swift")
        val songs = read("taylor", SearchFilter.SONGS)
        val before = testScheduler.currentTime
        assertSame(typed, cache.peek(key("  TAYLOR  ")))
        assertSame(typed, read("taylor"))
        assertSame(songs, read("taylor", SearchFilter.SONGS))
        assertEquals(0L, testScheduler.currentTime - before)
        assertEquals(3, calls)
        assertEquals("next-SONGS", songs.continuation)
    }

    @Test
    fun `accounts languages filters and non Latin queries remain distinct`() = runTest {
        val cache = cache()
        val keys = listOf(
            key("你好"), key("你好", scope = 2L), key("你好", language = "hi"),
            key("你好", filter = SearchFilter.VIDEOS), key("привет"), key("Taylor Swift"),
        )
        keys.forEachIndexed { index, query -> cache.put(query, page("row-$index")) }
        keys.forEachIndexed { index, query -> assertEquals("row-$index", (cache.peek(query)!!.rows.single() as SearchResult.Track).song.videoId) }
        assertNotEquals(key("你好"), key("привет"))
        assertNull(cache.peek(key("你好", scope = 3L)))
    }

    @Test
    fun `query normalization preserves Unicode and uses the same identity under a Turkish locale`() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            assertEquals(key("INDIGO"), key("indigo"))
            assertEquals("你好 мир", key("  你好   МИР  ").query)
            assertEquals(key("Taylor Swift"), key("Taylor\tSwift"))
        } finally {
            Locale.setDefault(old)
        }
    }

    @Test
    fun `cached preview expires and put cannot let an older request overwrite its continuation`() = runTest {
        val cache = cache(ttlMs = 1_000)
        val release = CompletableDeferred<Unit>()
        val query = key("taylor")
        val old = async { cache.get(query) { release.await(); page("old", continuation = "old-token") } }
        runCurrent()
        val newer = page("old", "new", continuation = "new-token")
        cache.put(query, newer)
        release.complete(Unit)
        old.await()
        assertSame(newer, cache.peek(query))
        advanceTimeBy(1_000)
        assertNull(cache.peek(query))
        assertEquals("fresh", (cache.get(query) { page("fresh") }.rows.single() as SearchResult.Track).song.videoId)
    }

    @Test
    fun `merged search pages preserve continuation and cannot grow the cache beyond its row budget`() = runTest {
        val cache = cache(maxEntries = 2, maxRows = 3)
        cache.put(key("a"), page("a1", "a2", continuation = "a-next"))
        cache.put(key("b"), page("b1"))
        cache.peek(key("a")) // The visible search is the most recent one.
        cache.put(key("c"), page("c1"))
        assertNull(cache.peek(key("b")))
        assertEquals("a-next", cache.peek(key("a"))!!.continuation)
        // An exceptionally long scrolled page remains visible to its caller,
        // but retaining it must not defeat the app's bounded memory budget.
        cache.put(key("a"), page("1", "2", "3", "4"))
        assertNull(cache.peek(key("a")))
    }

    @Test
    fun `simultaneous identical searches share one request instead of restarting it`() = runTest {
        val cache = cache()
        var calls = 0
        suspend fun read() = cache.get(key("daylight")) { calls++; delay(300); page("one") }
        val first = async { read() }
        val second = async { read() }
        assertSame(first.await(), second.await())
        assertEquals(1, calls)
        assertEquals(300L, testScheduler.currentTime)
    }
}
