/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.ui.screens

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeFlingPhysicsTest {
    @Test fun ordinaryReleaseSpeedsRemainCloseToTheFingerSpeed() {
        for (speed in listOf(0f, 100f, 500f, 1_000f)) {
            assertEquals(speed, homeFlingVelocity(speed, 250f), 4f)
        }
    }

    @Test fun fasterFlicksKeepTheirOrderWithoutBecomingUnbounded() {
        var previous = 0f
        for (speed in 0..30_000 step 100) {
            val result = homeFlingVelocity(speed.toFloat(), 250f)
            assertTrue(result >= previous, "A faster flick must never travel slower")
            assertTrue(result < 3_001f, "Extreme flicks cannot blast through the pool")
            assertEquals(-result, homeFlingVelocity(-speed.toFloat(), 250f))
            previous = result
        }
        assertTrue(homeFlingVelocity(8_000f, 250f) > homeFlingVelocity(2_000f, 250f))
        assertEquals(3_000f, homeFlingVelocity(Float.MAX_VALUE, 250f), 1f)
    }

    @Test fun equivalentGesturesBehaveTheSameAcrossDensityAndItemSizes() {
        for (scale in listOf(.7f, 2f, 3f, 4f)) {
            assertTrue(abs(homeFlingVelocity(2_000f * scale, 250f * scale) -
                homeFlingVelocity(2_000f, 250f) * scale) < .01f)
        }
        assertEquals(0f, homeFlingVelocity(1_000f, 0f))
        assertEquals(0f, homeFlingVelocity(Float.NaN, 250f))
    }
}
