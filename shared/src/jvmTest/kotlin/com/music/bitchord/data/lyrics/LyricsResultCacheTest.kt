package com.music.bitchord.data.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger

class LyricsResultCacheTest {
    private fun key(id: String, source: LyricsSource = LyricsSource.LRC_RED, explicit: Boolean? = null) =
        LyricsResultCache.Key(id, "Song", "Artist", 200L, "Album", explicit, source)
    private fun result(text: String = "words") = LyricsRepository.Result(LyricsSource.LRC_RED, listOf(LyricLine(0L, text)))

    @Test fun `only positive lyrics are cached so misses remain retryable`() {
        val cache = LyricsResultCache()
        cache.put(key("a"), null)
        cache.put(key("b"), result(""))
        cache.put(key("c"), result())
        assertEquals(1, cache.size())
        assertNull(cache.get(key("a")))
        assertNotNull(cache.get(key("c")))
    }

    @Test fun `provider and explicit recording stay separate`() {
        val cache = LyricsResultCache()
        cache.put(key("a", explicit = true), result())
        assertNull(cache.get(key("a", explicit = false)))
        assertNull(cache.get(key("a", source = LyricsSource.LRCLIB, explicit = true)))
    }

    @Test fun `least recently used results are evicted at the entry cap`() {
        val cache = LyricsResultCache(maxEntries = 2)
        cache.put(key("a"), result())
        cache.put(key("b"), result())
        cache.get(key("a"))
        cache.put(key("c"), result())
        assertNotNull(cache.get(key("a")))
        assertNull(cache.get(key("b")))
        assertNotNull(cache.get(key("c")))
    }

    @Test fun `memory cap accounts for word and syllable timing`() {
        val cache = LyricsResultCache(maxWeight = 400)
        val words = listOf(LyricWord(0L, 500L, "word", List(10) { LyricSyllable(0L, 500L, 0, 4) }))
        cache.put(key("heavy"), LyricsRepository.Result(LyricsSource.LRC_RED, listOf(LyricLine(0L, "word", words = words))))
        assertNull(cache.get(key("heavy")))
        repeat(10) { cache.put(key(it.toString()), result()) }
        assertTrue(cache.weight() <= 400)
    }

    @Test fun `queue changes drop old lyrics and reject late cancelled window writes`() {
        val cache = LyricsResultCache()
        cache.put(key("old"), result())
        cache.put(key("current"), result())
        cache.retain(setOf("previous", "current", "next"))
        assertNull(cache.get(key("old")))
        cache.put(key("old"), result())
        cache.put(key("next"), result())
        assertNull(cache.get(key("old")))
        assertNotNull(cache.get(key("current")))
        assertNotNull(cache.get(key("next")))
    }

    @Test fun `queue warmer service and UI share one successful provider call`() = runBlocking {
        val cache = LyricsResultCache()
        val calls = AtomicInteger()
        val requests = List(3) {
            async {
                cache.getOrFetch(key("current")) {
                    calls.incrementAndGet()
                    delay(20L)
                    result()
                }
            }
        }.awaitAll()
        assertEquals(1, calls.get())
        assertTrue(requests.all { it?.lines?.firstOrNull()?.text == "words" })
    }

    @Test fun `cancelled background lookup cannot poison the waiting foreground request`() = runBlocking {
        val cache = LyricsResultCache()
        val started = CompletableDeferred<Unit>()
        val background = async {
            cache.getOrFetch(key("next")) {
                started.complete(Unit)
                delay(30_000L)
                result("background")
            }
        }
        started.await()
        val foreground = async { cache.getOrFetch(key("next")) { result("foreground") } }
        background.cancelAndJoin()
        val found = withTimeout(1_000L) { foreground.await() }
        assertEquals("foreground", found?.lines?.firstOrNull()?.text)
        assertEquals("foreground", cache.get(key("next"))?.lines?.firstOrNull()?.text)
    }

    @Test fun `provider miss is retried by the next real request`() = runBlocking {
        val cache = LyricsResultCache()
        assertNull(cache.getOrFetch(key("next")) { null })
        assertNotNull(cache.getOrFetch(key("next")) { result() })
        Unit
    }
}
