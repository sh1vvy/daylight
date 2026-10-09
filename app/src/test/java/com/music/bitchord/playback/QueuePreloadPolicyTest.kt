package com.music.bitchord.playback

import androidx.media3.common.C
import org.junit.Assert.*
import org.junit.Test

class QueuePreloadPolicyTest {
    private val mib = 1024L * 1024

    @Test fun onlyFiveUpcomingOpeningsAreWarmed() {
        assertEquals(5, AudioCache.QUEUE_DEPTH)
        val longQueue = (1..100).toList()
        assertEquals(listOf(1, 2, 3, 4, 5), longQueue.take(AudioCache.QUEUE_DEPTH))
    }

    @Test fun compressedFallbackUsesTheSmallBudgetEvenUnderHiResSelection() {
        assertEquals(256 * 1024L, QueuePreloadPolicy.openingBytes(lossless = false, hiRes = false))
        assertEquals(256 * 1024L, QueuePreloadPolicy.openingBytes(lossless = false, hiRes = true))
    }

    @Test fun standardAndHiResFlacUseSeparateBoundedPrefixBudgets() {
        assertEquals(2 * mib, QueuePreloadPolicy.openingBytes(lossless = true, hiRes = false))
        assertEquals(4 * mib, QueuePreloadPolicy.openingBytes(lossless = true, hiRes = true))
        assertEquals(10 * mib, AudioCache.QUEUE_DEPTH * QueuePreloadPolicy.openingBytes(true, false))
        assertEquals(20 * mib, AudioCache.QUEUE_DEPTH * QueuePreloadPolicy.openingBytes(true, true))
    }

    @Test fun theNextTrackIsCappedAtOneSixteenthOfNormalBudgets() {
        assertEquals(16 * mib, QueuePreloadPolicy.nextTrackBytes(256 * mib))
        assertEquals(32 * mib, QueuePreloadPolicy.nextTrackBytes(512 * mib))
        assertEquals(64 * mib, QueuePreloadPolicy.nextTrackBytes(1_024 * mib))
    }

    @Test fun evenUnlimitedOrLargeCachePreferencesOnlyWarmOneBoundedNextTrack() {
        assertEquals(64 * mib, QueuePreloadPolicy.nextTrackBytes(16_384 * mib))
        assertEquals(64 * mib, QueuePreloadPolicy.nextTrackBytes(Long.MAX_VALUE))
    }

    @Test fun smallBudgetsRetainAMinimumOpeningWithoutUnlimitedDownloads() {
        assertEquals(2 * mib, QueuePreloadPolicy.nextTrackBytes(16 * mib))
        assertTrue(QueuePreloadPolicy.nextTrackBytes(256 * mib) <= 256 * mib)
        assertTrue(QueuePreloadPolicy.nextTrackBytes(1_024 * mib) <= 1_024 * mib)
    }

    @Test fun queueWarmingWaitsUntilEightSecondsAreBuffered() {
        assertFalse(QueuePreloadPolicy.canWarm(true, 12_000, 19_999, 180_000))
        assertTrue(QueuePreloadPolicy.canWarm(true, 12_000, 20_000, 180_000))
        assertTrue(QueuePreloadPolicy.canWarm(true, 12_000, 20_001, 180_000))
    }

    @Test fun pausedOrBufferingPlaybackNeverWarmsTheQueue() {
        assertFalse(QueuePreloadPolicy.canWarm(false, 0, 60_000, 180_000))
        assertFalse(QueuePreloadPolicy.canWarm(false, 0, 2_000, 2_000))
    }

    @Test fun fullyBufferedShortTracksCanPrepareTheirNextSong() {
        assertTrue(QueuePreloadPolicy.canWarm(true, 0, 2_000, 2_000))
        assertTrue(QueuePreloadPolicy.canWarm(true, 0, 1_750, 2_000))
        assertFalse(QueuePreloadPolicy.canWarm(true, 0, 1_749, 2_000))
    }

    @Test fun theFinalQuarterSecondToleranceIsInclusive() {
        assertTrue(QueuePreloadPolicy.canWarm(true, 298_000, 299_750, 300_000))
        assertFalse(QueuePreloadPolicy.canWarm(true, 298_000, 299_749, 300_000))
        assertTrue(QueuePreloadPolicy.canWarm(true, 298_000, 300_000, 300_000))
    }

    @Test fun unknownDurationCannotPretendAShortBufferIsTheWholeTrack() {
        for (duration in listOf(0L, C.TIME_UNSET)) {
            assertFalse(QueuePreloadPolicy.canWarm(true, 0, 2_000, duration))
            assertTrue(QueuePreloadPolicy.canWarm(true, 0, 8_000, duration))
        }
    }

    @Test fun staleBufferPositionsAfterASeekCannotAuthorizeBackgroundFetches() {
        assertFalse(QueuePreloadPolicy.canWarm(true, 100_000, 30_000, 180_000))
        assertFalse(QueuePreloadPolicy.canWarm(true, 100_000, 100_000, 180_000))
    }
}
