package com.music.bitchord.widget

import android.content.Context

/**
 * Everything a home-screen widget needs to know about playback.
 *
 * Persisted rather than read live, because a widget outlives the app. It is on
 * screen while the process is dead, after a reboot, and in the seconds before a
 * launcher's first update reaches us — none of which a
 * [MediaController][androidx.media3.session.MediaController] can serve, since
 * connecting one means starting [PlaybackService][com.music.bitchord.playback.PlaybackService]
 * just to find out what to draw. So the service writes here whenever the answer
 * changes ([publishWidgetState][com.music.bitchord.playback.PlaybackService]) and
 * the widget only ever reads a file.
 */
internal data class MediaWidgetSnapshot(
    val mediaId: String?,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    /**
     * Whether the transport should show a pause glyph.
     *
     * This tracks `player.playWhenReady`, **not** `player.isPlaying`. The
     * difference matters more here than almost anywhere else in the app: a
     * YouTube track has to be resolved through NewPipe before it can buffer, and
     * that can run for seconds, all of which `isPlaying` spends false. Keyed on
     * it, a widget would answer a tap by leaving the play glyph exactly where it
     * was — the one thing that makes a control feel broken. `playWhenReady`
     * flips the instant the command lands, which is also what the media
     * notification shows. An ended queue is the exception: it offers play again.
     */
    val isPlaying: Boolean,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
    /** Whether the track is liked. Drawn by the 4×1 widget's heart. */
    val isLiked: Boolean = false,
    /** Whether shuffle is on. Drawn by the 4×1 widget's Shuffle. */
    val shuffleEnabled: Boolean = false,
    /** Loading is a state label; widgets never poll the player. */
    val isLoading: Boolean = false,
    /** Host-only Jam transport; Like remains a personal action. */
    val controlsLocked: Boolean = false,
    /** Positions are published on player events, never on per-second UI ticks. */
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val capturedAtElapsedMs: Long = 0L,
    val capturedAtEpochMs: Long = 0L,
    /** Actual playback at 1×. A loading transport can show pause while this clock stays still. */
    val clockRunning: Boolean = false,
) {
    /** Whether there is a track to draw at all. */
    val hasTrack: Boolean get() = mediaId != null

    companion object {

        val EMPTY = MediaWidgetSnapshot(
            mediaId = null,
            title = "",
            artist = "",
            artworkUrl = null,
            isPlaying = false,
            hasPrevious = false,
            hasNext = false,
        )

        // Set by a publisher, not a reader: upgrade cleanup must not overwrite
        // playback that a new service has already resumed in this process.
        private var publishedInThisProcess = false

        @Synchronized
        /** Even an unchanged snapshot can come from a newly restored live player. */
        fun notePlaybackPublished() { publishedInThisProcess = true }

        fun save(context: Context, snapshot: MediaWidgetSnapshot) {
            notePlaybackPublished()
            prefs(context).edit()
                .putString(KEY_MEDIA_ID, snapshot.mediaId)
                .putString(KEY_TITLE, snapshot.title)
                .putString(KEY_ARTIST, snapshot.artist)
                .putString(KEY_ARTWORK, snapshot.artworkUrl)
                .putBoolean(KEY_PLAYING, snapshot.isPlaying)
                .putBoolean(KEY_HAS_PREVIOUS, snapshot.hasPrevious)
                .putBoolean(KEY_HAS_NEXT, snapshot.hasNext)
                .putBoolean(KEY_LIKED, snapshot.isLiked)
                .putBoolean(KEY_SHUFFLE, snapshot.shuffleEnabled)
                .putBoolean(KEY_LOADING, snapshot.isLoading)
                .putBoolean(KEY_CONTROLS_LOCKED, snapshot.controlsLocked)
                .putLong(KEY_POSITION_MS, snapshot.positionMs)
                .putLong(KEY_DURATION_MS, snapshot.durationMs)
                .putLong(KEY_CAPTURED_ELAPSED_MS, snapshot.capturedAtElapsedMs)
                .putLong(KEY_CAPTURED_EPOCH_MS, snapshot.capturedAtEpochMs)
                .putBoolean(KEY_CLOCK_RUNNING, snapshot.clockRunning)
                .apply()
        }

        @Synchronized
        fun resetAfterAppUpgrade(context: Context) {
            if (!publishedInThisProcess) save(context, load(context).afterAppUpgrade())
        }

        /** The last state published by the live playback service. */
        fun load(context: Context): MediaWidgetSnapshot {
            val prefs = prefs(context)
            val mediaId = prefs.getString(KEY_MEDIA_ID, null)
            if (mediaId != null) {
                return MediaWidgetSnapshot(
                    mediaId = mediaId,
                    title = prefs.getString(KEY_TITLE, "").orEmpty(),
                    artist = prefs.getString(KEY_ARTIST, "").orEmpty(),
                    artworkUrl = prefs.getString(KEY_ARTWORK, null),
                    isPlaying = prefs.getBoolean(KEY_PLAYING, false),
                    hasPrevious = prefs.getBoolean(KEY_HAS_PREVIOUS, false),
                    hasNext = prefs.getBoolean(KEY_HAS_NEXT, false),
                    isLiked = prefs.getBoolean(KEY_LIKED, false),
                    shuffleEnabled = prefs.getBoolean(KEY_SHUFFLE, false),
                    isLoading = prefs.getBoolean(KEY_LOADING, false),
                    controlsLocked = prefs.getBoolean(KEY_CONTROLS_LOCKED, false),
                    positionMs = prefs.getLong(KEY_POSITION_MS, 0L),
                    durationMs = prefs.getLong(KEY_DURATION_MS, 0L),
                    capturedAtElapsedMs = prefs.getLong(KEY_CAPTURED_ELAPSED_MS, 0L),
                    capturedAtEpochMs = prefs.getLong(KEY_CAPTURED_EPOCH_MS, 0L),
                    clockRunning = prefs.getBoolean(KEY_CLOCK_RUNNING, false),
                )
            }
            return EMPTY
        }

        private fun prefs(context: Context) =
            context.getSharedPreferences("bitchord_widget", Context.MODE_PRIVATE)

        private const val KEY_MEDIA_ID = "media_id"
        private const val KEY_TITLE = "title"
        private const val KEY_ARTIST = "artist"
        private const val KEY_ARTWORK = "artwork"
        private const val KEY_PLAYING = "playing"
        private const val KEY_HAS_PREVIOUS = "has_previous"
        private const val KEY_HAS_NEXT = "has_next"
        private const val KEY_LIKED = "liked"
        private const val KEY_SHUFFLE = "shuffle"
        private const val KEY_LOADING = "loading"
        private const val KEY_CONTROLS_LOCKED = "controls_locked"
        private const val KEY_POSITION_MS = "position_ms"
        private const val KEY_DURATION_MS = "duration_ms"
        private const val KEY_CAPTURED_ELAPSED_MS = "captured_elapsed_ms"
        private const val KEY_CAPTURED_EPOCH_MS = "captured_epoch_ms"
        private const val KEY_CLOCK_RUNNING = "clock_running"
    }
}
