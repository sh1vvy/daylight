package com.music.bitchord.playback

/** Small compressed openings, bounded FLAC openings, and only one fuller Wi-Fi prefetch. */
internal object QueuePreloadPolicy {
    private const val MIB = 1024L * 1024
    fun openingBytes(lossless: Boolean, hiRes: Boolean): Long = when {
        !lossless -> 256 * 1024L
        hiRes -> 4 * MIB
        else -> 2 * MIB
    }

    fun canWarm(isPlaying: Boolean, currentPositionMs: Long, bufferedPositionMs: Long, durationMs: Long): Boolean =
        isPlaying && (bufferedPositionMs - currentPositionMs >= 8_000L ||
            (durationMs > 0 && bufferedPositionMs >= durationMs - 250L))

    // Even a very large cache never turns queue warming into an unlimited download.
    fun nextTrackBytes(cacheBudget: Long): Long = minOf(64 * MIB, (cacheBudget / 16).coerceAtLeast(2 * MIB))
}
