package com.music.bitchord.playback

import com.music.bitchord.data.lyrics.LyricLine
import kotlin.math.ceil

/** Notification text changes at line boundaries; it needs no frame or half-second clock. */
internal class NotificationLyricsTimeline(lines: List<LyricLine>) {
    private data class Entry(val timeMs: Long, val subtitle: String?)

    // Prepared on the lyric loader's IO dispatcher. Keep only the fields the
    // notification needs, and sort once so seeks can use a bounded binary lookup.
    private val entries = lines.map { line ->
        Entry(line.timeMs, line.text.takeIf { it.isNotBlank() }?.let { "♪ $it" })
    }.sortedBy { it.timeMs }

    fun subtitleAt(positionMs: Long, artist: String): String =
        entries.getOrNull(upperBound(positionMs) - 1)?.subtitle ?: artist

    /** Null means the final line is already displayed and no clock is needed. */
    fun nextDelayMs(positionMs: Long, playbackSpeed: Float): Long? {
        val next = entries.getOrNull(upperBound(positionMs)) ?: return null
        val speed = playbackSpeed.takeIf { it.isFinite() && it > 0f } ?: 1f
        val untilNext = (next.timeMs.toDouble() - positionMs.toDouble()) / speed
        // A five-second backstop handles small position drift. Seeks, silence
        // skips, speed changes and resume all reschedule immediately in the service.
        return ceil(untilNext).toLong().coerceIn(16L, 5_000L)
    }

    private fun upperBound(positionMs: Long): Int {
        var low = 0
        var high = entries.size
        while (low < high) {
            val middle = low + (high - low) / 2
            if (entries[middle].timeMs <= positionMs) low = middle + 1 else high = middle
        }
        return low
    }
}
