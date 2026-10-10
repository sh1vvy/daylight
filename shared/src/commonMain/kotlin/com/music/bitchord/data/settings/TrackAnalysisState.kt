package com.music.bitchord.data.settings

/* Shared analysis and presentation state used by the Android player. */

/** Where one track stands in Automix's analysis. */
enum class TrackAnalysisState {
    /** Nothing in flight and no result — usually waiting on bytes to arrive. */
    WAITING,

    /** Decode and inference running now; a result is a few seconds away. */
    ANALYSING,

    /** Measured, with a tempo the planner can actually use. */
    ANALYSED,

    /**
     * Measured off the track's opening, with the whole-track pass running now to replace those
     * numbers with better ones.
     */
    REFINING,

    /** Tried and came back with nothing usable — a decode error, or audio that yielded no tempo. */
    FAILED,
}

/** Both sides of the next transition, for stats for nerds. */
data class SmartAnalysis(
    val current: TrackAnalysisState = TrackAnalysisState.WAITING,
    val next: TrackAnalysisState = TrackAnalysisState.WAITING,
)

/**
 * A span of the playing track, in fractions of its duration, that the next transition is planned to
 * occupy.
 */
data class TransitionWindow(val start: Float, val end: Float)

/**
 * An Automix blend in flight: the audible progress, ownership handoff and beat
 * grid. The UI draws the supplied progress; it does not drive audio timing.
 */
data class MixBlend(
    /**
     * Wall-clock length of one beat at the tempo the blend is actually playing
     * — after any beatmatch stretch and the listener's own speed — or 0 when
     * neither track's tempo is known.
     */
    val beatMs: Float,
    /** A `System.nanoTime()` at which a beat landed; with [beatMs], places every other one. */
    val beatAnchorNanos: Long,
    /** Whether the blend is playing rather than paused; a paused blend holds still. */
    val playing: Boolean,
    /** Speaker-time progress, never a UI timer. Pausing freezes this value. */
    val progress: Float = 0f,
    val outgoingId: String? = null,
    val incomingId: String? = null,
    val outgoingArtwork: String? = null,
    val incomingArtwork: String? = null,
    /** Metadata ownership changes at this fraction of the audible blend. */
    val handoffAt: Float = 0.5f,
    /** Cover dissolve's width in blend fractions (normally about 1.4 seconds). */
    val artworkSpan: Float = 0.16f,
)

/** Smooth cover dissolve around the audio engine's handoff; independent of frame rate. */
fun MixBlend.artworkFraction(): Float {
    val width = artworkSpan.coerceIn(0.001f, 1f)
    val start = (handoffAt - width / 2f).coerceIn(0f, 1f - width)
    val t = ((progress.coerceIn(0f, 1f) - start) / width).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
