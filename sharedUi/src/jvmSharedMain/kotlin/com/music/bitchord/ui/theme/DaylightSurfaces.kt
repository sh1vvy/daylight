/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/** One corner scale for Material cards, fields, menus and dialogs. */
val DaylightShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Shared tonal treatment for floating controls, independent of their geometry. */
@Immutable
data class DaylightFloatingColors(
    val surface: Color,
    val content: Color,
    val secondaryContent: Color,
    val selectedContent: Color,
    val selection: Color,
)

fun daylightFloatingColors(scheme: ColorScheme): DaylightFloatingColors {
    val dark = scheme.surface.luminance() < .5f
    val lifted = if (dark) lerp(scheme.surface, scheme.onSurface, .10f) else scheme.surface
    return DaylightFloatingColors(
        surface = lerp(lifted, scheme.primary, .045f).copy(alpha = .90f),
        content = scheme.onSurface,
        secondaryContent = scheme.onSurface.copy(alpha = .70f),
        selectedContent = scheme.primary,
        selection = scheme.primary.copy(alpha = if (dark) .16f else .11f),
    )
}
