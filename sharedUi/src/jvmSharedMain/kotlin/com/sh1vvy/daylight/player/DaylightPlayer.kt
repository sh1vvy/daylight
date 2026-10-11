/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-12.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.isActive

@Immutable
data class PlayerColors(val background: Color, val ink: Color, val muted: Color, val accent: Color, val container: Color, val onAccent: Color)

enum class PlayerPanel { ARTWORK, LYRICS, QUEUE }

/** The clock is observed only by the seek bar and the lyric window. */
@Stable
class PlayerPlayhead {
    var millis by mutableLongStateOf(0)
        internal set
}

@Composable
fun rememberPlayerPlayhead(trackId: String, playing: Boolean, busy: Boolean, duration: Long, sample: () -> PlayheadSample): PlayerPlayhead {
    val playhead = remember(trackId) { PlayerPlayhead() }
    val currentSample by rememberUpdatedState(sample)
    LaunchedEffect(trackId, playing, busy, duration) {
        if (playing && !busy) {
            while (isActive) withFrameNanos { now -> playhead.millis = projectedPosition(currentSample(), now, true, duration) }
        } else {
            snapshotFlow { currentSample() }.collect { playhead.millis = projectedPosition(it, System.nanoTime(), false, duration) }
        }
    }
    return playhead
}

/** Original Daylight presentation. Audio, providers and platform output are supplied through slots. */
@Composable
fun DaylightPlayer(
    track: PlayerTrack,
    session: PlayerSession,
    playhead: PlayerPlayhead,
    queue: List<PlayerTrack>,
    queueIndex: Int,
    colors: PlayerColors,
    labels: PlayerLabels,
    actions: PlayerActions,
    reduceMotion: Boolean,
    panel: PlayerPanel,
    onPanel: (PlayerPanel) -> Unit,
    onMenuAnchor: (Rect) -> Unit,
    artwork: @Composable (Modifier) -> Unit,
    progress: @Composable (Modifier, PlayerPlayhead, Boolean) -> Unit,
    output: @Composable () -> Unit,
    lyricsContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    motion: PlayerSurfaceMotion? = null,
    queueBlurEnabled: Boolean = true,
) {
    val currentOnPanel by rememberUpdatedState(onPanel)
    val panelExpansion = animateFloatAsState(if (panel == PlayerPanel.ARTWORK) 0f else 1f,
        if (reduceMotion) tween(0) else spring(.92f, 240f), label = "panel-layout")
    val density = LocalDensity.current
    val compactInline = (motion?.miniArtwork?.invoke()?.height ?: with(density) { 40.dp.toPx() }) < with(density) { 36.dp.toPx() }
    var anchor by remember { mutableStateOf(Offset.Zero) }
    val expansion = { motion?.expansion?.invoke()?.coerceIn(0f, 1f) ?: 1f }
    val surface = { bounds: Rect -> motion?.miniSurface?.invoke()?.translate(-anchor)?.let { morphBounds(bounds, it, expansion()) } ?: bounds }
    val path = remember { Path() }
    val statusTop = WindowInsets.statusBars.getTop(density)
    val navigationBottom = WindowInsets.navigationBars.getBottom(density)
    val host = modifier.fillMaxSize().testTag("daylight-player")
        .onGloballyPositioned { coordinates ->
            anchor = coordinates.positionOnScreen() - Offset(0f, (motion?.sheetOffset?.invoke() ?: 0f).toInt().toFloat())
        }
        // Counter the window's sheet placement: the scene itself travels into the measured pill.
        .offset { IntOffset(0, -(motion?.sheetOffset?.invoke() ?: 0f).toInt()) }
        .drawWithContent {
            val content = this
            val bounds = surface(Rect(Offset.Zero, size))
            val compactHeight = motion?.miniSurface?.invoke()?.height ?: 56.dp.toPx()
            val radius = (1f - expansion()) * compactHeight / 2f
            path.reset(); path.addRoundRect(RoundRect(bounds, CornerRadius(radius)))
            clipPath(path) {
                drawRect(lerp(motion?.miniColor ?: colors.background, colors.background, expansion()))
                content.drawContent()
            }
        }.surfaceSwipe(panel == PlayerPanel.ARTWORK, motion, { currentOnPanel(PlayerPanel.QUEUE) }, actions.dismiss)
    Layout(content = {
        Box(Modifier.graphicsLayer {
                val open = expansion()
                val panel = panelExpansion.value.coerceIn(0f, 1f)
                val compactRadius = motion?.miniArtworkRadius?.invoke() ?: 8.dp.toPx()
                val radius = androidx.compose.ui.util.lerp(panel * 8.dp.toPx(), compactRadius, coverFold(open, panel))
                shape = RoundedCornerShape(with(density) { radius.toDp() }); clip = true
            }
            .pointerInput(track.id, session.controlsLocked, session.previous, session.next, panel) {
                if (panel != PlayerPanel.ARTWORK) return@pointerInput
                var distance = 0f
                detectHorizontalDragGestures(onDragStart = { distance = 0f }, onDragEnd = {
                    if (abs(distance) > size.width * .22f) when {
                        session.controlsLocked -> actions.blocked()
                        distance < 0 && session.next -> actions.next()
                        distance > 0 && session.previous -> actions.previous()
                    }
                }) { change, delta -> change.consume(); distance += delta }
            }.testTag("daylight-player-artwork-action")
            .clickable(onClickLabel = if (panel != PlayerPanel.ARTWORK) labels.returnToPlayer else null) {
                if (panel == PlayerPanel.ARTWORK) actions.artwork() else currentOnPanel(PlayerPanel.ARTWORK)
            }) {
            artwork(Modifier.fillMaxSize().testTag("daylight-player-artwork"))
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - panelExpansion.value }
                .drawWithCache {
                    val bottom = Brush.verticalGradient(listOf(Color.Transparent, colors.background), startY = size.height * .46f, endY = size.height * .96f)
                    val top = Brush.verticalGradient(listOf(Color.Black.copy(alpha = .6f), Color.Transparent), endY = 200.dp.toPx())
                    onDrawBehind { drawRect(bottom); drawRect(top) }
                })
        }
        // Main-view dismissal remains gesture-first, with an invisible accessible target.
        Box(Modifier.fillMaxWidth().graphicsLayer { alpha = expansion() * (1f - panelExpansion.value) }
            .testTag("daylight-player-close")
            .clickable(enabled = panel == PlayerPanel.ARTWORK, onClickLabel = labels.close, onClick = actions.dismiss)
            .semantics { if (panel == PlayerPanel.ARTWORK) contentDescription = labels.close })
        PlayerTrackHeader(track, session, colors, labels, actions, onMenuAnchor,
            Modifier.graphicsLayer { alpha = ((expansion() - .2f) / .8f).coerceIn(0f, 1f) })
        PlayerPanelContent(panel, session, queue, queueIndex, colors, labels, actions, reduceMotion, queueBlurEnabled, lyricsContent,
            Modifier.graphicsLayer { alpha = expansion() })
        PlayerDeck(track, session, playhead, colors, labels, actions, panel, onPanel, onMenuAnchor, reduceMotion, progress, output,
            short = false, modifier = Modifier.graphicsLayer { alpha = ((expansion() - .35f) / .65f).coerceIn(0f, 1f) })
        // Compact text/transport blend in only at the end; they have no duplicate accessibility nodes.
        Row(Modifier.clearAndSetSemantics {}.graphicsLayer { alpha = ((.32f - expansion()) / .32f).coerceIn(0f, 1f) },
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(track.title, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!compactInline) Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.size(if (compactInline) 32.dp else 40.dp), contentAlignment = Alignment.Center) {
                Icon(if (session.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(if (compactInline) 24.dp else 32.dp), tint = MaterialTheme.colorScheme.onBackground)
            }
            if (!compactInline) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.FastForward, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
        // The compact panel handle returns to artwork; only the main view can minimize.
        Box(Modifier.fillMaxWidth().graphicsLayer { alpha = expansion() * panelExpansion.value }
            .testTag("daylight-player-panel-return")
            .panelReturnSwipe(panel != PlayerPanel.ARTWORK) { currentOnPanel(PlayerPanel.ARTWORK) }
            .clickable(enabled = panel != PlayerPanel.ARTWORK, onClickLabel = labels.returnToPlayer) {
                currentOnPanel(PlayerPanel.ARTWORK)
            }.semantics { if (panel != PlayerPanel.ARTWORK) contentDescription = labels.returnToPlayer },
            contentAlignment = Alignment.Center) {
            Box(Modifier.size(32.dp, 4.dp).background(colors.muted.copy(alpha = .45f), RoundedCornerShape(2.dp)))
        }
    }, modifier = host) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val gutter = 24.dp.roundToPx()
        val t = expansion()
        val panelT = panelExpansion.value.coerceIn(0f, 1f)
        val landscape = width > height && width >= 560.dp.roundToPx()
        val laneWidth = if (landscape) width / 2 else width
        val laneX = width - laneWidth
        val deckWidth = (laneWidth - gutter * 2).coerceAtLeast(1)
        val deck = measurables[4].measure(Constraints(maxWidth = deckWidth, minWidth = deckWidth, maxHeight = (height * .6f).toInt()))
        val deckY = (height - navigationBottom - 8.dp.roundToPx() - deck.height).coerceAtLeast(statusTop + 48.dp.roundToPx())
        // Let the 48dp touch targets overhang the gutter; the visible 24dp icons align with timers.
        val headerOverhang = ((1f - panelT) * 12.dp.toPx()).roundToInt()
        val headerWidth = (deckWidth + headerOverhang * 2 - (panelT * 56.dp.toPx()).roundToInt()).coerceAtLeast(1)
        val header = measurables[2].measure(Constraints(minWidth = headerWidth, maxWidth = headerWidth, maxHeight = height))
        val fullHeaderY = (deckY - header.height - 10.dp.roundToPx()).coerceAtLeast(statusTop + 48.dp.roundToPx())
        val panelHeaderY = statusTop + 48.dp.roundToPx()
        val headerY = androidx.compose.ui.util.lerp(fullHeaderY.toFloat(), panelHeaderY.toFloat(), panelT).roundToInt()
        val expandedArt = if (landscape) Rect(0f, 0f, laneX.toFloat(), height.toFloat()) else Rect(0f, 0f, width.toFloat(), (fullHeaderY + header.height + 85.dp.roundToPx()).toFloat())
        val panelArt = Rect((laneX + gutter).toFloat(), panelHeaderY.toFloat(), (laneX + gutter + 48.dp.roundToPx()).toFloat(), (panelHeaderY + 48.dp.roundToPx()).toFloat())
        val artInPlayer = if (landscape) expandedArt else morphBounds(expandedArt, panelArt, 1f - panelT)
        val targetArt = motion?.miniArtwork?.invoke()?.translate(-anchor)
        val surfaceRect = surface(Rect(0f, 0f, width.toFloat(), height.toFloat()))
        val artRect = if (targetArt != null && !landscape && panelT == 0f && t < 1f) {
            // Keep the sleeve full-bleed through the large part of the descent. It folds into
            // the cover only as the controls retire and the surface reaches the mini player.
            val sleeve = Rect(surfaceRect.left, surfaceRect.top, surfaceRect.right,
                surfaceRect.top + (artInPlayer.height * (.5f + .5f * t)).coerceAtMost(surfaceRect.height))
            morphBounds(sleeve, targetArt, 1f - coverFold(t, panelT))
        } else if (targetArt != null) morphBounds(artInPlayer, targetArt, t) else artInPlayer
        val art = measurables[0].measure(Constraints.fixed(artRect.width.roundToInt().coerceAtLeast(1), artRect.height.roundToInt().coerceAtLeast(1)))
        val close = measurables[1].measure(Constraints.fixed(deckWidth, 48.dp.roundToPx()))
        val panelY = panelHeaderY + header.height + 14.dp.roundToPx()
        val body = measurables[3].measure(Constraints.fixed(deckWidth, (deckY - panelY - 12.dp.roundToPx()).coerceAtLeast(1)))
        val mini = motion?.miniSurface?.invoke()?.translate(-anchor)
        val compactX = (targetArt?.right ?: (mini?.left ?: 0f) + 52.dp.toPx()) + (if (compactInline) 8.dp else 10.dp).toPx()
        val compactW = ((mini?.right ?: width.toFloat()) - compactX - (if (compactInline) 8.dp else 12.dp).toPx()).roundToInt().coerceAtLeast(1)
        val compact = measurables[5].measure(Constraints.fixed(compactW, (mini?.height ?: 56.dp.toPx()).roundToInt().coerceAtLeast(1)))
        val panelReturn = measurables[6].measure(Constraints.fixed(deckWidth, 48.dp.roundToPx()))
        layout(width, height) {
            art.place(artRect.left.roundToInt(), artRect.top.roundToInt())
            if (panelT < 1f) close.place(laneX + gutter, statusTop)
            body.place(laneX + gutter, panelY)
            val destinationY = mini?.top ?: headerY.toFloat()
            header.place(laneX + gutter - headerOverhang + (panelT * 56.dp.toPx()).roundToInt(), androidx.compose.ui.util.lerp(destinationY, headerY.toFloat(), t).roundToInt())
            deck.place(laneX + gutter, androidx.compose.ui.util.lerp(destinationY, deckY.toFloat(), t).roundToInt())
            compact.place(compactX.roundToInt(), (mini?.top ?: height.toFloat()).roundToInt())
            if (panelT > 0f) panelReturn.place(laneX + gutter, statusTop)
        }
    }
}

@Composable
private fun PlayerTrackHeader(track: PlayerTrack, session: PlayerSession, colors: PlayerColors, labels: PlayerLabels,
    actions: PlayerActions, onMenuAnchor: (Rect) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconToggleButton(session.liked, { actions.like() }, enabled = session.canLike, modifier = Modifier.testTag("daylight-player-heart")) {
            Icon(if (session.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, labels.like,
                modifier = Modifier.size(24.dp).testTag("daylight-player-heart-glyph"), tint = if (session.liked) colors.accent else colors.ink)
        }
        Column(Modifier.weight(1f).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(track.title, color = colors.ink, fontSize = 21.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = (-.4).sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(track.artist, color = colors.muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(actions.menu, Modifier.size(48.dp).testTag("daylight-player-menu").onGloballyPositioned { onMenuAnchor(it.boundsInWindow()) }) {
            Icon(Icons.Rounded.MoreHoriz, labels.menu, Modifier.size(24.dp).testTag("daylight-player-menu-glyph"), tint = colors.ink)
        }
    }
}

@Composable
private fun PlayerDeck(track: PlayerTrack, session: PlayerSession, playhead: PlayerPlayhead, colors: PlayerColors, labels: PlayerLabels,
    actions: PlayerActions, panel: PlayerPanel, onPanel: (PlayerPanel) -> Unit, onMenuAnchor: (Rect) -> Unit, reduceMotion: Boolean,
    progress: @Composable (Modifier, PlayerPlayhead, Boolean) -> Unit, output: @Composable () -> Unit, short: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        progress(Modifier.fillMaxWidth(), playhead, !session.controlsLocked && session.durationMs > 0)
        Spacer(Modifier.height(if (short) 2.dp else 10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TransportButton(labels.previous, Icons.Rounded.FastRewind, session.previous && !session.controlsLocked, colors.container, colors.ink,
                actions.previous, reduceMotion, Modifier.weight(1f).height(if (short) 64.dp else 80.dp))
            PlayButton(session, labels, colors, actions.playPause, reduceMotion, Modifier.weight(1.18f).height(if (short) 72.dp else 88.dp))
            TransportButton(labels.next, Icons.Rounded.FastForward, session.next && !session.controlsLocked, colors.container, colors.ink,
                actions.next, reduceMotion, Modifier.weight(1f).height(if (short) 64.dp else 80.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = if (short) 0.dp else 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconToggleButton(checked = panel == PlayerPanel.LYRICS, onCheckedChange = { onPanel(if (it) PlayerPanel.LYRICS else PlayerPanel.ARTWORK) },
                enabled = !session.lyricsMissing, modifier = Modifier.testTag("daylight-player-lyrics")) {
                Icon(Icons.Rounded.FormatQuote, labels.lyrics, tint = if (session.lyricsMissing) colors.muted.copy(alpha = .4f) else if (panel == PlayerPanel.LYRICS) colors.accent else colors.muted)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { output() }
            IconToggleButton(panel == PlayerPanel.QUEUE, { onPanel(if (it) PlayerPanel.QUEUE else PlayerPanel.ARTWORK) }, Modifier.testTag("daylight-player-queue")) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, labels.queue, tint = if (panel == PlayerPanel.QUEUE) colors.accent else colors.muted)
            }
        }
    }
}

@Composable
private fun PlayButton(session: PlayerSession, labels: PlayerLabels, colors: PlayerColors, action: () -> Unit, reduceMotion: Boolean, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val corner = animateFloatAsState(if (session.playing) 28f else 36f, if (reduceMotion) tween(0) else spring(.9f, Spring.StiffnessMedium), label = "play-shape")
    val scale = animateFloatAsState(if (pressed) .94f else 1f, if (reduceMotion) tween(0) else spring(.7f, Spring.StiffnessMedium), label = "play-press")
    FilledTonalButton(onClick = action, enabled = !session.busy, interactionSource = source,
        shape = RoundedCornerShape(corner.value.dp), contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = colors.accent, contentColor = colors.onAccent,
            disabledContainerColor = colors.accent.copy(alpha = .65f), disabledContentColor = colors.onAccent),
        modifier = modifier.testTag("daylight-player-play").semantics { contentDescription = if (session.playing) labels.pause else labels.play }
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }) {
        if (session.busy) CircularProgressIndicator(Modifier.size(26.dp), color = colors.onAccent, strokeWidth = 2.dp)
        else AnimatedContent(session.playing, transitionSpec = {
            (fadeIn(tween(if (reduceMotion) 0 else 140)) + scaleIn(tween(if (reduceMotion) 0 else 180), initialScale = .85f)) togetherWith
                (fadeOut(tween(if (reduceMotion) 0 else 90)) + scaleOut(tween(if (reduceMotion) 0 else 120), targetScale = .85f))
        }, label = "play-glyph") { playing ->
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(38.dp), tint = colors.onAccent)
        }
    }
}

@Composable
private fun TransportButton(label: String, icon: ImageVector, enabled: Boolean, container: Color, ink: Color, action: () -> Unit,
    reduceMotion: Boolean, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) .94f else 1f, if (reduceMotion) tween(0) else spring(.7f, Spring.StiffnessMedium), label = "transport-press")
    FilledTonalButton(action, modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }, enabled = enabled,
        shape = RoundedCornerShape(24.dp), interactionSource = source, contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = container, contentColor = ink,
            disabledContainerColor = container.copy(alpha = .6f), disabledContentColor = ink.copy(alpha = .3f))) {
        Icon(icon, label, Modifier.size(30.dp))
    }
}

@Composable
private fun PlayerPanelContent(panel: PlayerPanel, session: PlayerSession,
    queue: List<PlayerTrack>, queueIndex: Int, colors: PlayerColors, labels: PlayerLabels, actions: PlayerActions,
    reduceMotion: Boolean, queueBlurEnabled: Boolean, lyricsContent: @Composable (Modifier) -> Unit, modifier: Modifier) {
    AnimatedContent(panel, modifier, transitionSpec = {
        when {
            reduceMotion -> fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            targetState == PlayerPanel.QUEUE -> (slideInVertically(spring(.9f, Spring.StiffnessMediumLow)) { it } + fadeIn(tween(180))) togetherWith fadeOut(tween(160))
            initialState == PlayerPanel.QUEUE -> fadeIn(tween(180)) togetherWith (slideOutVertically(tween(260)) { it } + fadeOut(tween(220)))
            else -> fadeIn(tween(220)) togetherWith fadeOut(tween(160))
        }
    }, label = "player-panel") { shown ->
        when (shown) {
            PlayerPanel.ARTWORK -> Spacer(Modifier.fillMaxSize())
            PlayerPanel.LYRICS -> Box(Modifier.fillMaxSize().testTag("daylight-player-lyric-list")) {
                lyricsContent(Modifier.fillMaxSize())
            }
            PlayerPanel.QUEUE -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlaybackModePill(if (session.shuffle) labels.shuffleOn else labels.shuffleOff, Icons.Rounded.Shuffle,
                        session.shuffle, !session.controlsLocked, colors, actions.shuffle, reduceMotion,
                        Modifier.weight(1f).testTag("daylight-player-shuffle"))
                    PlaybackModePill(when (session.repeat) { 1 -> labels.repeatOne; 2 -> labels.repeatAll; else -> labels.repeatOff },
                        if (session.repeat == 1) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        session.repeat != 0, !session.controlsLocked, colors, actions.repeat, reduceMotion,
                        Modifier.weight(1f).testTag("daylight-player-repeat"))
                    PlaybackModePill(if (session.autoplay) labels.autoplayOn else labels.autoplayOff, Icons.Rounded.AllInclusive,
                        session.autoplay, !session.controlsLocked, colors, actions.autoplay, reduceMotion,
                        Modifier.weight(1f).testTag("daylight-player-autoplay"))
                }
                DaylightQueue(queue, queueIndex, session.controlsLocked, colors, labels, actions,
                    reduceMotion, queueBlurEnabled, Modifier.weight(1f))
            }
        }
    }
}

/** Queue-only playback modes: contrasting fills, with explicit states for accessibility. */
@Composable
private fun PlaybackModePill(label: String, icon: ImageVector, selected: Boolean, enabled: Boolean,
    colors: PlayerColors, action: () -> Unit, reduceMotion: Boolean, modifier: Modifier) {
    val container = animateColorAsState(if (selected) colors.ink else colors.ink.copy(alpha = .10f),
        tween(if (reduceMotion) 0 else 180), label = "queue-mode-fill")
    val ink = animateColorAsState(if (selected) colors.background else colors.ink,
        tween(if (reduceMotion) 0 else 180), label = "queue-mode-ink")
    FilledTonalButton(action, modifier.heightIn(min = 48.dp).semantics {
        this.selected = selected
        contentDescription = label
    }, enabled = enabled, shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = container.value, contentColor = ink.value,
            disabledContainerColor = container.value.copy(alpha = .25f), disabledContentColor = ink.value.copy(alpha = .4f))) {
        Icon(icon, null, Modifier.size(24.dp))
    }
}

/** Vertical touch slop leaves taps, horizontal track swipes and the seek bar to their own controls. */
private fun Modifier.panelReturnSwipe(enabled: Boolean, action: () -> Unit): Modifier =
    if (!enabled) this else pointerInput(Unit) {
        val distanceThreshold = 24.dp.toPx()
        val flickThreshold = 600.dp.toPx()
        val velocity = VelocityTracker()
        var distance = 0f
        detectVerticalDragGestures(
            onDragStart = { velocity.resetTracking(); distance = 0f },
            onDragEnd = {
                if (distance >= distanceThreshold ||
                    (distance >= 12.dp.toPx() && velocity.calculateVelocity().y >= flickThreshold)) action()
            },
            onVerticalDrag = { change, delta ->
                change.consume(); distance += delta; velocity.addPosition(change.uptimeMillis, change.position)
            },
        )
    }

/** Up opens the queue; down directly drives the surface, preserving velocity on release. */
private fun Modifier.surfaceSwipe(enabled: Boolean, motion: PlayerSurfaceMotion?, queue: () -> Unit, dismiss: () -> Unit): Modifier =
    if (!enabled) this else pointerInput(motion) {
        val tracker = VelocityTracker()
        var distance = 0f
        var closing = false
        detectVerticalDragGestures(onDragStart = { distance = 0f; closing = false; tracker.resetTracking() },
            onDragCancel = { if (closing) motion?.release?.invoke(0f) }, onDragEnd = {
                if (closing) motion?.release?.invoke(tracker.calculateVelocity().y)
                else if (distance < -40.dp.toPx()) queue()
                else if (distance > 40.dp.toPx() && motion == null) dismiss()
            }) { change, delta ->
                change.consume(); tracker.addPosition(change.uptimeMillis, change.position)
                distance += delta
                if (!closing && distance > 0f && motion != null) { closing = true; motion.drag(distance) }
                else if (closing) motion?.drag?.invoke(delta)
            }
    }
