/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.player

import kotlin.test.*

class PlayerModelTest {
    @Test fun extrapolationStopsForPauseBufferingAndStaleAudioReadings() {
        val sample = PlayheadSample(20_000, 1_000_000_000, true, 0)
        assertEquals(20_500, projectedPosition(sample, 1_500_000_000, true, 60_000))
        assertEquals(20_000, projectedPosition(sample, 1_500_000_000, false, 60_000))
        assertEquals(20_000, projectedPosition(sample.copy(advancing = false), 1_500_000_000, true, 60_000))
        assertEquals(21_000, projectedPosition(sample, 100_000_000_000, true, 60_000))
        assertEquals(20_000, projectedPosition(sample, 100, true, 60_000))
    }
    @Test fun seekAndTrackDurationRemainAuthoritative() {
        val sample = PlayheadSample(90_000, 1_000_000_000, true, 3)
        assertEquals(30_000, projectedPosition(sample, 1_500_000_000, true, 30_000))
        assertEquals(2_500, projectedPosition(sample.copy(positionMs = 2_000, seek = 4), 1_500_000_000, true, 30_000))
        assertEquals(Long.MAX_VALUE, projectedPosition(sample.copy(positionMs = Long.MAX_VALUE), 1_500_000_000, true, 0))
    }
    @Test fun lyricIndexHandlesUnsortedRepeatedStartsAndSeeksBackwards() {
        val timeline = LyricTimeline(listOf(PlayerLyric("second", 2_000, 3_000), PlayerLyric("first", 1_000, 1_500), PlayerLyric("backing", 2_000, 3_000)))
        assertEquals(-1, timeline.activeAt(999))
        assertEquals(0, timeline.activeAt(1_500))
        assertEquals(2, timeline.activeAt(2_000))
        assertEquals(0, timeline.activeAt(1_000))
    }
    @Test fun plainLyricsDoNotInventTimestampsAndEmptyLyricsAreSafe() {
        val timeline = LyricTimeline(listOf(PlayerLyric("plain", -1, null)))
        assertFalse(timeline.synced)
        assertFalse(LyricTimeline(listOf(PlayerLyric("untimed first", 0, null), PlayerLyric("untimed next", 0, null))).synced)
        assertEquals(-1, timeline.activeAt(50_000))
        assertEquals(-1, LyricTimeline(emptyList()).activeAt(10))
    }
    @Test fun SyllableTimingHoldsThroughGapsAndNeverRevealsNextWordEarly() {
        val line = PlayerLyric("day light", 1_000, 3_000, listOf(
            TimedToken("day", 1_000, 1_500, 0, 3), TimedToken("light", 2_000, 3_000, 4, 9)))
        val timeline = LyricTimeline(listOf(line))
        assertEquals(0f, timeline.revealedChars(0, 999))
        assertEquals(1.5f, timeline.revealedChars(0, 1_250))
        assertEquals(4f, timeline.revealedChars(0, 1_750))
        assertEquals(6.5f, timeline.revealedChars(0, 2_500))
        assertEquals(9f, timeline.revealedChars(0, 3_000))
    }
}
