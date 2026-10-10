package com.music.bitchord.ui.components

import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import kotlin.math.roundToInt

/** A compact menu grows from the actual ⋯ button; it never lifts the player into a sheet. */
@Composable
fun PlayerSongMenu(song: Song?, anchor: Rect?, onDismiss: () -> Unit, content: @Composable (Song) -> Unit) {
    var retained by remember { mutableStateOf<Song?>(null) }
    if (song != null) retained = song
    val shown = song ?: retained ?: return
    val reduced by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(song != null, reduced) {
        progress.animateTo(if (song != null) 1f else 0f, tween(if (reduced) 100 else if (song != null) 210 else 140, easing = FastOutSlowInEasing))
        if (song == null) retained = null
    }
    val insets = WindowInsets.safeDrawing
    val density = LocalDensity.current
    val topInset = insets.getTop(density)
    val bottomInset = insets.getBottom(density)
    // The portrait player is itself a dialog. An activity-owned Popup would sit
    // behind that window; a separate transparent dialog keeps the anchored menu
    // above both the full-screen player and the tablet's inline player.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
    )) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND) }
        Layout(modifier = Modifier.fillMaxSize(), content = {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.10f))
                .clickable(remember { MutableInteractionSource() }, indication = null, enabled = song != null, onClick = onDismiss))
            Box(Modifier.testTag("player-song-menu").pointerInput(song != null) {
                if (song == null) awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }.shadow(18.dp, RoundedCornerShape(26.dp))
                .clip(RoundedCornerShape(26.dp))) { content(shown) }
        }) { measurables, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val margin = 16.dp.roundToPx()
            val top = topInset + margin
            val bottom = (height - bottomInset - margin).coerceAtLeast(top)
            val menuWidth = minOf(280.dp.roundToPx(), (width - margin * 2).coerceAtLeast(1))
            val scrim = measurables[0].measure(Constraints.fixed(width, height))
            val menu = measurables[1].measure(Constraints(minWidth = menuWidth, maxWidth = menuWidth,
                maxHeight = minOf((height * 0.70f).roundToInt(), (bottom - top).coerceAtLeast(1))))
            val button = anchor ?: Rect(width - margin - 48.dp.toPx(), height * 0.52f, width - margin.toFloat(), height * 0.52f + 48.dp.toPx())
            val left = (button.right.roundToInt() - menuWidth).coerceIn(margin, (width - margin - menuWidth).coerceAtLeast(margin))
            // Keep the bottom edge by the song controls, just as in the reference.
            val y = (button.top.roundToInt() - menu.height + 16.dp.roundToPx()).coerceIn(top, (bottom - menu.height).coerceAtLeast(top))
            layout(width, height) {
                scrim.placeWithLayer(0, 0) { alpha = progress.value }
                menu.placeWithLayer(left, y) {
                    alpha = progress.value
                    transformOrigin = TransformOrigin(((button.center.x - left) / menu.width).coerceIn(0f, 1f), 1f)
                    if (!reduced) {
                        scaleX = 0.82f + 0.18f * progress.value
                        scaleY = scaleX
                    }
                }
            }
        }
    }
}
