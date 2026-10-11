/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-12.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.player

import kotlin.test.*
import androidx.compose.ui.geometry.Rect

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
    @Test fun morphKeepsBothMeasuredEndpointsAndNeverEscapesTheirBounds() {
        val full = Rect(0f, 0f, 1080f, 2340f)
        val mini = Rect(48f, 2040f, 1032f, 2208f)
        assertEquals(full, morphBounds(full, mini, 1f))
        assertEquals(mini, morphBounds(full, mini, 0f))
        assertEquals(mini, morphBounds(full, mini, -2f))
        assertEquals(full, morphBounds(full, mini, 2f))
        for (i in 0..100) {
            val frame = morphBounds(full, mini, i / 100f)
            assertTrue(frame.width > 0 && frame.height > 0)
            assertTrue(frame.left in 0f..48f && frame.top in 0f..2040f)
        }
    }
    @Test fun movingTimelineKeepsThePlayheadCenteredAndSeeksOppositeToTheDrag() {
        for (i in 0..100) {
            val head = i / 100f
            assertEquals(160f, timelineX(head, head, 320f), .001f)
            assertEquals(320f, timelineX(1f, head, 320f) - timelineX(0f, head, 320f), .001f)
        }
        assertEquals(.25f, timelineSeek(.5f, 80f, 320f))
        assertEquals(.75f, timelineSeek(.5f, -80f, 320f))
        assertEquals(0f, timelineSeek(.1f, 1_000f, 320f))
        assertEquals(1f, timelineSeek(.9f, -1_000f, 320f))
        assertEquals(.5f, timelineSeek(.5f, 80f, 0f))
    }
    @Test fun coverRoundingFollowsTheFoldAndFinishesBeforeMiniHandoff() {
        assertEquals(0f, coverFold(1f, 0f))
        assertEquals(0f, coverFold(.45f, 0f), .0001f)
        assertEquals(.5f, coverFold(.285f, 0f), .0001f)
        assertEquals(1f, coverFold(.12f, 0f))
        assertEquals(1f, coverFold(0f, 0f))
        assertEquals(0f, coverFold(1f, 1f))
        assertEquals(.5f, coverFold(.5f, 1f))
        assertEquals(1f, coverFold(0f, 1f))
        for (i in 1..100) assertTrue(coverFold(i / 100f, 0f) <= coverFold((i - 1) / 100f, 0f))
    }
}
