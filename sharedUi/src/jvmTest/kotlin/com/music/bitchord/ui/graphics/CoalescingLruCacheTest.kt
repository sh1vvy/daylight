package com.music.bitchord.ui.graphics

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoalescingLruCacheTest {
    @Test
    fun concurrentSurfacesShareOneLoadAndThenUseTheCachedValue() = runBlocking {
        val cache = CoalescingLruCache<Int>(2)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var loads = 0
        val first = async { cache.getOrLoad("cover") { loads++; entered.complete(Unit); release.await(); 42 } }
        entered.await()
        val others = List(12) { async { cache.getOrLoad("cover") { loads++; -1 } } }
        yield()
        release.complete(Unit)
        assertEquals(42, first.await())
        others.forEach { assertEquals(42, it.await()) }
        assertEquals(42, cache.getOrLoad("cover") { loads++; -1 })
        assertEquals(1, loads)
    }

    @Test
    fun leastRecentlyReadEntryIsEvicted() = runBlocking {
        val cache = CoalescingLruCache<Int>(2)
        cache.getOrLoad("a") { 1 }
        cache.getOrLoad("b") { 2 }
        assertEquals(1, cache["a"])
        cache.getOrLoad("c") { 3 }
        assertNull(cache["b"])
        assertEquals(1, cache["a"])
        assertEquals(3, cache["c"])
    }

    @Test
    fun nullAndFailedLoadsCanBeRetried() = runBlocking {
        val cache = CoalescingLruCache<Int>(2)
        assertNull(cache.getOrLoad("cover") { null })
        try { cache.getOrLoad("cover") { error("temporarily unavailable") } } catch (_: IllegalStateException) { }
        assertEquals(3, cache.getOrLoad("cover") { 3 })
    }

    @Test
    fun cancelledFollowerDoesNotCancelTheSharedLoad() = runBlocking {
        val cache = CoalescingLruCache<Int>(2)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = async { cache.getOrLoad("cover") { entered.complete(Unit); release.await(); 42 } }
        entered.await()
        val follower = async { cache.getOrLoad("cover") { -1 } }
        yield()
        follower.cancel()
        release.complete(Unit)
        assertEquals(42, first.await())
        assertEquals(42, cache["cover"])
    }

    @Test
    fun closingTheFirstSurfaceKeepsAnotherSurfacesDecodeAlive() = runBlocking {
        val cache = CoalescingLruCache<Int>(2)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = async { cache.getOrLoad("cover") { entered.complete(Unit); release.await(); 42 } }
        entered.await()
        val follower = async { cache.getOrLoad("cover") { -1 } }
        yield()
        first.cancel()
        release.complete(Unit)
        assertEquals(42, follower.await())
        assertEquals(42, cache["cover"])
    }

    @Test
    fun lastSurfaceLeavingCancelsUnneededWorkAndAllowsANewRequest() = runBlocking {
        val cache = CoalescingLruCache<Int>(2)
        val entered = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        val surface = async {
            cache.getOrLoad("cover") {
                try { entered.complete(Unit); awaitCancellation() } finally { stopped.complete(Unit) }
            }
        }
        entered.await()
        surface.cancelAndJoin()
        stopped.await()
        assertEquals(7, cache.getOrLoad("cover") { 7 })
    }

    @Test
    fun cancelledNonCooperativeLoadCannotOverwriteItsReplacement() = runBlocking {
        val cache = CoalescingLruCache<Int>(2)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val old = async {
            cache.getOrLoad("cover") {
                withContext(NonCancellable) {
                    entered.complete(Unit)
                    release.await()
                    finished.complete(Unit)
                    1
                }
            }
        }
        entered.await()
        old.cancelAndJoin()
        assertEquals(2, cache.getOrLoad("cover") { 2 })
        release.complete(Unit)
        finished.await()
        yield()
        assertEquals(2, cache["cover"])
    }

    @Test
    fun zeroBudgetOnlySharesPendingWorkWithoutRetainingEncodedBytes() = runBlocking {
        val cache = CoalescingLruCache<Int>(0)
        cache.getOrLoad("cover") { 1 }
        assertNull(cache["cover"])
        assertEquals(2, cache.getOrLoad("cover") { 2 })
    }
}
