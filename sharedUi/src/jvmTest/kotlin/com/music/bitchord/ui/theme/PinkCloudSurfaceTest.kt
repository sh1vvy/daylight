package com.music.bitchord.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PinkCloudSurfaceTest {
    private val pink = lightColorScheme(
        background = Color(0xFFFFF2F6),
        onBackground = Color(0xFF39212D),
        primary = Color(0xFFA23765),
        onSurfaceVariant = Color(0xFF704755),
        surfaceContainerLow = Color(0xFFFFF5F8),
    )

    @Test
    fun homeAndNavigationKeepTheSelectedPinkSurface() {
        assertSame(pink, daylightHomeColorScheme(pink, pinkCloud = true))
        assertEquals(pink.background, daylightHomeBackground(pink.background, pinkCloud = true))
        assertEquals(Color(0xFFFFF8F4), daylightHomeBackground(Color.White))
        assertEquals(Color(0xFF19151D), daylightHomeBackground(Color.Black))
    }

    @Test
    fun materialHomePreservesWallpaperColorsAndSurfaceRoles() {
        val native = lightColorScheme(background = Color(0xFFF4FBF7), primary = Color(0xFF006A62))
        assertSame(native, daylightHomeColorScheme(native, materialExpressive = true))
        assertEquals(native.background, daylightHomeBackground(native.background, materialExpressive = true))
    }

    @Test
    fun missingArtworkStillUsesTheThemeAndItsReadableText() {
        val palette = pinkCloudArtworkPalette(pink, null)
        assertEquals(pink.background, palette.background)
        assertEquals(pink.primary, palette.accent)
        assertEquals(pink.onBackground, palette.onBackground)
    }

    @Test
    fun darkSleevesRetainTheirWashWithoutMakingThePageTextIllegible() {
        val sleeve = ArtworkPalette(
            background = Color.Black,
            wash = Color(0xFF001940),
            elevated = Color.Black,
            accent = Color.Cyan,
            onBackground = Color.White,
            onBackgroundVariant = Color.White,
            divider = Color.White,
        )
        val palette = pinkCloudArtworkPalette(pink, sleeve)
        assertTrue(palette.wash != pink.surfaceContainerLow)
        assertEquals(pink.primary, palette.accent)
        val bodyContrast = (palette.background.luminance() + 0.05f) / (palette.onBackground.luminance() + 0.05f)
        val mutedContrast = (palette.wash.luminance() + 0.05f) / (palette.onBackgroundVariant.luminance() + 0.05f)
        assertTrue(bodyContrast >= 4.5f)
        assertTrue(mutedContrast >= 4.5f)
    }
}
