package com.music.bitchord.ui.player

import org.junit.Test
import kotlin.test.*
import kotlin.math.abs

class LyricSpringTrailTest {
    @Test fun anchorIsDirectAndFollowingRowsSettleWithinABoundedTime() {
        val trail = LyricSpringTrail()
        trail.begin(100f)
        trail.step(100f, 1f / 60)
        assertEquals(0f, trail.offset(0))
        assertTrue(trail.offset(1) > 0f)
        assertTrue(trail.offset(5) >= trail.offset(1))
        repeat(300) { trail.step(100f, 1f / 60) }
        assertTrue(trail.settled())
        assertTrue(abs(trail.offset(5)) < 0.08f)
    }
    @Test fun reverseAndInterruptedFollowRetainFiniteBoundedPositions() {
        val trail = LyricSpringTrail()
        trail.begin(120f)
        repeat(10) { trail.step(it * 12f, 1f / 60) }
        val prior = trail.offset(3)
        trail.begin(-80f)
        assertEquals(prior, trail.offset(3), 0.01f)
        repeat(300) {
            trail.step(-80f, 1f / 60)
            assertTrue(trail.offset(5).isFinite())
            assertTrue(abs(trail.offset(5)) <= 180f)
        }
        assertTrue(trail.settled())
    }
    @Test fun userScrollOrReducedMotionClearsAllLayersImmediately() {
        val trail = LyricSpringTrail()
        trail.begin(100f); trail.step(100f, 0.05f); trail.clear()
        repeat(10) { assertEquals(0f, trail.offset(it)) }
        assertTrue(trail.settled())
    }
}
