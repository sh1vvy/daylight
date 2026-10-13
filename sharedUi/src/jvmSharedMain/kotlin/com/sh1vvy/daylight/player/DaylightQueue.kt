/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-12 through 2026-10-13.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.sh1vvy.daylight.motion.SwipeActionGate
import com.sh1vvy.daylight.motion.observeSwipeRelease
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Daylight's upcoming-only queue. A drag previews locally and commits one timeline move on drop. */
@OptIn(ExperimentalHazeApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun DaylightQueue(queue: List<PlayerTrack>, playingIndex: Int, locked: Boolean,
    colors: PlayerColors, labels: PlayerLabels, actions: PlayerActions,
    reduced: Boolean, blurEnabled: Boolean, modifier: Modifier) {
    val entries = remember(queue, playingIndex) { upcomingTracks(queue, playingIndex) }
    val list = rememberLazyListState()
    val drag = remember(list) { QueueReorder(list) }
    val density = LocalDensity.current
    val release = remember(drag.drop) { Animatable(drag.releaseOffset) }
    val haze = remember { HazeState() }
    SideEffect {
        drag.source = entries
        drag.onMove = actions.move
        drag.onActive = actions.queueDragActive
        drag.edge = with(density) { 44.dp.toPx() }
        drag.speed = with(density) { 280.dp.toPx() }
    }
    LaunchedEffect(entries, locked) { drag.reconcile(entries, locked) }
    DisposableEffect(drag) { onDispose { drag.cancel() } }
    LaunchedEffect(playingIndex) { if (drag.held == null) list.scrollToItem(0) }
    LaunchedEffect(drag.drop) {
        if (drag.settling != null) {
            release.snapTo(drag.releaseOffset)
            if (reduced) release.snapTo(0f) else release.animateTo(0f, spring(.88f, 420f))
            drag.settling = null
        }
    }
    LaunchedEffect(drag.waiting) {
        if (drag.waiting) { delay(700); drag.finishPending() }
    }
    LaunchedEffect(drag.held) {
        if (drag.held == null) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (isActive && drag.held != null) {
            val now = withFrameNanos { it }
            val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(1f / 30)
            previous = now
            val scroll = drag.scrollSpeed()
            if (scroll != 0f && list.scrollBy(scroll * seconds) != 0f) drag.shift(0f)
        }
    }
    val rows = drag.preview ?: entries
    fun liveIndex(key: String) = entries.firstOrNull { it.key == key }?.index
    fun moveAccessible(entry: UpcomingTrack, direction: Int): Boolean {
        if (locked || drag.held != null || drag.waiting) return false
        val from = entries.indexOfFirst { it.key == entry.key }
        val target = entries.getOrNull(from + direction) ?: return false
        if (previewQueueMove(entries, entry.key, target.key) === entries) return false
        actions.queueDragActive(true)
        try { actions.move(entry.index, target.index) } finally { actions.queueDragActive(false) }
        return true
    }
    Column(modifier) {
        Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 12.dp)) {
            Text(labels.continuePlaying, style = MaterialTheme.typography.titleMedium, color = colors.ink)
            if (labels.source.isNotBlank()) Text(labels.source, style = MaterialTheme.typography.bodySmall,
                color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize().testTag("daylight-player-queue-list")
                .then(if (blurEnabled) Modifier.hazeSource(haze) else Modifier), state = list,
                contentPadding = PaddingValues(bottom = 48.dp)) {
                if (rows.isEmpty()) item {
                    Text(labels.queueEmpty, color = colors.muted, modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp))
                }
                items(rows, key = UpcomingTrack::key) { entry ->
                    val held = drag.held == entry.key
                    val mutable = !locked && drag.held == null && !drag.waiting
                    val currentRemove by rememberUpdatedState<() -> Unit>({
                        if (mutable) liveIndex(entry.key)?.let(actions.remove)
                    })
                    val commitGate = remember(entry.key) { SwipeActionGate() }
                    val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) commitGate.commit { currentRemove() }
                        false
                    }, positionalThreshold = { it * .4f })
                    val accessibility = listOf(
                        CustomAccessibilityAction(labels.remove) { if (!mutable) false else liveIndex(entry.key)?.let { actions.remove(it); true } ?: false },
                        CustomAccessibilityAction(labels.moveEarlier) { moveAccessible(entry, -1) },
                        CustomAccessibilityAction(labels.moveLater) { moveAccessible(entry, 1) },
                    )
                    SwipeToDismissBox(swipe, modifier = Modifier.observeSwipeRelease(commitGate)
                        .animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = if (reduced || held) null else spring(.9f, 420f))
                        .zIndex(if (held || drag.settling == entry.key) 1f else 0f)
                        .graphicsLayer {
                            translationY = when {
                                held -> drag.top - (list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == entry.key }?.offset ?: 0)
                                drag.settling == entry.key -> release.value
                                else -> 0f
                            }
                            shadowElevation = if (held) 8.dp.toPx() else 0f
                            shape = RoundedCornerShape(12.dp); clip = true
                        }.testTag("daylight-queue-row-${entry.key}")
                        .semantics { customActions = accessibility },
                        enableDismissFromStartToEnd = false, enableDismissFromEndToStart = mutable,
                        backgroundContent = {
                            Box(Modifier.fillMaxSize().padding(end = 8.dp), contentAlignment = Alignment.CenterEnd) {
                                Box(Modifier.size(56.dp, 40.dp).background(MaterialTheme.colorScheme.error, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.RemoveCircle, null, tint = MaterialTheme.colorScheme.onError)
                                }
                            }
                        }) {
                        Row(Modifier.fillMaxWidth().background(if (held) colors.container else colors.background)
                            .clickable(enabled = mutable) { liveIndex(entry.key)?.let(actions.jump) }
                            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(entry.track.artwork, null, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(entry.track.title, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(entry.track.artist, color = colors.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Box(Modifier.size(48.dp).testTag("daylight-queue-drag-${entry.key}")
                                .semantics { contentDescription = "${labels.reorder}: ${entry.track.title}"; customActions = accessibility }
                                .pointerInput(entry.key, locked) {
                                    if (locked) return@pointerInput
                                    detectVerticalDragGestures(onDragStart = { drag.start(entry.key) },
                                        onDragEnd = { drag.end() }, onDragCancel = { drag.cancel() }) { change, amount ->
                                        if (drag.held == entry.key) { change.consume(); drag.shift(amount) }
                                    }
                                }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Menu, null, Modifier.size(22.dp), tint = colors.muted.copy(alpha = if (locked) .35f else .65f))
                            }
                        }
                    }
                }
                if (rows.isNotEmpty()) item {
                    TextButton(actions.clear, enabled = !locked && drag.held == null && !drag.waiting,
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp)) { Text(labels.clear, color = colors.accent) }
                }
            }
            // Sample only a narrow strip at one-third resolution; never blur the full player.
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(40.dp).testTag("daylight-player-queue-bottom-fade")
                .then(if (blurEnabled) Modifier.hazeEffect(haze,
                    HazeStyle(backgroundColor = colors.background, tints = emptyList(), blurRadius = 10.dp,
                        noiseFactor = 0f, fallbackTint = HazeTint(colors.background))) {
                    inputScale = HazeInputScale.Fixed(.33f)
                    mask = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black)
                } else Modifier)
                .background(Brush.verticalGradient(0f to Color.Transparent, .45f to colors.background.copy(alpha = .12f), 1f to colors.background)))
        }
    }
}

/** Viewport-space dragging is independent of animated item placement and the audio timeline. */
private class QueueReorder(private val list: LazyListState) {
    var source: List<UpcomingTrack> = emptyList()
    var onMove: (Int, Int) -> Unit = { _, _ -> }
    var onActive: (Boolean) -> Unit = {}
    var edge = 0f
    var speed = 0f
    var held by mutableStateOf<String?>(null)
        private set
    var preview by mutableStateOf<List<UpcomingTrack>?>(null)
        private set
    var top by mutableFloatStateOf(0f)
        private set
    var settling by mutableStateOf<String?>(null)
    var releaseOffset = 0f
    var drop by mutableIntStateOf(0)
        private set
    var waiting by mutableStateOf(false)
        private set
    private var original: List<UpcomingTrack> = emptyList()
    private var height = 0
    private var awaitingSlot: Int? = null

    fun start(key: String) {
        if (held != null || waiting) return
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        original = source; preview = source; height = item.size; top = item.offset.toFloat()
        awaitingSlot = null; settling = null; held = key; onActive(true)
    }
    fun shift(delta: Float) {
        val key = held ?: return
        val info = list.layoutInfo
        top = (top + delta).coerceIn(info.viewportStartOffset.toFloat(), (info.viewportEndOffset - height).toFloat().coerceAtLeast(info.viewportStartOffset.toFloat()))
        val slot = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        if (awaitingSlot != null && awaitingSlot != slot.index) return
        awaitingSlot = null
        val center = top + height / 2f
        val target = info.visibleItemsInfo.firstOrNull { it.key != key && center > it.offset && center < it.offset + it.size } ?: return
        val before = preview ?: return
        val after = previewQueueMove(before, key, target.key as? String ?: return)
        if (after !== before) {
            // Preserve the viewport when the first visible item's identity moves.
            list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
            preview = after; awaitingSlot = after.indexOfFirst { it.key == key }
        }
    }
    fun scrollSpeed(): Float {
        val key = held ?: return 0f
        if (edge <= 0 || height + edge * 2 >= list.layoutInfo.viewportEndOffset - list.layoutInfo.viewportStartOffset) return 0f
        val rows = preview ?: return 0f
        val position = rows.indexOfFirst { it.key == key }
        val lane = rows.getOrNull(position)?.track?.lane ?: return 0f
        val intoTop = (list.layoutInfo.viewportStartOffset + edge - top).coerceAtLeast(0f)
        val intoBottom = (top + height - list.layoutInfo.viewportEndOffset + edge).coerceAtLeast(0f)
        return when {
            intoTop > 0 && list.canScrollBackward && rows.getOrNull(position - 1)?.track?.lane == lane -> -speed * (intoTop / edge).coerceAtMost(1f)
            intoBottom > 0 && list.canScrollForward && rows.getOrNull(position + 1)?.track?.lane == lane -> speed * (intoBottom / edge).coerceAtMost(1f)
            else -> 0f
        }
    }
    fun end() {
        val key = held ?: return
        val slot = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
        val targetSlot = awaitingSlot?.let { index -> list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } }
        releaseOffset = top - (targetSlot?.offset ?: slot?.offset ?: top.toInt()); settling = key; drop++
        val move = preview?.let { queueDropMove(original, it, key) }
        held = null; awaitingSlot = null
        try {
            if (move != null && source == original) { waiting = true; onMove(move.first, move.second) }
            else preview = null
        } finally { onActive(false) }
    }
    fun reconcile(entries: List<UpcomingTrack>, locked: Boolean) {
        if (locked || held != null && entries != original) cancel()
        if (waiting && (entries.map { it.key } == preview?.map { it.key } || entries.map { it.key }.toSet() != original.map { it.key }.toSet())) finishPending()
    }
    fun finishPending() { waiting = false; preview = null }
    fun cancel() {
        val wasHeld = held != null
        held = null; preview = null; waiting = false; awaitingSlot = null; settling = null
        if (wasHeld) onActive(false)
    }
}
