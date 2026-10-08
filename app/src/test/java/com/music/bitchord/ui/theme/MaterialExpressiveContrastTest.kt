package com.music.bitchord.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialExpressiveContrastTest {
    @Test
    fun fallbackTextStaysReadableAcrossControlsAndContainers() {
        listOf(MaterialExpressiveLightColors, MaterialExpressiveDarkColors).forEach { colors ->
            val pairs = listOf(
                Triple("primary", colors.onPrimary, colors.primary),
                Triple("primary container", colors.onPrimaryContainer, colors.primaryContainer),
                Triple("secondary", colors.onSecondary, colors.secondary),
                Triple("secondary container", colors.onSecondaryContainer, colors.secondaryContainer),
                Triple("tertiary", colors.onTertiary, colors.tertiary),
                Triple("tertiary container", colors.onTertiaryContainer, colors.tertiaryContainer),
                Triple("background", colors.onBackground, colors.background),
                Triple("surface", colors.onSurface, colors.surface),
                Triple("surface variant", colors.onSurfaceVariant, colors.surfaceVariant),
                Triple("inverse surface", colors.inverseOnSurface, colors.inverseSurface),
                Triple("inverse primary", colors.inversePrimary, colors.inverseSurface),
                Triple("error", colors.onError, colors.error),
                Triple("error container", colors.onErrorContainer, colors.errorContainer),
            )
            pairs.forEach { (name, foreground, background) ->
                val contrast = contrastRatio(foreground, background)
                assertTrue("$name text contrast $contrast must be at least 4.5:1", contrast >= 4.5f)
            }
        }

    }

    @Test
    fun fallbackCaptionsStayReadableOnEverySurfaceElevation() {
        listOf(MaterialExpressiveLightColors, MaterialExpressiveDarkColors).forEach { colors ->
            val surfaces = listOf(
                colors.surfaceBright, colors.surfaceDim, colors.surfaceContainer,
                colors.surfaceContainerHigh, colors.surfaceContainerHighest,
                colors.surfaceContainerLow, colors.surfaceContainerLowest,
            )
            surfaces.forEach { surface ->
                assertTrue(contrastRatio(colors.onSurface, surface) >= 4.5f)
                assertTrue(contrastRatio(colors.onSurfaceVariant, surface) >= 4.5f)
            }
        }

    }

    @Test
    fun fallbackControlOutlinesStayVisibleOnBothBackgrounds() {
        assertTrue(contrastRatio(MaterialExpressiveLightColors.outline, MaterialExpressiveLightColors.background) >= 3f)
        assertTrue(contrastRatio(MaterialExpressiveDarkColors.outline, MaterialExpressiveDarkColors.background) >= 3f)
    }

    private fun contrastRatio(first: Color, second: Color): Float {
        val a = first.luminance()
        val b = second.luminance()
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }
}
