package com.music.bitchord

import com.music.bitchord.data.BoundedRequestCache
import com.music.bitchord.data.innertube.Innertube
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Request counts and simulated latency, without depending on a live provider. */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowseRequestCacheTest {
    private fun <V> TestScope.cache(
        ttlMs: Long = 60_000,
        maxEntries: Int = 24,
        maxWeight: Int = 4_000,
        weightOf: (V) -> Int = { 1 },
    ) = BoundedRequestCache<String, V>(
        scope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job])),
        ttlMs = ttlMs,
        maxEntries = maxEntries,
        maxWeight = maxWeight,
        weightOf = weightOf,
        now = { testScheduler.currentTime },
    )

    @Test
    fun `reopening a three-page playlist performs no extra requests within the freshness window`() = runTest {
        val cache = cache<List<String>>()
        var calls = 0
        suspend fun page(id: String) = cache.get(id) {
            calls++
            delay(300)
            listOf("$id-track-1", "$id-track-2")
        }
        val first = page("first")
        assertEquals(300L, testScheduler.currentTime)
        val whole = first + page("continuation-1") + page("continuation-2")
        assertEquals(900L, testScheduler.currentTime)
        assertEquals(3, calls)

        val reopenAt = testScheduler.currentTime
        assertSame(first, page("first"))
        assertEquals(whole, page("first") + page("continuation-1") + page("continuation-2"))
        assertEquals(0L, testScheduler.currentTime - reopenAt)
        assertEquals(3, calls)
    }

    @Test
    fun `ownership menu and page readers share one concurrent browse request`() = runTest {
        val cache = cache<String>()
        var calls = 0
        suspend fun read() = cache.get("account-a:en:playlist") {
            calls++
            delay(300)
            "tracks, ownership, library controls and header"
        }
        val menu = async { read() }
        val page = async { read() }
        assertEquals(menu.await(), page.await())
        assertEquals(1, calls)
        assertEquals(300L, testScheduler.currentTime)
    }

    @Test
    fun `account profile language and continuation keys are isolated`() = runTest {
        val cache = cache<String>()
        var calls = 0
        val keys = listOf("account-a:profile-a:en:first", "account-a:profile-b:en:first",
            "account-b:profile-a:en:first", "account-a:profile-a:hi:first", "account-a:profile-a:en:next")
        repeat(2) {
            keys.forEach { key -> assertEquals(key, cache.get(key) { calls++; key }) }
        }
        assertEquals(keys.size, calls)
    }

    @Test
    fun `a successful empty playlist retains its metadata rather than refetching`() = runTest {
        val cache = cache<List<String>>()
        var calls = 0
        repeat(3) { assertTrue(cache.get("empty") { calls++; emptyList() }.isEmpty()) }
        assertEquals(1, calls)
    }

    @Test
    fun `failure is retried on the next visit`() = runTest {
        val cache = cache<String>()
        var calls = 0
        try {
            cache.get("playlist") { calls++; error("temporary network failure") }
            error("the first fetch should have failed")
        } catch (failure: IllegalStateException) {
            assertEquals("temporary network failure", failure.message)
        }
        assertEquals("tracks", cache.get("playlist") { calls++; "tracks" })
        assertEquals(2, calls)
    }

    @Test
    fun `expiry is measured from completion and an expired listing refetches`() = runTest {
        val cache = cache<String>(ttlMs = 1_000)
        var calls = 0
        suspend fun read() = cache.get("playlist") { calls++; delay(300); "tracks-$calls" }
        assertEquals("tracks-1", read())
        advanceTimeBy(999)
        assertEquals("tracks-1", read())
        advanceTimeBy(1)
        assertEquals("tracks-2", read())
        assertEquals(2, calls)
    }

    @Test
    fun `playlist edit or explicit refresh invalidates completed pages`() = runTest {
        val cache = cache<String>()
        var calls = 0
        suspend fun read() = cache.get("playlist") { "tracks-${++calls}" }
        assertEquals("tracks-1", read())
        cache.clear()
        assertEquals("tracks-2", read())
        assertEquals(2, calls)
    }

    @Test
    fun `a request started before an edit cannot repopulate the refreshed cache`() = runTest {
        val cache = cache<String>()
        val releaseOld = CompletableDeferred<Unit>()
        val old = async { cache.get("playlist") { releaseOld.await(); "old order" } }
        runCurrent()
        cache.clear()
        assertEquals("new order", cache.get("playlist") { "new order" })
        releaseOld.complete(Unit)
        assertEquals("old order", old.await())
        assertEquals("new order", cache.get("playlist") { error("must reuse the new order") })
    }

    @Test
    fun `an abandoned reader cannot cancel a request another page still needs`() = runTest {
        val cache = cache<String>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val first = async { cache.get("playlist") { calls++; release.await(); "tracks" } }
        runCurrent()
        val second = async { cache.get("playlist") { error("must join the first request") } }
        runCurrent()
        first.cancelAndJoin()
        release.complete(Unit)
        assertEquals("tracks", second.await())
        assertEquals(1, calls)
    }

    @Test
    fun `leaving the last reader cancels wasted network work and permits a retry`() = runTest {
        val cache = cache<String>()
        var cancelled = false
        val first = async {
            cache.get("playlist") {
                try {
                    CompletableDeferred<Unit>().await()
                    "unused"
                } finally {
                    cancelled = true
                }
            }
        }
        runCurrent()
        first.cancelAndJoin()
        runCurrent()
        assertTrue(cancelled)
        assertEquals("fresh tracks", cache.get("playlist") { "fresh tracks" })
    }

    @Test
    fun `least recently used pages are evicted to respect the entry limit`() = runTest {
        val cache = cache<String>(maxEntries = 2)
        val calls = mutableMapOf<String, Int>()
        suspend fun read(key: String) = cache.get(key) {
            calls[key] = calls.getOrDefault(key, 0) + 1
            key
        }
        read("a"); read("b"); read("a"); read("c"); read("a"); read("b")
        assertEquals(1, calls["a"])
        assertEquals(2, calls["b"])
        assertEquals(1, calls["c"])
    }

    @Test
    fun `long playlists respect the row budget as well as the page count`() = runTest {
        val cache = cache<List<String>>(maxWeight = 3, weightOf = { it.size })
        var firstCalls = 0
        cache.get("first") { firstCalls++; listOf("a", "b") }
        cache.get("second") { listOf("c", "d") }
        cache.get("first") { firstCalls++; listOf("a", "b") }
        assertEquals(2, firstCalls)
    }

    @Test
    fun `a single oversized response is usable without being retained`() = runTest {
        val cache = cache<List<String>>(maxWeight = 1, weightOf = { it.size })
        var calls = 0
        repeat(2) { assertEquals(2, cache.get("huge") { calls++; listOf("a", "b") }.size) }
        assertEquals(2, calls)
    }

    @Test
    fun `session cookies and channel selection advance the opaque cache scope`() {
        val oldCookie = Innertube.cookie
        try {
            val original = Innertube.responseCacheScope
            Innertube.cookie = "cache-test-session"
            assertTrue(Innertube.responseCacheScope > original)
            val account = Innertube.responseCacheScope
            Innertube.selectChannel("channel-a", "profile-a", "0")
            assertTrue(Innertube.responseCacheScope > account)
            val profile = Innertube.responseCacheScope
            Innertube.selectChannel("channel-a", "profile-a", "0")
            assertEquals(profile, Innertube.responseCacheScope)
            Innertube.selectChannel("channel-b", "profile-b", "0")
            assertTrue(Innertube.responseCacheScope > profile)
        } finally {
            Innertube.cookie = oldCookie
            Innertube.selectChannel(null, null)
        }
    }
}
