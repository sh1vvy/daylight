package com.music.bitchord.ui.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

/**
 * A dark rose range keeps the player's white lyrics readable. Remapping only
 * the backdrop's luminance retains each sleeve's mesh while leaving the actual
 * artwork and motion covers unchanged. The filter runs in the existing image
 * draw, so switching themes needs no new bitmap decode or blur.
 */
internal object PinkCloudPlayerStyle {
    val base = Color(0xFF281524)
    val highlight = Color(0xFF985E80)
    val scrim = Color(0xFF190C17)
    val drawer = Color(0xFF341A2D)

    val imageFilter: ColorFilter by lazy {
        val range = listOf(
            base.red to highlight.red,
            base.green to highlight.green,
            base.blue to highlight.blue,
        )
        val matrix = FloatArray(20)
        range.forEachIndexed { row, (dark, light) ->
            val delta = light - dark
            matrix[row * 5] = delta * 0.2126f
            matrix[row * 5 + 1] = delta * 0.7152f
            matrix[row * 5 + 2] = delta * 0.0722f
            matrix[row * 5 + 4] = dark * 255f
        }
        matrix[18] = 1f // Preserve alpha, including the mesh's crossfade.
        ColorFilter.colorMatrix(ColorMatrix(matrix))
    }

    /** The same remap for the legacy gradient's four ordinary colour values. */
    fun colorFor(source: Color): Color {
        val brightness = source.red * 0.2126f + source.green * 0.7152f + source.blue * 0.0722f
        return Color(
            red = base.red + (highlight.red - base.red) * brightness,
            green = base.green + (highlight.green - base.green) * brightness,
            blue = base.blue + (highlight.blue - base.blue) * brightness,
            alpha = source.alpha,
        )
    }
}
