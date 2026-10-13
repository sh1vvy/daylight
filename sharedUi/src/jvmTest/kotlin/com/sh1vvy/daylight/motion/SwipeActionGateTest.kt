/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-13 through 2026-10-13.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.motion

import kotlin.test.*

class SwipeActionGateTest {
    @Test fun predictionsAndRepeatedConfirmationsCannotRepeatTheAction() {
        val gate = SwipeActionGate(); var queued = 0
        assertFalse(gate.commit { queued++ })
        gate.begin()
        repeat(20) { assertFalse(gate.commit { queued++ }) }
        gate.release()
        assertTrue(gate.commit { queued++ })
        repeat(20) { assertFalse(gate.commit { queued++ }) }
        assertEquals(1, queued)
        gate.begin(); gate.release()
        assertTrue(gate.commit { queued++ })
        assertEquals(2, queued)
    }
    @Test fun tapOrVerticalReleaseDoesNotRearmASettlingSwipe() {
        val gate = SwipeActionGate()
        gate.begin(); gate.release(eligible = false)
        assertFalse(gate.commit { fail("Tap or vertical scroll action") })
        gate.begin(); gate.release()
        assertTrue(gate.commit { })
    }
    @Test fun cancelledGestureAndReentrantCallbacksCannotCommit() {
        val gate = SwipeActionGate()
        gate.begin(); gate.cancel(); gate.release()
        assertFalse(gate.commit { fail("Cancelled action") })
        gate.begin(); gate.release()
        assertTrue(gate.commit { assertFalse(gate.commit { fail("Repeated action") }) })
    }
}
