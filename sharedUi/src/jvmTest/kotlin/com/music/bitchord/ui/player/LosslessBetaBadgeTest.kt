package com.music.bitchord.ui.player

import com.music.bitchord.data.NerdStats
import org.junit.Assert.*
import org.junit.Test

class LosslessBetaBadgeTest {
    @Test fun `normal and pending audio have no quality label`() {
        assertNull(confirmedLosslessLabel(null))
        assertNull(confirmedLosslessLabel(stats("audio/opus")))
        assertNull(confirmedLosslessLabel(stats("audio/mp4a-latm", bitrate = 320)))
    }

    @Test fun `only measured lossless audio earns a label`() {
        assertEquals(ConfirmedLosslessLabel.LOSSLESS, confirmedLosslessLabel(stats("audio/flac", depth = 16)))
        assertEquals(ConfirmedLosslessLabel.LOSSLESS, confirmedLosslessLabel(stats("audio/flac", depth = 24)))
        assertEquals(ConfirmedLosslessLabel.HI_RES, confirmedLosslessLabel(stats("audio/flac", depth = 32)))
        assertEquals(ConfirmedLosslessLabel.HI_RES, confirmedLosslessLabel(stats("audio/flac", rate = 96_000, depth = 16)))
    }

    @Test fun `unmeasured depth cannot claim hi res`() {
        assertEquals(ConfirmedLosslessLabel.LOSSLESS, confirmedLosslessLabel(stats("audio/flac")))
    }

    private fun stats(mime: String, depth: Int? = null, rate: Int = 44_100, bitrate: Int? = null) = NerdStats.Snapshot(
        mimeType = mime, bitrateKbps = bitrate, sampleRateHz = rate, channels = 2, bitDepth = depth,
    )
}
