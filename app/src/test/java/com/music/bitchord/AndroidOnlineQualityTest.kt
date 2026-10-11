/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-08 through 2026-10-08.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

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
