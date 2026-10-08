package com.music.bitchord

import com.music.bitchord.data.settings.AndroidOnlineQuality
import com.music.bitchord.data.settings.AudioQuality
import com.music.bitchord.data.settings.DownloadQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AndroidOnlineQualityTest {
    @Test
    fun `legacy uncapped streaming preferences share the best available selection`() {
        listOf(AudioQuality.MEDIUM, AudioQuality.HIGH, AudioQuality.LOSSLESS).forEach { saved ->
            val visible = AndroidOnlineQuality.streamingSelection(saved)
            assertEquals(AudioQuality.HIGH, visible)
            assertEquals(Int.MAX_VALUE, visible.maxKbps)
        }
        assertEquals(AudioQuality.LOW, AndroidOnlineQuality.streamingSelection(AudioQuality.LOW))
        assertEquals(64, AndroidOnlineQuality.streamingSelection(AudioQuality.LOW).maxKbps)
        assertFalse(AndroidOnlineQuality.streamingOptions.contains(AudioQuality.LOSSLESS))
        assertFalse(AndroidOnlineQuality.streamingOptions.contains(AudioQuality.MEDIUM))
    }

    @Test
    fun `legacy lossless download preference keeps the native highest ceiling`() {
        val visible = AndroidOnlineQuality.downloadSelection(DownloadQuality.LOSSLESS)
        assertEquals(DownloadQuality.HIGH, visible)
        assertEquals(Int.MAX_VALUE, visible.maxKbps)
        assertEquals(DownloadQuality.STANDARD, AndroidOnlineQuality.downloadSelection(DownloadQuality.STANDARD))
        assertEquals(128, AndroidOnlineQuality.downloadSelection(DownloadQuality.STANDARD).maxKbps)
        assertFalse(AndroidOnlineQuality.downloadOptions.contains(DownloadQuality.LOSSLESS))
    }
}
