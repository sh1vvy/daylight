/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-12.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** One small draw surface. The clock moves the track, never the centered marker or the player layout. */
@Composable
fun DaylightTimeline(position: () -> Float, enabled: Boolean, loading: Boolean, colors: PlayerColors,
    transitionWindow: ClosedFloatingPointRange<Float>?, glow: () -> Float,
    onScrub: (Float) -> Unit, onFinish: () -> Unit, onCancel: () -> Unit,
    modifier: Modifier = Modifier) {
    val currentPosition by rememberUpdatedState(position)
    val currentScrub by rememberUpdatedState(onScrub)
    val currentFinish by rememberUpdatedState(onFinish)
    val currentCancel by rememberUpdatedState(onCancel)
    var dragging by remember { mutableStateOf(false) }
    val accessible by remember { derivedStateOf { (currentPosition() * 100).toInt() / 100f } }
    Canvas(modifier.fillMaxWidth().height(44.dp).testTag("player-progress-bar")
        .semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(accessible, 0f..1f)
            if (enabled) setProgress { value -> currentScrub(value.coerceIn(0f, 1f)); currentFinish(); true }
        }.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures { point ->
                currentScrub((currentPosition() + point.x / size.width - .5f).coerceIn(0f, 1f))
                currentFinish()
            }
        }.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            var from = 0f
            var distance = 0f
            detectDragGestures(onDragStart = {
                from = currentPosition(); distance = 0f; dragging = true
            }, onDragEnd = { dragging = false; currentFinish() },
                onDragCancel = { dragging = false; currentCancel() }) { change, delta ->
                change.consume(); distance += delta.x
                currentScrub(timelineSeek(from, distance, size.width.toFloat()))
            }
        }) {
        val fraction = currentPosition().coerceIn(0f, 1f)
        val center = size.width / 2f
        val y = size.height / 2f
        val start = timelineX(0f, fraction, size.width)
        val end = timelineX(1f, fraction, size.width)
        val bright = colors.ink.copy(alpha = if (loading) .4f else .9f)
        val elapsed = lerp(colors.accent, colors.ink, .35f).copy(alpha = .38f)
        val thick = (if (dragging) 7.dp else 4.dp).toPx()
        val thin = 2.dp.toPx()
        clipRect {
            // Clamp visible endpoints before drawing the capsule so viewport clipping never squares them.
            val visibleStart = start.coerceAtLeast(0f)
            val visibleEnd = end.coerceAtMost(size.width)
            drawRoundRect(elapsed, Offset(visibleStart, y - thin / 2), Size(center - visibleStart, thin), CornerRadius(thin / 2))
            val strength = if (dragging) 0f else glow().coerceIn(0f, 1f)
            if (strength > 0f) for (spread in 1..3) {
                val pad = spread * 2.dp.toPx()
                drawRoundRect(bright.copy(alpha = strength * .07f / spread), Offset(center - pad, y - thick / 2 - pad),
                    Size(end - center + pad * 2, thick + pad * 2), CornerRadius(thick / 2 + pad))
            }
            drawRoundRect(bright, Offset(center, y - thick / 2), Size(visibleEnd - center, thick), CornerRadius(thick / 2))
            transitionWindow?.let { window ->
                val left = timelineX(window.start.coerceIn(0f, 1f), fraction, size.width).coerceAtLeast(center)
                val right = timelineX(window.endInclusive.coerceIn(0f, 1f), fraction, size.width).coerceAtMost(size.width)
                if (right > left) drawRoundRect(colors.accent.copy(alpha = .65f), Offset(left, y - thick / 2), Size(right - left, thick), CornerRadius(thick / 2))
            }
        }
        // The reference's warm marker stays at the same coordinate during playback and seeking.
        drawRoundRect(Color(0xFFFFB45B), Offset(center - 1.dp.toPx(), y - 6.dp.toPx()),
            Size(2.dp.toPx(), 12.dp.toPx()), CornerRadius(1.dp.toPx()))
    }
}
