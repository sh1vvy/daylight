/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-13 through 2026-10-13.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.motion

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Material asks its confirmation predicate while predicting anchors as well as
 * on release. A returning row must not perform actions during those predictions.
 * The unconsumed observer below allows one action only after pointer release,
 * and keeps it disarmed throughout spring-back, cancellation and recomposition.
 */
class SwipeActionGate {
    private var available = false
    private var active = false
    fun begin() { active = true; available = false }
    fun release(eligible: Boolean = true) { available = active && eligible; active = false }
    fun cancel() { active = false; available = false }
    fun commit(action: () -> Unit): Boolean {
        if (!available || active) return false
        available = false // Disarm before a callback can invalidate its row.
        action()
        return true
    }
}

fun Modifier.observeSwipeRelease(gate: SwipeActionGate): Modifier = pointerInput(gate) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        gate.begin()
        var position = down.position
        try {
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.firstOrNull { it.id == down.id }?.let { position = it.position }
            } while (event.changes.any { it.pressed })
            val delta = position - down.position
            // A tap during spring-back or a vertical list scroll cannot re-arm
            // an older horizontal offset that is still returning to rest.
            gate.release(kotlin.math.abs(delta.x) > viewConfiguration.touchSlop &&
                kotlin.math.abs(delta.x) > kotlin.math.abs(delta.y))
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            gate.cancel()
            throw cancelled
        }
    }
}
