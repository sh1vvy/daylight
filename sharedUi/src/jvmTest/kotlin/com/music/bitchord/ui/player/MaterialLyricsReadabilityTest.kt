package com.music.bitchord.ui.player

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MaterialLyricsReadabilityTest {
    @Test
    fun stackedRowWordAndBackingFadesCannotDimNativeTextRoles() {
        val artworkLayers = listOf(
            listOf(0.58f, 0.58f),
            listOf(0.54f, 0.58f, 0.72f, 0.85f),
        )
        listOf(lightColorScheme(), darkColorScheme()).forEach { colors ->
            artworkLayers.forEach { layers ->
                val opacity = layers.fold(1f) { alpha, layer -> alpha * lyricLayerAlpha(true, layer) }
                val nativeInk = colors.onSurfaceVariant.copy(alpha = opacity).compositeOver(colors.surface)
                assertTrue(contrast(nativeInk, colors.surface) >= 4.5f)
            }
            assertTrue(contrast(colors.primary, colors.surface) >= 4.5f)
            assertTrue(contrast(colors.onSurfaceVariant, colors.surface) >= 4.5f)
        }
    }

    @Test
    fun artworkThemesKeepTheirOriginalLayerHierarchy() {
        val row = 0.54f
        val word = 0.58f
        assertEquals(row * word, lyricLayerAlpha(false, row) * lyricLayerAlpha(false, word))
        assertEquals(0.72f * 0.85f, lyricLayerAlpha(false, 0.72f * 0.85f))
    }

    private fun contrast(a: Color, b: Color): Float =
        (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)
}
