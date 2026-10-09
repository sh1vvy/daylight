package com.music.bitchord.ui.player

import com.music.bitchord.data.NerdStats
import org.junit.Assert.*
import org.junit.Test

class LosslessBetaBadgeTest {
    @Test fun `verified provider label cannot make Opus lossless`() {
        val opus = stats("audio/opus")
        assertFalse(opus.isLossless)
        assertFalse(betaFallbackIsVisible(NerdStats.LosslessBetaStatus.VERIFIED, opus))
        assertTrue(betaFallbackIsVisible(NerdStats.LosslessBetaStatus.TIMED_OUT, opus))
    }

    @Test fun `unmeasured or actually lossless audio never gets a lossy fallback label`() {
        assertFalse(betaFallbackIsVisible(NerdStats.LosslessBetaStatus.NO_MATCH, null))
        assertFalse(betaFallbackIsVisible(NerdStats.LosslessBetaStatus.STREAM_FAILED, stats("audio/flac")))
        assertFalse(betaFallbackIsVisible(NerdStats.LosslessBetaStatus.NO_MATCH, stats("audio/opus", "Local")))
        assertTrue(stats("audio/flac").isLossless)
    }

    private fun stats(mime: String, source: String = "YouTube") = NerdStats.Snapshot(
        mimeType = mime, bitrateKbps = null, sampleRateHz = 44_100, channels = 2, sourceName = source,
    )
}
