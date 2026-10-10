package com.music.bitchord.widget

import kotlin.math.abs

/** Launcher cells vary in dp. Protect 48dp play/pause and legible text in every 2×2 allocation. */
internal object RecordWidgetSizing {
    const val MIN_WIDTH_DP = 110f
    const val MIN_HEIGHT_DP = 110f
    fun showSideControls(width: Float) = width >= 160f // 3×48dp targets plus 8dp insets.
    fun showArtist(height: Float, fontScale: Float) = height >= 160f && (fontScale <= 1.3f || height >= 240f)
    fun showTime(height: Float, fontScale: Float) = height >= 200f && (fontScale <= 1.3f || height >= 280f)
    fun showWave(height: Float, fontScale: Float) = height >= 260f && fontScale <= 1.3f
}

internal data class RecordWidgetProgress(val positionMs: Long, val durationMs: Long, val clockRunning: Boolean) {
    val knownDuration: Boolean get() = durationMs > 0L
    val progress: Int get() = if (knownDuration) ((positionMs.toDouble() / durationMs) * 1_000).toInt().coerceIn(0, 1_000) else 0
}

/**
 * A launcher Chronometer advances the display without waking Daylight. It is only
 * started for actual, unbuffered, normal-speed playback with a known duration.
 * Comparing wall and monotonic deltas rejects an anchor from a previous boot;
 * old snapshots never manufacture a running clock or position.
 */
internal fun MediaWidgetSnapshot.recordProgress(nowElapsedMs: Long, nowEpochMs: Long): RecordWidgetProgress {
    val duration = durationMs.coerceAtLeast(0L)
    val elapsed = nowElapsedMs - capturedAtElapsedMs
    val wallElapsed = nowEpochMs - capturedAtEpochMs
    val validAnchor = capturedAtElapsedMs > 0 && capturedAtEpochMs > 0 && elapsed >= 0 &&
        abs(wallElapsed - elapsed) < 5_000L
    val running = hasTrack && isPlaying && !isLoading && clockRunning && validAnchor && duration > 0
    val position = (positionMs.coerceAtLeast(0L) + if (running) elapsed else 0L)
        .let { if (duration > 0) it.coerceAtMost(duration) else it }
    return RecordWidgetProgress(position, duration, running && position < duration)
}

/** Multiple Media3 callbacks can describe one frame; don't republish a bitmap for a new timestamp alone. */
internal fun MediaWidgetSnapshot.sameWidgetPresentationAs(previous: MediaWidgetSnapshot): Boolean {
    val stateWithoutNewAnchor = copy(
        positionMs = previous.positionMs,
        capturedAtElapsedMs = previous.capturedAtElapsedMs,
        capturedAtEpochMs = previous.capturedAtEpochMs,
    )
    if (stateWithoutNewAnchor != previous) return false
    val previousPosition = previous.recordProgress(capturedAtElapsedMs, capturedAtEpochMs).positionMs
    return abs(positionMs - previousPosition) < 500L
}

internal fun recordWidgetTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val hours = seconds / 3_600
    val minutes = seconds / 60 % 60
    val remainder = seconds % 60
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:${remainder.toString().padStart(2, '0')}"
    else "$minutes:${remainder.toString().padStart(2, '0')}"
}
