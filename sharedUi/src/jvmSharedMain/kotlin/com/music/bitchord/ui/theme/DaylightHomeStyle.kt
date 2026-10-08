package com.music.bitchord.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/** Also used by the floating navigation fades so Home has one continuous surface. */
fun daylightHomeBackground(themeBackground: Color, pinkCloud: Boolean = false): Color =
    if (pinkCloud) themeBackground
    else if (themeBackground.luminance() < 0.5f) Color(0xFF19151D) else Color(0xFFFFF8F4)

internal fun daylightHomeColorScheme(base: ColorScheme, pinkCloud: Boolean = false): ColorScheme {
    if (pinkCloud) return base
    val dark = base.background.luminance() < 0.5f
    return base.copy(
        primary = if (dark) Color(0xFFFFB5A3) else Color(0xFF983A45),
        onPrimary = if (dark) Color(0xFF432129) else Color.White,
        background = daylightHomeBackground(base.background),
        onBackground = if (dark) Color(0xFFF8ECEF) else Color(0xFF33262E),
        surface = if (dark) Color(0xFF302731) else Color(0xFFFFFDFC),
        onSurface = if (dark) Color(0xFFF8ECEF) else Color(0xFF33262E),
        surfaceVariant = if (dark) Color(0xFF3B303C) else Color(0xFFF0E3E6),
        onSurfaceVariant = if (dark) Color(0xFFD0BBC9) else Color(0xFF705765),
        outline = if (dark) Color(0xFF67515F) else Color(0xFFBBA1AC),
    )
}

/** Static, cached color washes: no bitmap, animation or live blur is needed. */
@Composable
internal fun HomeAtmosphere(modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    val dark = background.luminance() < 0.5f
    val pinkCloud = LocalPinkCloud.current
    val peach = if (pinkCloud) Color(0xFFF6BED5) else if (dark) Color(0xFF784432) else Color(0xFFFFD3AE)
    val lilac = if (pinkCloud) Color(0xFFE7CBEF) else if (dark) Color(0xFF52406C) else Color(0xFFDED0FA)
    Box(
        modifier.drawWithCache {
            val warmWash = Brush.radialGradient(
                colors = listOf(peach.copy(alpha = if (dark) 0.5f else 0.8f), Color.Transparent),
                center = Offset(size.width * 0.08f, 180.dp.toPx()),
                radius = 420.dp.toPx(),
            )
            val lilacWash = Brush.radialGradient(
                colors = listOf(lilac.copy(alpha = if (dark) 0.6f else 0.75f), Color.Transparent),
                center = Offset(size.width * 1.05f, 310.dp.toPx()),
                radius = 370.dp.toPx(),
            )
            val lowerWash = Brush.radialGradient(
                colors = listOf(peach.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(size.width * 0.75f, size.height * 0.9f),
                radius = 400.dp.toPx(),
            )
            onDrawBehind {
                drawRect(background)
                drawRect(warmWash)
                drawRect(lilacWash)
                drawRect(lowerWash)
            }
        },
    )
}
