/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-12.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.player

import androidx.compose.runtime.Immutable

/** Persisted independently of artwork and lyric preferences. Existing installs retain CURRENT. */
enum class PlayerExperience { CURRENT, DAYLIGHT }

@Immutable
data class PlayerTrack(val id: String, val title: String, val artist: String, val artwork: String?, val key: String = id)

@Immutable
data class PlayerSession(
    val playing: Boolean,
    val busy: Boolean,
    val durationMs: Long,
    val previous: Boolean,
    val next: Boolean,
    val controlsLocked: Boolean,
    val liked: Boolean,
    val canLike: Boolean,
    val shuffle: Boolean,
    val repeat: Int,
    val autoplay: Boolean,
    val lyricsMissing: Boolean,
)

/** A dated audio reading. The presentation never invents an audio or queue owner. */
data class PlayheadSample(val positionMs: Long, val atNanos: Long, val advancing: Boolean, val seek: Int)

fun projectedPosition(sample: PlayheadSample, nowNanos: Long, playing: Boolean, durationMs: Long): Long {
    val elapsed = if (playing && sample.advancing && sample.atNanos > 0) {
        ((nowNanos - sample.atNanos).coerceAtLeast(0) / 1_000_000).coerceAtMost(1_000)
    } else 0
    val position = sample.positionMs.coerceAtLeast(0).let { if (Long.MAX_VALUE - it < elapsed) Long.MAX_VALUE else it + elapsed }
    return if (durationMs > 0) position.coerceAtMost(durationMs) else position
}

data class PlayerActions(
    val playPause: () -> Unit,
    val previous: () -> Unit,
    val next: () -> Unit,
    val seekFraction: (Float) -> Unit,
    val seekMs: (Long) -> Unit,
    val like: () -> Unit,
    val menu: () -> Unit,
    val dismiss: () -> Unit,
    val artwork: () -> Unit,
    val output: () -> Unit,
    val shuffle: () -> Unit,
    val repeat: () -> Unit,
    val autoplay: () -> Unit,
    val jump: (Int) -> Unit,
    val remove: (Int) -> Unit,
    val clear: () -> Unit,
    val blocked: () -> Unit,
)

@Immutable
data class PlayerLabels(
    val play: String, val pause: String, val previous: String, val next: String,
    val lyrics: String, val queue: String, val output: String, val close: String,
    val like: String, val menu: String, val shuffle: String, val repeat: String,
    val autoplay: String, val remove: String, val clear: String,
    val shuffleOn: String, val shuffleOff: String,
    val repeatOne: String, val repeatAll: String, val repeatOff: String,
    val autoplayOn: String, val autoplayOff: String,
    val returnToPlayer: String,
)
