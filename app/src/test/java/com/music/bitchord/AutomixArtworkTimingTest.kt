package com.music.bitchord

import com.music.bitchord.data.settings.MixBlend
import com.music.bitchord.data.settings.artworkFraction
import org.junit.Assert.*
import org.junit.Test

class AutomixArtworkTimingTest {
    private val blend = MixBlend(500f, 0L, true, handoffAt = 0.6f, artworkSpan = 0.2f)
    @Test fun dissolveFollowsActualHandoffAndKeepsEndpointsOpaque() {
        assertEquals(0f, blend.copy(progress = 0.4f).artworkFraction(), 0.0001f)
        assertEquals(0.5f, blend.copy(progress = 0.6f).artworkFraction(), 0.0001f)
        assertEquals(1f, blend.copy(progress = 0.8f).artworkFraction(), 0.0001f)
        val frames = (0..100).map { blend.copy(progress = it / 100f).artworkFraction() }
        assertTrue(frames.zipWithNext().all { (a,b) -> b >= a })
        assertEquals(blend.copy(progress = 0.6f, playing = false).artworkFraction(), blend.copy(progress = 0.6f).artworkFraction(), 0f)
    }
    @Test fun earlyAndLateHandoffsStillCompleteWithinTheBlend() {
        for (handoff in listOf(0f, 0.05f, 0.95f, 1f)) {
            assertEquals(0f, blend.copy(progress = -1f, handoffAt = handoff).artworkFraction(), 0f)
            assertEquals(1f, blend.copy(progress = 2f, handoffAt = handoff).artworkFraction(), 0f)
        }
    }
}
