package com.music.bitchord.data.spotify

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistBatchCreationTest {
    @Test
    fun largeCreationKeepsOrderDuplicatesAndUsesFiftyTrackRequests() = runBlocking {
        val ids = (0 until 122).map { (it % 31).toString() }
        val requests = mutableListOf<List<String>>()
        val result = createPlaylistInBatches(ids, {
            requests += it.toList()
            Result.success("created")
        }, { id, batch ->
            assertEquals("created", id)
            requests += batch.toList()
            Result.success(Unit)
        }).getOrThrow()
        assertEquals(listOf(50, 50, 22), requests.map { it.size })
        assertEquals(ids, requests.flatten())
        assertEquals(122, result.addedCount)
        assertTrue(result.complete)
    }

    @Test
    fun firstFailedBatchStopsFurtherWritesAndReportsOnlyCompletedBatches() = runBlocking {
        var appended = 0
        val result = createPlaylistInBatches((0 until 180).map(Int::toString), {
            Result.success("partial")
        }, { _, _ ->
            appended++
            if (appended == 2) Result.failure(IllegalStateException("rate limit")) else Result.success(Unit)
        }).getOrThrow()
        assertEquals(2, appended)
        assertEquals(100, result.addedCount)
        assertEquals(180, result.totalCount)
        assertFalse(result.complete)
    }

    @Test
    fun failedCreationNeverSendsAppendRequests() = runBlocking {
        val result = createPlaylistInBatches(listOf("a"), {
            Result.failure(IllegalStateException("offline"))
        }, { _, _ -> error("No destination was created") })
        assertTrue(result.isFailure)
    }

    @Test(expected = CancellationException::class)
    fun cancellationCannotBeReportedAsAnOrdinaryPartialImport() = runBlocking {
        createPlaylistInBatches((0 until 51).map(Int::toString), {
            Result.success("cancelled")
        }, { _, _ -> Result.failure(CancellationException("Dismissed")) })
        Unit
    }
}
