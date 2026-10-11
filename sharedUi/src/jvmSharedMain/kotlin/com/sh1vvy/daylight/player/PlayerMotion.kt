/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-12.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.player

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.util.lerp

/** Geometry and touch callbacks supplied by the window host, independent of its sheet implementation. */
@Stable
class PlayerSurfaceMotion(
    val expansion: () -> Float,
    val sheetOffset: () -> Float,
    val miniSurface: () -> Rect?,
    val miniArtwork: () -> Rect?,
    val miniColor: Color,
    val drag: (Float) -> Unit,
    val release: (Float) -> Unit,
    val miniArtworkRadius: () -> Float,
)

/** End points stay exact even when the sheet or the mini player is transformed by its host. */
fun morphBounds(expanded: Rect, compact: Rect, expansion: Float): Rect {
    val t = expansion.coerceIn(0f, 1f)
    return Rect(lerp(compact.left, expanded.left, t), lerp(compact.top, expanded.top, t),
        lerp(compact.right, expanded.right, t), lerp(compact.bottom, expanded.bottom, t))
}

/** Use the same fold progress as cover geometry; rounding reaches the mini radius before handoff. */
fun coverFold(expansion: Float, panelExpansion: Float): Float =
    if (panelExpansion <= 0f) (1f - ((expansion - .12f) / .33f).coerceIn(0f, 1f))
    else (1f - expansion).coerceIn(0f, 1f)

/** The timeline travels past a fixed playhead; its length represents the complete track. */
fun timelineX(fraction: Float, position: Float, width: Float): Float = width * (.5f + fraction - position)
fun timelineSeek(position: Float, draggedPx: Float, width: Float): Float =
    if (width <= 0f) position.coerceIn(0f, 1f) else (position - draggedPx / width).coerceIn(0f, 1f)
