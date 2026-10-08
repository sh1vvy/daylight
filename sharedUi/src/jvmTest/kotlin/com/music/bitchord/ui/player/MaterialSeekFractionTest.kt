package com.music.bitchord.ui.player

import kotlin.test.Test
import kotlin.test.assertEquals

class MaterialSeekFractionTest {
    @Test
    fun keepsNormalSeekPositionsAndClampsOutOfTrackValues() {
        assertEquals(0.42f, materialSeekFraction(0.42f))
        assertEquals(0f, materialSeekFraction(-0.1f))
        assertEquals(1f, materialSeekFraction(1.1f))
    }

    @Test
    fun invalidStreamPositionCannotPoisonTheSliderState() {
        assertEquals(0f, materialSeekFraction(Float.NaN))
        assertEquals(0f, materialSeekFraction(Float.POSITIVE_INFINITY))
        assertEquals(0f, materialSeekFraction(Float.NEGATIVE_INFINITY))
    }
}
