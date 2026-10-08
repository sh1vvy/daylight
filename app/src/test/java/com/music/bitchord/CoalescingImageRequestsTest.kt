package com.music.bitchord

import com.music.bitchord.data.remote.KeyedImageRequestGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class CoalescingImageRequestsTest {
    @Test
    fun requestsForOneEncodedImageWaitForTheFirstDiskWriter() = runBlocking {
        val gate = KeyedImageRequestGate()
        var active = 0
        var peak = 0
        var completed = 0
        List(16) {
            async {
                gate.withKey("cover") {
                    active++
                    peak = maxOf(peak, active)
                    yield()
                    active--
                    completed++
                }
            }
        }.forEach { it.await() }
        assertEquals(1, peak)
        assertEquals(16, completed)
    }

    @Test
    fun differentCoversRemainConcurrent() = runBlocking {
        val gate = KeyedImageRequestGate()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = async { gate.withKey("one") { entered.complete(Unit); release.await() } }
        entered.await()
        assertEquals(42, gate.withKey("two") { 42 })
        release.complete(Unit)
        first.await()
    }

    @Test
    fun cancellingAWaitingRequestDoesNotBlockTheWriterOrLaterRequests() = runBlocking {
        val gate = KeyedImageRequestGate()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = async { gate.withKey("cover") { entered.complete(Unit); release.await(); 42 } }
        entered.await()
        val waiting = async { gate.withKey("cover") { -1 } }
        yield()
        waiting.cancelAndJoin()
        release.complete(Unit)
        assertEquals(42, first.await())
        assertEquals(7, gate.withKey("cover") { 7 })
    }
}
