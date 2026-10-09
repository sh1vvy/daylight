package com.music.bitchord

import androidx.media3.common.C
import androidx.media3.common.Format
import com.music.bitchord.data.lossless.flacHeader
import com.music.bitchord.playback.automixSnapshot
import com.music.bitchord.playback.smart.AutomixAudioPolicy
import org.junit.Assert.*
import org.junit.Test

class AutomixDecoderFormatTest {
    private fun flac(depth: Int, rate: Int, encoding: Int = C.ENCODING_INVALID, shape: Int = 0): Format {
        val header = flacHeader(rate, depth)
        val data = when (shape) { 1 -> header.copyOfRange(4, header.size); 2 -> header.copyOfRange(8, header.size); else -> header }
        return Format.Builder().setSampleMimeType("audio/flac").setPcmEncoding(encoding)
            .setInitializationData(listOf(data)).build()
    }

    @Test fun sourcePrecisionComesFromDecoderStreamInfoInAllContainerShapes() {
        for (shape in 0..2) {
            val measured = flac(24, 48_000, shape = shape).automixSnapshot()
            assertEquals(24, measured.bitDepth)
            assertEquals(48_000, measured.sampleRateHz)
            assertEquals(AutomixAudioPolicy.Verdict.ALLOWED, AutomixAudioPolicy.verdict(measured))
        }
    }

    @Test fun floatTransportDoesNotTurnStandardFlacIntoHiRes() {
        val actual = flac(16, 44_100, C.ENCODING_PCM_FLOAT).automixSnapshot()
        assertEquals(16, actual.bitDepth)
        assertFalse(actual.isHiRes)
    }

    @Test fun highSampleRateIsDetectedBeforeStandbyBecomesAudible() {
        val actual = flac(24, 96_000, C.ENCODING_PCM_FLOAT).automixSnapshot()
        assertTrue(actual.isHiRes)
    }

    @Test fun decoderEncodingAndRateTakePriorityOverContainerGaps() {
        val actual = flac(24, 96_000).buildUpon().setPcmEncoding(C.ENCODING_PCM_16BIT).setSampleRate(48_000).build().automixSnapshot()
        assertEquals(16, actual.bitDepth)
        assertEquals(48_000, actual.sampleRateHz)
        assertFalse(actual.isHiRes)
    }

    @Test fun absentOrTruncatedFlacInfoWaitsForProof() {
        for (data in listOf(ByteArray(0), flacHeader().copyOf(30))) {
            val actual = Format.Builder().setSampleMimeType("audio/flac").setInitializationData(listOf(data)).build().automixSnapshot()
            assertEquals(AutomixAudioPolicy.Verdict.WAITING_FOR_FORMAT, AutomixAudioPolicy.verdict(actual))
        }
    }

    @Test fun dsdCannotBecomeEligibleByPassingThroughRawPcm() {
        val actual = Format.Builder().setSampleMimeType("audio/raw").setCodecs("dsd64").setSampleRate(176_400).build().automixSnapshot()
        assertEquals(2_822_400, actual.sampleRateHz)
        assertTrue(actual.isHiRes)
    }
}
