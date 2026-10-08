package com.music.bitchord

import com.music.bitchord.data.DownloadedListingFallback
import com.music.bitchord.data.model.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadedListingFallbackTest {
    @Test
    fun `an empty online success waits for slow saved files and keeps their tracks`() = runTest {
        var published = emptyList<String>()
        val fallback = DownloadedListingFallback(this, read = {
            delay(300)
            listOf("saved track")
        }, publish = { published = it })
        assertEquals(listOf("saved track"), fallback.finish(UiState.Success(emptyList())))
        assertEquals(published, listOf("saved track"))
        assertEquals(300L, testScheduler.currentTime)
    }

    @Test
    fun `an offline failure waits for the downloaded copy rather than hiding it`() = runTest {
        val fallback = DownloadedListingFallback(this, read = {
            delay(300)
            listOf("saved track")
        }, publish = {})
        assertEquals(listOf("saved track"), fallback.finish(UiState.Error("offline")))
        assertEquals(300L, testScheduler.currentTime)
    }

    @Test
    fun `ready online songs cancel unnecessary file checks without waiting for them`() = runTest {
        var cancelled = false
        var published = false
        val fallback = DownloadedListingFallback(this, read = {
            try {
                delay(10_000)
                listOf("saved track")
            } finally {
                cancelled = true
            }
        }, publish = { published = true })
        runCurrent()
        assertEquals(emptyList<String>(), fallback.finish(UiState.Success(listOf("online track"))))
        runCurrent()
        assertTrue(cancelled)
        assertEquals(false, published)
        assertEquals(0L, testScheduler.currentTime)
    }
}
