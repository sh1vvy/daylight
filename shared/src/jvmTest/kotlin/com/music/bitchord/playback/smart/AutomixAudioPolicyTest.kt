package com.music.bitchord.playback.smart

import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.sources.StreamFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutomixAudioPolicyTest {
    private fun format(mime: String = "audio/flac", depth: Int? = 16, rate: Int? = 44_100) = NerdStats.Snapshot(
        mimeType = mime, bitrateKbps = null, sampleRateHz = rate, bitDepth = depth, channels = 2,
    )
    private val opus = format("audio/opus", null, 48_000)

    @Test fun lossyAndStandardLosslessAreAllowed() {
        for (actual in listOf(opus, format(), format(depth = 24, rate = 48_000))) {
            assertEquals(AutomixAudioPolicy.Verdict.ALLOWED, AutomixAudioPolicy.verdict(actual))
            assertTrue(AutomixAudioPolicy.mayEnable(false, actual))
        }
    }

    @Test fun highSampleRateOrUnsupportedDepthIsBlocked() {
        for (actual in listOf(format(rate = 96_000), format(depth = 24, rate = 192_000), format(depth = 32))) {
            assertEquals(AutomixAudioPolicy.Verdict.HI_RES, AutomixAudioPolicy.verdict(actual))
            assertFalse(AutomixAudioPolicy.mayEnable(false, actual))
            assertFalse(AutomixAudioPolicy.mayAnalyze(false, actual))
        }
    }

    @Test fun hiResSelectionBlocksEvenAnOpusFallback() {
        assertFalse(AutomixAudioPolicy.mayEnable(true, opus))
        assertFalse(AutomixAudioPolicy.mayAnalyze(true, opus))
        assertEquals(AutomixAudioPolicy.Verdict.HI_RES, AutomixAudioPolicy.transitionVerdict(true, opus, opus))
    }

    @Test fun bothDecksMustBeMeasuredBeforeEitherCanMix() {
        assertEquals(AutomixAudioPolicy.Verdict.WAITING_FOR_FORMAT, AutomixAudioPolicy.transitionVerdict(false, opus, null))
        assertEquals(AutomixAudioPolicy.Verdict.WAITING_FOR_FORMAT, AutomixAudioPolicy.transitionVerdict(false, null, opus))
        assertEquals(AutomixAudioPolicy.Verdict.ALLOWED, AutomixAudioPolicy.transitionVerdict(false, opus, format(depth = 24)))
    }

    @Test fun aHiResIncomingDeckCannotHideBehindNormalCurrentAudio() {
        assertEquals(AutomixAudioPolicy.Verdict.HI_RES, AutomixAudioPolicy.transitionVerdict(false, opus, format(rate = 96_000)))
        assertFalse(AutomixAudioPolicy.mayAnalyze(false, opus, format(rate = 96_000)))
    }

    @Test fun aHiResOutgoingDeckCannotHideBehindNormalNextAudio() {
        assertEquals(AutomixAudioPolicy.Verdict.HI_RES, AutomixAudioPolicy.transitionVerdict(false, format(rate = 96_000), opus))
    }

    @Test fun unknownFlacResolutionWaitsWithoutGuessing() {
        assertEquals(AutomixAudioPolicy.Verdict.WAITING_FOR_FORMAT, AutomixAudioPolicy.verdict(format(depth = null)))
        assertEquals(AutomixAudioPolicy.Verdict.WAITING_FOR_FORMAT, AutomixAudioPolicy.verdict(format(rate = null)))
        assertEquals(AutomixAudioPolicy.Verdict.WAITING_FOR_FORMAT, AutomixAudioPolicy.verdict(format(depth = 0, rate = 0)))
        assertTrue(AutomixAudioPolicy.mayEnable(false, null))
        assertTrue(AutomixAudioPolicy.mayAnalyze(false, opus, null))
    }

    @Test fun aProviderClaimCannotOverrideTheActualDecoder() {
        val opusWithClaim = NerdStats.Snapshot(
            mimeType = "audio/opus", bitrateKbps = 160, sampleRateHz = 48_000, channels = 2,
            claimed = StreamFormat(codec = "flac", bitDepth = 24, sampleRateHz = 192_000),
        )
        assertEquals(AutomixAudioPolicy.Verdict.ALLOWED, AutomixAudioPolicy.verdict(opusWithClaim))
    }

    @Test fun aLateFormatDuringArmingRejectsThePairAfterRetirement() {
        val gate = AutomixTransitionGate()
        assertEquals(AutomixAudioPolicy.Verdict.WAITING_FOR_FORMAT, gate.verdict("pair", false, opus, null))
        assertEquals(AutomixAudioPolicy.Verdict.HI_RES, gate.verdict("pair", false, opus, format(rate = 96_000)))
        assertEquals(AutomixAudioPolicy.Verdict.HI_RES, gate.verdict("pair", false, opus, null))
        assertEquals(AutomixAudioPolicy.Verdict.WAITING_FOR_FORMAT, gate.verdict("new rendition", false, opus, null))
    }

    @Test fun aFormatChangeDuringFadeRevokesEligibility() {
        val gate = AutomixTransitionGate()
        assertEquals(AutomixAudioPolicy.Verdict.ALLOWED, gate.verdict("pair", false, opus, format(depth = 24)))
        assertEquals(AutomixAudioPolicy.Verdict.HI_RES, gate.verdict("pair", false, opus, format(depth = 24, rate = 96_000)))
    }

    @Test fun selectingHiResDuringFadeBlocksWithoutPermanentlyRejectingTheRecording() {
        val gate = AutomixTransitionGate()
        assertEquals(AutomixAudioPolicy.Verdict.ALLOWED, gate.verdict("pair", false, opus, opus))
        assertEquals(AutomixAudioPolicy.Verdict.HI_RES, gate.verdict("pair", true, opus, opus))
        assertEquals(AutomixAudioPolicy.Verdict.ALLOWED, gate.verdict("pair", false, opus, opus))
    }

    @Test fun aRealDecoderFallbackCanMakeTheSamePairSafeAgain() {
        val gate = AutomixTransitionGate()
        gate.verdict("pair", false, opus, format(rate = 96_000))
        assertEquals(AutomixAudioPolicy.Verdict.ALLOWED, gate.verdict("pair", false, opus, opus))
    }
}
