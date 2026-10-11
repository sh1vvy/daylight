/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.player

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
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
    lyrics: LyricTimeline?,
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
    lyricTools: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnPanel by rememberUpdatedState(onPanel)
    BoxWithConstraints(modifier.fillMaxSize().background(colors.background).testTag("daylight-player")
        .queueSwipe(panel == PlayerPanel.ARTWORK, upward = true, onOpposite = actions.dismiss) { currentOnPanel(PlayerPanel.QUEUE) }) {
        val landscape = maxWidth > maxHeight && maxWidth >= 560.dp
        val short = maxHeight < 650.dp
        val artHeight = if (landscape) maxHeight else (maxHeight - if (short) 240.dp else 285.dp).coerceAtLeast(180.dp) + 95.dp
        val artWidth = if (landscape) maxWidth * .48f else maxWidth
        Box(Modifier.width(artWidth).height(artHeight).align(Alignment.TopStart)
            .pointerInput(track.id, session.controlsLocked, session.previous, session.next) {
                var distance = 0f
                detectHorizontalDragGestures(onDragStart = { distance = 0f }, onDragEnd = {
                    if (abs(distance) > size.width * .22f) when {
                        session.controlsLocked -> actions.blocked()
                        distance < 0 && session.next -> actions.next()
                        distance > 0 && session.previous -> actions.previous()
                    }
                }) { change, delta -> change.consume(); distance += delta }
            }.clickable(onClick = actions.artwork)) {
            artwork(Modifier.fillMaxSize().testTag("daylight-player-artwork"))
            Box(Modifier.fillMaxSize().drawWithCache {
                val bottom = Brush.verticalGradient(listOf(Color.Transparent, colors.background), startY = size.height * .46f, endY = size.height * .96f)
                val top = Brush.verticalGradient(listOf(Color.Black.copy(alpha = .6f), Color.Transparent), endY = 200.dp.toPx())
                val side = Brush.horizontalGradient(listOf(Color.Transparent, colors.background), startX = size.width * .7f)
                onDrawBehind { drawRect(bottom); drawRect(top); if (landscape) drawRect(side) }
            })
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.dismiss, modifier = Modifier.testTag("daylight-player-close")) {
                    Icon(Icons.Rounded.KeyboardArrowDown, labels.close, tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                // No screen title over the artwork. The track carries the hierarchy below.
            }
            if (landscape) {
                Row(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                    Spacer(Modifier.weight(1f))
                    Column(Modifier.weight(1.08f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                        PlayerPanelContent(panel, session, playhead, lyrics, queue, queueIndex, colors, labels, actions, onPanel, reduceMotion, lyricTools, Modifier.weight(1f))
                        PlayerDeck(track, session, playhead, colors, labels, actions, panel, onPanel, onMenuAnchor, reduceMotion, progress, output, short = true)
                    }
                }
            } else {
                PlayerPanelContent(panel, session, playhead, lyrics, queue, queueIndex, colors, labels, actions, onPanel, reduceMotion, lyricTools,
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp))
                PlayerDeck(track, session, playhead, colors, labels, actions, panel, onPanel, onMenuAnchor, reduceMotion, progress, output, short,
                    Modifier.fillMaxWidth().widthIn(max = 560.dp).padding(horizontal = 24.dp, vertical = 8.dp).align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun PlayerDeck(track: PlayerTrack, session: PlayerSession, playhead: PlayerPlayhead, colors: PlayerColors, labels: PlayerLabels,
    actions: PlayerActions, panel: PlayerPanel, onPanel: (PlayerPanel) -> Unit, onMenuAnchor: (Rect) -> Unit, reduceMotion: Boolean,
    progress: @Composable (Modifier, PlayerPlayhead, Boolean) -> Unit, output: @Composable () -> Unit, short: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconToggleButton(session.liked, { actions.like() }, enabled = session.canLike, modifier = Modifier.testTag("daylight-player-heart")) {
                Icon(if (session.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, labels.like, tint = if (session.liked) colors.accent else colors.ink)
            }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(track.title, color = colors.ink, fontSize = if (short) 18.sp else 21.sp, fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-.4).sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Text(track.artist, color = colors.muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(actions.menu, Modifier.size(48.dp).testTag("daylight-player-menu").onGloballyPositioned { onMenuAnchor(it.boundsInWindow()) }) {
                Icon(Icons.Rounded.MoreHoriz, labels.menu, tint = colors.ink)
            }
        }
        Spacer(Modifier.height(if (short) 4.dp else 10.dp))
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
private fun PlayerPanelContent(panel: PlayerPanel, session: PlayerSession, playhead: PlayerPlayhead, lyrics: LyricTimeline?,
    queue: List<PlayerTrack>, queueIndex: Int, colors: PlayerColors, labels: PlayerLabels, actions: PlayerActions,
    onPanel: (PlayerPanel) -> Unit, reduceMotion: Boolean, lyricTools: @Composable () -> Unit, modifier: Modifier) {
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
            PlayerPanel.LYRICS -> Column(Modifier.fillMaxSize().background(colors.background.copy(alpha = .95f), RoundedCornerShape(24.dp))) {
                lyricTools()
                if (lyrics == null || lyrics.lines.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (session.lyricsMissing) labels.lyricsUnavailable else labels.lyricsLoading, color = colors.ink)
                } else DaylightLyrics(lyrics, playhead, colors, labels.resumeLyrics, actions.seekMs, reduceMotion, Modifier.fillMaxSize())
            }
            PlayerPanel.QUEUE -> Column(Modifier.fillMaxSize().background(colors.background.copy(alpha = .94f), RoundedCornerShape(24.dp))) {
                Column(Modifier.fillMaxWidth().testTag("daylight-player-queue-handle")
                    .queueSwipe(true, upward = false) { onPanel(PlayerPanel.ARTWORK) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(32.dp, 4.dp).background(colors.muted.copy(alpha = .45f), RoundedCornerShape(2.dp)))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(labels.queue, color = colors.ink, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 16.dp))
                    IconToggleButton(session.shuffle, { actions.shuffle() }, enabled = !session.controlsLocked) { Icon(Icons.Rounded.Shuffle, labels.shuffle, tint = if (session.shuffle) colors.accent else colors.muted) }
                    IconButton(actions.repeat, enabled = !session.controlsLocked) { Icon(if (session.repeat == 1) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, labels.repeat, tint = if (session.repeat != 0) colors.accent else colors.muted) }
                    IconToggleButton(session.autoplay, { actions.autoplay() }, enabled = !session.controlsLocked) { Icon(Icons.Rounded.AllInclusive, labels.autoplay, tint = if (session.autoplay) colors.accent else colors.muted) }
                }
                }
                LazyColumn(Modifier.weight(1f).testTag("daylight-player-queue-list")) {
                    itemsIndexed(queue, key = { index, item -> "${item.key}:$index" }) { index, item ->
                        Row(Modifier.fillMaxWidth().background(if (index == queueIndex) colors.container else Color.Transparent, RoundedCornerShape(16.dp))
                            .clickable(enabled = !session.controlsLocked) { actions.jump(index) }.padding(start = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(item.artwork, null, Modifier.size(44.dp).graphicsLayer { shape = RoundedCornerShape(8.dp); clip = true }, contentScale = ContentScale.Crop)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(item.title, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.artist, color = colors.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton({ actions.remove(index) }, enabled = !session.controlsLocked) { Icon(Icons.Rounded.Close, labels.remove, tint = colors.muted) }
                        }
                    }
                    item { TextButton(actions.clear, enabled = !session.controlsLocked) { Text(labels.clear, color = colors.accent) } }
                }
            }
        }
    }
}

/** Vertical touch slop leaves taps, horizontal track swipes and the seek bar to their own controls. */
private fun Modifier.queueSwipe(enabled: Boolean, upward: Boolean, onOpposite: (() -> Unit)? = null, action: () -> Unit): Modifier =
    if (!enabled) this else pointerInput(upward) {
        val direction = if (upward) -1f else 1f
        val distanceThreshold = 40.dp.toPx()
        val flickThreshold = 600.dp.toPx()
        val velocity = VelocityTracker()
        var distance = 0f
        detectVerticalDragGestures(
            onDragStart = { velocity.resetTracking(); distance = 0f },
            onDragEnd = {
                if (distance * direction >= distanceThreshold ||
                    (distance * direction >= 12.dp.toPx() && velocity.calculateVelocity().y * direction >= flickThreshold)) action()
                else if (distance * direction <= -distanceThreshold) onOpposite?.invoke()
            },
            onVerticalDrag = { change, delta ->
                change.consume(); distance += delta; velocity.addPosition(change.uptimeMillis, change.position)
            },
        )
    }
