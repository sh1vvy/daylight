package com.music.bitchord.ui.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinkCloudPlayerStyleTest {
    @Test
    fun evenTheBrightestArtworkKeepsWhiteLyricsReadable() {
        // Sample extremes as well as coloured sleeves. The matrix uses positive
        // channel weights, so white is the brightest possible input.
        val sleeves = listOf(Color.Black, Color.White, Color.Red, Color.Green, Color.Blue, Color.Yellow)
        sleeves.forEach { sleeve ->
            val backdrop = PinkCloudPlayerStyle.colorFor(sleeve)
            val contrast = 1.05f / (backdrop.luminance() + 0.05f)
            assertTrue("White lyrics need 4.5:1 contrast on $sleeve: $contrast", contrast >= 4.5f)
        }
    }

    @Test
    fun lightAndDarkMeshRegionsRemainDistinctInsteadOfFlatteningArtwork() {
        assertEquals(PinkCloudPlayerStyle.base, PinkCloudPlayerStyle.colorFor(Color.Black))
        val bright = PinkCloudPlayerStyle.colorFor(Color.White)
        assertEquals(PinkCloudPlayerStyle.highlight.red, bright.red, 0.0001f)
        assertEquals(PinkCloudPlayerStyle.highlight.green, bright.green, 0.0001f)
        assertEquals(PinkCloudPlayerStyle.highlight.blue, bright.blue, 0.0001f)

        val shadows = PinkCloudPlayerStyle.colorFor(Color(0xFF303030))
        val lights = PinkCloudPlayerStyle.colorFor(Color(0xFFD0D0D0))
        assertTrue(lights.luminance() > shadows.luminance())
        assertTrue(lights.red > lights.green && shadows.red > shadows.green)
    }

    @Test
    fun crossfadeAlphaIsPreserved() {
        val source = Color.White.copy(alpha = 0.35f)
        assertEquals(source.alpha, PinkCloudPlayerStyle.colorFor(source).alpha, 0f)
    }
}
