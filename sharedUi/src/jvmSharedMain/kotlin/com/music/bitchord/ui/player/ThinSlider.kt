package com.music.bitchord.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.settings.MixBlend
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos

/**
 * Apple Music's scrubber: a hairline capsule with no thumb knob, which
 * thickens under your finger and settles back when you let go. Material's
 * Slider can't be shaped like this — it always draws a thumb and a tall
 * track — so this is drawn directly.
 */
@Composable
fun ThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    /**
     * Sends the same travelling sheen across the bar while a version swap is crossfading.
     * Kept separate from [loading]: a mix is playback feedback, not a wait state. An Automix
     * blend draws [mixPulse] instead and suppresses this.
     */
    mixing: Boolean = false,
    /**
     * Automix's slow glow, shared with the Mixing label. The fill retains the actual
     * playhead position through the outgoing and incoming track.
     */
    mixPulse: MixPulse? = null,
    /**
     * Sends a travelling sheen across the whole bar — played *and* unplayed,
     * the bar's full thickness — for as long as it is true.
     *
     * The wait a version switch spends fetching and measuring the other cut is
     * a wait with no measurable fraction to draw, and a stock indeterminate
     * line sat on the scrubber like a second, uglier bar beside the one the
     * listener is already watching. The sheen claims the bar itself instead:
     * no extra chrome, no slot of its own, nothing shifting under it on the
     * frame the eye lands — just motion along the bar, pointing the way the
     * music is going.
     */
    loading: Boolean = false,
    /**
     * Span of the track, as fractions of its duration, that the next Automix
     * transition is planned to occupy. Drawn as a brighter stretch of the
     * unplayed bar so the mix is visible before it arrives.
     */
    transitionWindow: ClosedFloatingPointRange<Float>? = null,
    idleHeight: Dp = 7.dp,
    activeHeight: Dp = 12.dp,
    activeColor: Color = Color.White.copy(alpha = 0.92f),
    inactiveColor: Color = Color.White.copy(alpha = 0.26f),
    /** Halfway between the two track colours: visible against unplayed, invisible under played. */
    markerColor: Color = Color.White.copy(alpha = 0.5f),
) {
    var dragging by remember { mutableStateOf(false) }
    val height by animateDpAsState(
        targetValue = if (dragging) activeHeight else idleHeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "sliderHeight",
    )
    // The sheen gets a minimum beat even when the switch resolves instantly —
    // a cached offset can land in a few hundred milliseconds — so its entry,
    // its sweep and its hand-over to the progress bar always play out as one
    // continuous morph, whenever the wait happened to start and stop. A flip
    // back to loading during the hold cancels it and the sheen simply stays,
    // so rapid toggling never blinks the bar out mid-morph.
    var shownLoading by remember { mutableStateOf(loading) }
    var sheenStart by remember { mutableStateOf(System.nanoTime()) }
    LaunchedEffect(loading) {
        if (loading) {
            sheenStart = System.nanoTime()
            shownLoading = true
        } else {
            val remaining = LOADING_MIN_MS - (System.nanoTime() - sheenStart) / 1_000_000L
            if (remaining > 0) delay(remaining)
            shownLoading = false
        }
    }
    // Both operations let the sheen claim the bar. A version switch is latched for a minimum
    // beat; a version swap's crossfade follows the audio engine and fades when it finishes.
    // An Automix blend has a slow glow and keeps the progress fill.
    val sheenVisible = shownLoading || (mixing && mixPulse?.active != true)
    val fillFactor by animateFloatAsState(
        targetValue = if (sheenVisible) 0f else 1f,
        animationSpec = tween(durationMillis = MORPH_MS, easing = FastOutSlowInEasing),
        label = "fillFactor",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            // Generous invisible touch target — the visible bar is only ~7dp.
            .height(activeHeight + 22.dp)
            // One gesture loop for both taps and drags. Two separate detectors
            // — a drag one plus a tap one — meant taps never landed: the drag
            // detector took the pointer and a tap has no drag to report.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragging = true
                    onValueChange((down.position.x / size.width).coerceIn(0f, 1f))

                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pointer.pressed) {
                            pointer.consume()
                            break
                        }
                        if (pointer.positionChanged()) {
                            onValueChange((pointer.position.x / size.width).coerceIn(0f, 1f))
                            pointer.consume()
                        }
                    }

                    dragging = false
                    onValueChangeFinished?.invoke()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            val radius = CornerRadius(size.height / 2f)
            drawRoundRect(color = inactiveColor, cornerRadius = radius)
            // Between the two track colours, and drawn *under* the played fill:
            // once the playhead reaches the window the transition is no longer
            // upcoming, and the ordinary progress colour taking it over is what
            // says so.
            transitionWindow?.let { window ->
                val from = size.width * window.start.coerceIn(0f, 1f)
                val to = size.width * window.endInclusive.coerceIn(0f, 1f)
                if (to > from) {
                    drawRoundRect(
                        color = markerColor,
                        topLeft = Offset(from, 0f),
                        size = Size(to - from, size.height),
                        cornerRadius = radius,
                    )
                }
            }
            // Progress remains truthful through the handoff. The halo breathes gently
            // around the played portion; scrubbing always takes precedence.
            val cover = if (dragging) 0f else mixPulse?.cover ?: 0f
            val base = value.coerceIn(0f, 1f)
            val fraction = base
            // Scaled by [fillFactor]: retracted to nothing while the sheen
            // runs (two white signals on one bar would read as progress
            // fighting the wait) and slid back in when the switch lands. The
            // capsule's minimum width rides the same factor, so the nub at
            // zero progress retires with the fill instead of sitting as a
            // dot under the sheen.
            val filled = size.width * fraction * fillFactor
            if (filled > 0f) {
                // Three translucent capsules give a soft halo without a blur render pass.
                // Read the breath here: only this small canvas redraws on each active frame.
                if (cover > 0f && !dragging) {
                    val glow = cover * (0.55f + 0.45f * (mixPulse?.level ?: 0f))
                    for (spread in 1..3) {
                        val pad = spread * 2.dp.toPx()
                        drawRoundRect(
                            color = activeColor.copy(alpha = glow * 0.035f / spread),
                            topLeft = Offset(-pad, -pad),
                            size = Size(filled + pad * 2f, size.height + pad * 2f),
                            cornerRadius = CornerRadius(size.height / 2f + pad),
                        )
                    }
                }
                val alpha = if (dragging) activeColor.alpha else mixPulse?.alpha(activeColor.alpha) ?: activeColor.alpha
                drawRoundRect(
                    color = activeColor.copy(alpha = alpha),
                    size = Size(
                        filled.coerceAtLeast(size.height * fillFactor).coerceAtMost(size.width),
                        size.height,
                    ),
                    cornerRadius = radius,
                )
            }
        }
        // Composed only while switching or mixing. The infinite animation therefore costs no
        // frames during ordinary playback, and AnimatedVisibility lets it leave gracefully.
        AnimatedVisibility(
            visible = sheenVisible,
            // Grown out of the bar's own left end — where the progress fill
            // begins — instead of slid in from a third of its own width: the
            // capsule is full-bleed, so that slide started past the screen
            // edge and flew in from outside the display. Same duration and
            // curve as the fill's retraction, so the swap reads as one morph.
            enter = fadeIn(tween(durationMillis = MORPH_MS, easing = FastOutSlowInEasing)) +
                scaleIn(
                    animationSpec = tween(durationMillis = MORPH_MS, easing = FastOutSlowInEasing),
                    initialScale = 0f,
                    transformOrigin = TransformOrigin(0f, 0.5f),
                ),
            exit = fadeOut(tween(durationMillis = MORPH_MS, easing = FastOutSlowInEasing)),
        ) {
            MixSheen(height = height, color = activeColor)
        }
    }
}

private const val SHEEN_BAND_FRACTION = 0.34f

/**
 * One slow breathing clock for everything that glows with an Automix blend — the seek bar's fill and
 * the "Mixing" label — so the two can never drift out of step with each other.
 *
 * Every value here is snapshot state meant to be read in draw or a graphics layer, where a
 * change costs a redraw rather than a recomposition.
 */
@Stable
class MixPulse internal constructor(private val blend: () -> MixBlend?) {
    /**
     * Whether a blend is running right now. Derived, so composition that reads it recomposes
     * when a blend starts or ends, not for each engine progress update.
     */
    val active: Boolean by derivedStateOf { blend() != null }

    /** Whether the blend is playing; a paused one lets the pulse settle and the clock stop. */
    internal val playing: Boolean by derivedStateOf { blend()?.playing == true }

    internal val coverAnim = Animatable(0f)
    internal val depthAnim = Animatable(0f)
    internal var level by mutableFloatStateOf(0f)

    /** Kept across restarts of the clock, so a blend ending never jumps the phase. */
    internal var phase = 0.0

    /** 0..1: how far the blend has claimed the bar and the label, eased in and out. */
    val cover: Float get() = coverAnim.value

    /** Gently modulates opacity, returning to [fullAlpha] when paused or disabled. */
    fun alpha(fullAlpha: Float, restShare: Float = PULSE_REST): Float {
        val rest = fullAlpha * restShare
        val pulsed = rest + (fullAlpha - rest) * level
        return fullAlpha + (pulsed - fullAlpha) * depthAnim.value
    }

}

/**
 * Runs the breathing clock for [mixBlend]. [enabled] off (reduced motion) keeps the cover — the bar
 * keeps its progress and the label still shows — but holds them steady instead of pulsing.
 */
@Composable
fun rememberMixPulse(mixBlend: () -> MixBlend?, enabled: Boolean): MixPulse {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val animate = enabled && lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val current by rememberUpdatedState(mixBlend)
    val pulse = remember { MixPulse { current() } }
    val active = pulse.active
    val playing = pulse.playing
    LaunchedEffect(active, playing, animate) {
        launch {
            pulse.coverAnim.animateTo(
                if (active) 1f else 0f,
                tween(durationMillis = COVER_MS, easing = FastOutSlowInEasing),
            )
        }
        launch {
            // Paused mid-blend, the pulse settles to a steady bar rather than beating on
            // over silence — and the frame clock below gets to stop.
            pulse.depthAnim.animateTo(
                if (active && playing && animate) 1f else 0f,
                tween(durationMillis = PULSE_FADE_MS, easing = FastOutSlowInEasing),
            )
        }
        if (!animate) return@LaunchedEffect
        // One continuous, slow breath shared by the label and bar. Reads live engine
        // state for start/pause/end; the visual phase does not change audio timing.
        var last: Long? = null
        while ((active && playing) || pulse.depthAnim.value > 0f) {
            withFrameNanos { now ->
                val dt = (now - (last ?: now)).coerceIn(0L, MAX_FRAME_NANOS)
                last = now
                // A slow breath instead of flashing at the track's BPM. Pausing or
                // leaving the player stops this frame clock; no work between blends.
                var phase = pulse.phase + dt / BREATH_NANOS
                phase -= kotlin.math.floor(phase)
                pulse.phase = phase
                pulse.level = breathShape(phase.toFloat())
            }
        }
    }
    return pulse
}

/** How long the label and glow take to appear or retire around a blend. */
private const val COVER_MS = 700

/** How long the glow takes to swell in and settle. */
private const val PULSE_FADE_MS = 900

/** Minimum opacity, as a share of the fill's own: always legible. */
private const val PULSE_REST = 0.82f

/** A restrained breathing period independent of the track's BPM. */
private const val BREATH_NANOS = 3_600_000_000.0

/** A dropped frame or a backgrounded app must not fling the phase forward. */
private const val MAX_FRAME_NANOS = 100_000_000L

/** Raised cosine: smooth in value and slope throughout each slow breath. */
private fun breathShape(phase: Float): Float {
    val c = 0.5f + 0.5f * cos(2f * PI.toFloat() * phase)
    return c
}

/** Length of one morph step — entry, fill retraction, fill return, exit — all on the same curve. */
private const val MORPH_MS = 450

/** Shortest time the sheen stays up, so even an instant switch still plays its morph. */
private const val LOADING_MIN_MS = 600L

/**
 * A highlight sweeping the bar's full thickness — played *and* unplayed
 * alike — about once a second, at the progress fill's own brightness.
 *
 * Loading has no measurable fraction to draw, so an indeterminate indicator
 * has to draw *something*: the usual choice is a thin line claiming a
 * sliver of the scrubber's height, which reads as a second, lesser bar
 * growing out of the first. This band instead takes the whole thickness the
 * scrubber already occupies and moves along it, so the wait looks like the
 * bar itself moving rather than an alien element parked on top — no gap, no
 * slot, no shifting of the controls below it.
 *
 * One pass a second rather than the old two: any faster and the band is a
 * strobe the eye tracks instead of a wait it can ignore. The pass reverses
 * at each edge rather than restarting from the far one.
 */
@Composable
private fun MixSheen(height: Dp, color: Color) {
    val transition = rememberInfiniteTransition(label = "mixSheen")
    val phase by transition.animateFloat(
        initialValue = -SHEEN_BAND_FRACTION,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            // Reverses rather than restarting: a restart teleports the band
            // back to the far edge every second, which is a hitch the eye
            // catches each time. Ping-pong has no edge to fall off.
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mixSheenPhase",
    )
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val band = size.width * SHEEN_BAND_FRACTION
        val x = phase * size.width
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.5f to color,
                    1f to Color.Transparent,
                ),
                startX = x,
                endX = x + band,
            ),
            size = Size(size.width, size.height),
            // The band is clipped to the same capsule the track is drawn
            // with: a plain rect bared square corners wherever the sweep
            // crossed the rounded ends.
            cornerRadius = CornerRadius(size.height / 2f),
        )
    }
}
