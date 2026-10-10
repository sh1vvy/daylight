/*
 * Spring-chain equations adapted from Accompanist Lyrics UI, Apache-2.0.
 * https://github.com/6xingyv/accompanist-lyrics-ui
 * Modified for Daylight on 10 October 2026: bounded following lanes driven by
 * one existing playback/list frame loop, with no renderer or dependency replacement.
 * See docs/licenses/Accompanist-Lyrics-UI-Apache-2.0.txt.
 */
package com.music.bitchord.ui.player

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.pow

/** The anchor follows the list directly; at most five trailing layers spring behind it. */
internal class LyricSpringTrail {
    private val positions = FloatArray(6)
    private val velocities = FloatArray(6)
    private var base = 0f
    private var limit = 96f
    fun begin(distance: Float) {
        for (i in 1..5) positions[i] -= base
        base = 0f
        limit = abs(distance).coerceIn(24f, 180f)
    }
    fun clear() { positions.fill(0f); velocities.fill(0f); base = 0f }
    fun step(target: Float, seconds: Float) {
        if (!target.isFinite() || !seconds.isFinite() || seconds <= 0f) return
        base = target
        val dt = seconds.coerceAtMost(0.05f)
        val steps = ceil(dt / (1f / 240f)).toInt().coerceAtLeast(1)
        val slice = dt / steps
        repeat(steps) {
            for (i in 5 downTo 1) {
                val response = (1f / (1f + 0.25f * i)).coerceAtLeast(0.35f)
                val neighbour = if (i > 1) positions[i - 1] - base else 0f
                val aim = base + neighbour * 0.65f
                val acceleration = -100f * response * (positions[i] - aim) -
                    12f * response.pow(0.3f) * velocities[i]
                velocities[i] += acceleration * slice
                positions[i] += velocities[i] * slice
                if (abs(positions[i] - base) > limit) {
                    positions[i] = base + (positions[i] - base).coerceIn(-limit, limit)
                    velocities[i] = 0f
                }
            }
        }
    }
    fun offset(distance: Int): Float = if (distance <= 0) 0f else base - positions[distance.coerceAtMost(5)]
    fun settled(): Boolean = (1..5).all { abs(base - positions[it]) < 0.08f && abs(velocities[it]) < 0.08f }
}
