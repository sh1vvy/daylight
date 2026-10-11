/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guard actual readability as palette washes and Material surfaces change. */
class DaylightPaletteContrastTest {
    @Test fun textRemainsReadableAcrossAllAtmospheresAndWindowShapes() {
        for ((name, flags) in listOf("Dark" to (true to false), "Light" to (false to false), "Pink" to (false to true))) {
            val scheme = daylightColorScheme(flags.first, flags.second)
            val field = daylightAtmosphere(flags.first, flags.second)
            for (aspect in listOf(.45f, .75f, 1.8f)) for (x in 0..10) for (y in 0..20) {
                val background = field.colorAt(x / 10f, y / 20f, aspect)
                readable("$name body at $x,$y/$aspect", scheme.onBackground, background)
                readable("$name secondary at $x,$y/$aspect", scheme.onSurfaceVariant, background)
                readable("$name active track at $x,$y/$aspect", scheme.primary, background)
                val selectedRow = daylightTrackSelection(scheme).compositeOver(background)
                readable("$name selected track at $x,$y/$aspect", scheme.primary, selectedRow)
                readable("$name selected track secondary", scheme.onSurfaceVariant, selectedRow)
                val floating = daylightFloatingColors(scheme)
                val surface = floating.surface.compositeOver(background)
                readable("$name floating content", floating.content, surface)
                readable("$name floating secondary", floating.secondaryContent, surface)
                readable("$name selected tab", floating.selectedContent, floating.selection.compositeOver(surface))
            }
        }
    }

    @Test fun buttonsAndMaterialContainersKeepReadableInk() {
        for (scheme in listOf(DaylightDarkColors, DaylightLightColors, PinkCloudColors)) {
            for ((ink, surface) in listOf(
                scheme.onPrimary to scheme.primary, scheme.onPrimaryContainer to scheme.primaryContainer,
                scheme.onSecondary to scheme.secondary, scheme.onSecondaryContainer to scheme.secondaryContainer,
                scheme.onTertiary to scheme.tertiary, scheme.onTertiaryContainer to scheme.tertiaryContainer,
                scheme.onError to scheme.error, scheme.onErrorContainer to scheme.errorContainer,
                scheme.onSurfaceVariant to scheme.surfaceVariant, scheme.inverseOnSurface to scheme.inverseSurface,
            )) readable("Material ink", ink, surface)
            for (surface in listOf(scheme.surface, scheme.surfaceContainerLowest, scheme.surfaceContainerLow,
                scheme.surfaceContainer, scheme.surfaceContainerHigh, scheme.surfaceContainerHighest)) {
                readable("Container body", scheme.onSurface, surface)
                readable("Container secondary", scheme.onSurfaceVariant, surface)
            }
        }
    }

    private fun readable(label: String, ink: Color, surface: Color) {
        val foreground = ink.compositeOver(surface).luminance()
        val background = surface.luminance()
        val ratio = (maxOf(foreground, background) + .05f) / (minOf(foreground, background) + .05f)
        assertTrue("$label has $ratio:1 contrast, needs 4.5:1", ratio >= 4.5f)
    }
}
