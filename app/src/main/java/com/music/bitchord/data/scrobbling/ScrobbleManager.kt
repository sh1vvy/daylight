package com.music.bitchord.data.scrobbling

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** Counts actual listening time, excluding pauses, buffering and seeks. */
class ScrobbleManager(
    scope: CoroutineScope,
    var minSongDuration: Int = 30,
    var scrobbleDelayPercent: Float = 0.5f,
    var scrobbleDelaySeconds: Int = 240,
    private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val unixSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
    private val submit: suspend (Song, Int, Long) -> Unit = { song, duration, started ->
        LastFM.scrobble(song.artist, song.title, album = song.albumName,
            duration = duration, timestamp = started).getOrThrow()
    },
    private val nowPlaying: suspend (Song, Int?) -> Unit = { song, duration ->
        LastFM.updateNowPlaying(song.artist, song.title, album = song.albumName, duration = duration).getOrThrow()
    },
) {
    private val lifetime = SupervisorJob(scope.coroutineContext[Job])
    private val work = CoroutineScope(scope.coroutineContext + lifetime)
    private var timer: Job? = null
    private var nowPlayingJob: Job? = null
    private var runningSince: Long? = null
    private var remaining = 0L
    private var duration = 0
    private var startedAt = 0L
    private var current: Song? = null
    private var submitted = false
    var useNowPlaying = true
        set(value) {
            field = value
            if (!value) {
                nowPlayingJob?.cancel()
                nowPlayingJob = null
            }
        }
    var usePrimaryArtistOnly = false

    fun destroy() { onSongStop(); lifetime.cancel() }

    fun onSongStart(song: Song?, durationMs: Long? = null) {
        onSongStop()
        if (song == null) return
        current = song
        startedAt = unixSeconds()
        resolveDuration(song, durationMs)
        resume()
        val initialDuration = duration.takeIf { it > 0 }
        if (useNowPlaying) nowPlayingJob = work.launch {
            runCatching { nowPlaying(forScrobble(song), initialDuration) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        }
    }

    fun onSongResume(song: Song) {
        if (current?.videoId == song.videoId) resume()
    }

    fun onSongPause() {
        timer?.cancel()
        timer = null
        runningSince?.let { remaining = (remaining - (monotonicMillis() - it)).coerceAtLeast(0) }
        runningSince = null
    }

    fun onSongStop() {
        timer?.cancel(); timer = null; runningSince = null
        // A late status response must not replace the next track's Now Playing.
        // Qualified scrobble submissions have their own jobs and can finish.
        nowPlayingJob?.cancel(); nowPlayingJob = null
        current = null; remaining = 0; duration = 0; submitted = false
    }

    fun onPlayerStateChanged(isPlaying: Boolean, song: Song?, durationMs: Long? = null) {
        if (song == null) { onSongStop(); return }
        if (!isPlaying) { onSongPause(); return }
        if (current?.videoId != song.videoId) onSongStart(song, durationMs)
        else {
            if (duration == 0) resolveDuration(song, durationMs)
            resume()
        }
    }

    private fun resolveDuration(song: Song, durationMs: Long?) {
        duration = durationMs?.takeIf { it > 0 }?.div(1000)?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()
            ?: parseDuration(song.durationText)
        if (duration <= max(30, minSongDuration)) return
        val required = min(duration * 500L, 240_000L)
        val preferred = min((duration * 1000L * scrobbleDelayPercent.coerceIn(0f, 1f)).roundToLong(),
            scrobbleDelaySeconds.coerceAtLeast(0) * 1000L)
        remaining = max(required, preferred)
    }

    private fun resume() {
        if (submitted || timer?.isActive == true || current == null || duration <= max(30, minSongDuration)) return
        val song = current!!
        val recordingDuration = duration
        val timestamp = startedAt
        runningSince = monotonicMillis()
        timer = work.launch {
            delay(remaining)
            remaining = 0; runningSince = null; submitted = true
            work.launch {
                runCatching { submit(forScrobble(song), recordingDuration, timestamp) }
                    .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            }
        }
    }

    private fun forScrobble(song: Song): Song =
        if (usePrimaryArtistOnly) song.copy(artist = song.artist.primaryArtist()) else song

    private fun parseDuration(text: String?): Int {
        val parts = text?.split(':')?.map { it.toLongOrNull() ?: return 0 } ?: return 0
        if (parts.size !in 2..3 || parts.any { it < 0 } || parts.drop(1).any { it >= 60 }) return 0
        return parts.fold(0L) { total, value -> total * 60 + value }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
