/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
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

@Immutable
data class TimedToken(val text: String, val startMs: Long, val endMs: Long, val startChar: Int, val endChar: Int)

@Immutable
data class PlayerLyric(val text: String, val startMs: Long, val endMs: Long?, val tokens: List<TimedToken> = emptyList(), val seekMs: Long = startMs)

/** One prepared index per transcription; frame reads perform a logarithmic lookup. */
class LyricTimeline(lines: List<PlayerLyric>) {
    val synced = lines.any { it.startMs >= 0 && (it.startMs > 0 || it.tokens.isNotEmpty() || it.endMs != null) }
    val lines = if (synced) lines.filter { it.startMs >= 0 }.sortedBy { it.startMs } else lines
    private val starts = this.lines.map { it.startMs }.toLongArray()

    fun activeAt(positionMs: Long): Int {
        if (!synced || starts.isEmpty() || positionMs < starts[0]) return -1
        var low = 0
        var high = starts.lastIndex
        while (low <= high) {
            val mid = (low + high).ushr(1)
            if (starts[mid] <= positionMs) low = mid + 1 else high = mid - 1
        }
        return high
    }

    /** Unicode character ranges are prepared by the adapter, with repeated words kept distinct. */
    fun revealedChars(index: Int, positionMs: Long): Float {
        val line = lines.getOrNull(index) ?: return 0f
        if (positionMs < line.startMs) return 0f
        if (line.tokens.isEmpty()) return line.text.length.toFloat()
        for (token in line.tokens) {
            if (positionMs < token.startMs) return token.startChar.toFloat()
            if (positionMs < token.endMs) {
                val progress = (positionMs - token.startMs).toFloat() / (token.endMs - token.startMs).coerceAtLeast(1)
                return token.startChar + (token.endChar - token.startChar) * progress
            }
        }
        return line.text.length.toFloat()
    }
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
    val lyricsLoading: String, val lyricsUnavailable: String, val resumeLyrics: String,
)
