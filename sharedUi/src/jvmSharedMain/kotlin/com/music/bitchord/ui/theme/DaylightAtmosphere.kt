/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Viewport-fixed colour fields. No bitmap, blur, timer or scroll-state dependency. */
@Immutable
data class DaylightAtmosphere(
    val top: Color,
    val middle: Color,
    val bottom: Color,
    val glow: Color,
    val glowAlpha: Float,
    val glowX: Float,
    val glowY: Float,
    val radius: Float,
) {
    /** Also used to check ink contrast across the composed gradient, including its glow. */
    fun colorAt(x: Float, y: Float, aspectRatio: Float = .5f): Color {
        val base = if (y <= .5f) lerp(top, middle, (y * 2).coerceIn(0f, 1f))
            else lerp(middle, bottom, ((y - .5f) * 2).coerceIn(0f, 1f))
        val dx = (x - glowX) * aspectRatio
        val distance = kotlin.math.sqrt(dx * dx + (y - glowY) * (y - glowY))
        return glow.copy(alpha = glowAlpha * (1f - distance / radius).coerceIn(0f, 1f)).compositeOver(base)
    }
}

private val DarkAtmosphere = DaylightAtmosphere(
    top = Color(0xFF222023), middle = Color(0xFF3F2852), bottom = Color(0xFF493047),
    glow = Color(0xFFFBCF4E), glowAlpha = .18f, glowX = .06f, glowY = 1.10f, radius = .90f,
)
private val LightAtmosphere = DaylightAtmosphere(
    top = Color(0xFFF8F4ED), middle = Color(0xFFF4F3EA), bottom = Color(0xFFD9F1F4),
    glow = Color(0xFFE3FF00), glowAlpha = .14f, glowX = -.12f, glowY = .18f, radius = .72f,
)
private val PinkAtmosphere = DaylightAtmosphere(
    top = Color(0xFFFFBEDD), middle = Color(0xFFFFE9F2), bottom = Color(0xFFF2F4C7),
    glow = Color(0xFFFF7BBF), glowAlpha = .24f, glowX = 1.05f, glowY = .12f, radius = .90f,
)

fun daylightAtmosphere(dark: Boolean, pinkCloud: Boolean = false): DaylightAtmosphere = when {
    pinkCloud -> PinkAtmosphere
    dark -> DarkAtmosphere
    else -> LightAtmosphere
}

@Composable
fun currentDaylightAtmosphere(): DaylightAtmosphere = daylightAtmosphere(
    dark = MaterialTheme.colorScheme.background.luminance() < .5f,
    pinkCloud = LocalPinkCloud.current,
)

/** Brushes survive every content/scroll frame; they rebuild only for a size/palette change. */
@Composable
fun Modifier.daylightBackground(): Modifier {
    val atmosphere = currentDaylightAtmosphere()
    return drawWithCache {
        val floor = Brush.verticalGradient(
            0f to atmosphere.top, .5f to atmosphere.middle, 1f to atmosphere.bottom,
        )
        // The radius uses viewport height, while the center follows both axes.
        // No scroll offset enters this cache: the field stays behind the page.
        val glow = Brush.radialGradient(
            colors = listOf(atmosphere.glow.copy(alpha = atmosphere.glowAlpha), atmosphere.glow.copy(alpha = 0f)),
            center = Offset(atmosphere.glowX * size.width, atmosphere.glowY * size.height),
            radius = (size.height * atmosphere.radius).coerceAtLeast(1f),
        )
        onDrawBehind { drawRect(floor); drawRect(glow) }
    }
}
