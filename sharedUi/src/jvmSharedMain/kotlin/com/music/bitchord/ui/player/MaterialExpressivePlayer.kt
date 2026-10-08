package com.music.bitchord.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.theme.LocalMaterialExpressive
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.material_expressive_playback_position
import org.jetbrains.compose.resources.stringResource

/** Normal player art uses white ink; Material's light and dark surfaces use their matching ink. */
@Composable
@ReadOnlyComposable
internal fun playerContentColor(): Color =
    if (LocalMaterialExpressive.current) MaterialTheme.colorScheme.onSurface else Color.White

@Composable
@ReadOnlyComposable
internal fun playerSecondaryContentColor(normalAlpha: Float = 0.55f): Color =
    if (LocalMaterialExpressive.current) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = normalAlpha)

/** Material text roles carry their own contrast; stacked lyric-layer fades must not dim them again. */
internal fun lyricLayerAlpha(materialExpressive: Boolean, artworkAlpha: Float): Float =
    if (materialExpressive) 1f else artworkAlpha

/** Invalid stream positions must never reach Material's gesture or accessibility state. */
internal fun materialSeekFraction(value: Float): Float =
    if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

/**
 * Google's wavy indicator in the track slot of a real Material Slider. The Slider owns seeking,
 * RTL, keyboard input and TalkBack's set-progress action; the wave is presentation only.
 * A stopped wave stays visible without a continuously running frame clock.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MaterialExpressiveSeekBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    animateWave: Boolean,
    transitionWindow: ClosedFloatingPointRange<Float>?,
    mixCover: () -> Float = { 0f },
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val marker = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val seekLabel = stringResource(Res.string.material_expressive_playback_position)
    Slider(
        value = materialSeekFraction(value),
        onValueChange = { onValueChange(materialSeekFraction(it)) },
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = seekLabel },
        colors = SliderDefaults.colors(
            thumbColor = primary,
            activeTrackColor = primary,
            inactiveTrackColor = track,
        ),
        track = { state ->
            Box(Modifier.fillMaxWidth().height(14.dp)) {
                LinearWavyProgressIndicator(
                    progress = {
                        val position = materialSeekFraction(state.value)
                        position + (1f - position) * materialSeekFraction(mixCover())
                    },
                    color = primary,
                    trackColor = track,
                    wavelength = 28.dp,
                    waveSpeed = if (animateWave) 28.dp else 0.dp,
                    amplitude = { 1f },
                    modifier = Modifier.fillMaxWidth().height(14.dp),
                )
                transitionWindow?.let { window ->
                    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                        val from = materialSeekFraction(window.start)
                        val to = materialSeekFraction(window.endInclusive)
                        if (to > from && to > state.value) {
                            val center = size.height / 2f
                            drawLine(
                                color = marker,
                                start = Offset(size.width * if (rtl) 1f - maxOf(from, state.value) else maxOf(from, state.value), center),
                                end = Offset(size.width * if (rtl) 1f - to else to, center),
                                strokeWidth = 3.dp.toPx(),
                            )
                        }
                    }
                }
            }
        },
    )
}
