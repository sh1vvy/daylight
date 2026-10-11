/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.ui.screens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeQuickPickLoopTest {
    @Test fun firstAndLastTracksHaveNeighboursAcrossTheWrap() {
        val first = quickPickLoopPage(12, 0)
        assertEquals(0, quickPickLoopIndex(first, 12))
        assertEquals(11, quickPickLoopIndex(first - 1, 12))
        assertEquals(0, quickPickLoopIndex(first + 12, 12))
        assertEquals(1, quickPickLoopIndex(first + 13, 12))
    }

    @Test fun recenteringPreservesEachTrackForDifferentPoolSizes() {
        for (count in 2..12) for (index in 0 until count) {
            val page = quickPickLoopPage(count, index)
            assertTrue(page in 24 until QUICK_PICK_LOOP_PAGES - 25)
            assertEquals(index, quickPickLoopIndex(page, count))
            assertEquals((index + 1) % count, quickPickLoopIndex(page + 1, count))
            assertEquals((index + count - 1) % count, quickPickLoopIndex(page - 1, count))
        }
    }

    @Test fun emptyAndSingleTrackPoolsCannotDivideByZeroOrSelectAnInvalidPage() {
        assertEquals(0, quickPickLoopIndex(0, 0))
        assertEquals(0, quickPickLoopPage(0, 0))
        assertEquals(0, quickPickLoopPage(1, 10))
        assertEquals(0, quickPickLoopIndex(50_000, 1))
    }

    @Test fun passingCoversClearEachOtherBeforeTheDepthOrderSwitches() {
        val separation = 2 * quickPickPosition(.5f)
        assertTrue(separation > quickPickScale(.5f), "Even without rotation, the covers cannot pop over each other at the midpoint")
    }

    @Test fun coversRemainInsideThePageGuttersThroughoutTheSwipe() {
        for (step in -200..200) {
            val offset = step / 100f
            val outerEdge = kotlin.math.abs(quickPickPosition(offset)) + quickPickScale(offset) / 2
            assertTrue(outerEdge * QUICK_PICK_COVER_FRACTION <= .5f,
                "The full, unrotated sleeve fits inside the inner viewport at $offset")
        }
    }

    @Test fun bufferingFadesInContinuouslyRatherThanJumpingWhenTheNearestPageChanges() {
        assertEquals(1f, quickPickOpacity(0f))
        assertEquals(0f, quickPickOpacity(1.5f))
        for (step in 1..150) {
            val before = (step - 1) / 100f
            val after = step / 100f
            assertTrue(kotlin.math.abs(quickPickOpacity(before) - quickPickOpacity(after)) < .02f)
            assertTrue(kotlin.math.abs(quickPickPosition(before) - quickPickPosition(after)) < .02f)
            assertEquals(-quickPickPosition(after), quickPickPosition(-after))
        }
    }
}
