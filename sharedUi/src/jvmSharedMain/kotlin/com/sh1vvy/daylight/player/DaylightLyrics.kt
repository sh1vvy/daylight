/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor

/** Independent lyric window: retained row layouts, real timing, and interruptible following. */
@Composable
internal fun DaylightLyrics(timeline: LyricTimeline, playhead: PlayerPlayhead, colors: PlayerColors, resumeLabel: String,
    onSeek: (Long) -> Unit, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val list = rememberLazyListState()
    val active by remember(timeline, playhead) { derivedStateOf { timeline.activeAt(playhead.millis) } }
    val dragged by list.interactionSource.collectIsDraggedAsState()
    var following by remember(timeline) { mutableStateOf(true) }
    LaunchedEffect(dragged) { if (dragged) following = false }
    LaunchedEffect(active, following) {
        if (following && active >= 0 && !dragged) {
            val inset = (list.layoutInfo.viewportEndOffset * .25f).toInt()
            if (reduceMotion) list.scrollToItem(active, -inset) else list.animateScrollToItem(active, -inset)
        }
    }
    Box(modifier) {
        LazyColumn(state = list, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.fillMaxSize().testTag("daylight-player-lyric-list")) {
            itemsIndexed(timeline.lines, key = { index, _ -> index }) { index, line ->
                if (line.text.isEmpty()) Spacer(Modifier.height(18.dp)) else LyricText(line, index, index == active, timeline, playhead, colors,
                    Modifier.fillMaxWidth().clickable(enabled = timeline.synced && line.startMs >= 0) { following = true; onSeek(line.seekMs) })
            }
        }
        if (!following && timeline.synced) TextButton({ following = true }, Modifier.align(Alignment.BottomCenter)
            .background(colors.container, RoundedCornerShape(50))) { Text(resumeLabel, color = colors.ink) }
    }
}

@Composable
private fun LyricText(line: PlayerLyric, index: Int, active: Boolean, timeline: LyricTimeline, playhead: PlayerPlayhead,
    colors: PlayerColors, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.headlineSmall.copy(fontSize = 27.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.45).sp)
    BoxWithConstraints(modifier) {
        val width = with(density) { maxWidth.roundToPx() }
        val layout = remember(line.text, style, width) { measurer.measure(line.text, style, constraints = Constraints(maxWidth = width)) }
        val boxes = remember(layout) { List(line.text.length) { layout.getBoundingBox(it) } }
        Canvas(Modifier.fillMaxWidth().height(with(density) { layout.size.height.toDp() }).semantics { text = AnnotatedString(line.text) }) {
            drawText(layout, color = if (!timeline.synced) colors.ink else colors.muted)
            if (active) {
                val revealed = timeline.revealedChars(index, playhead.millis).coerceIn(0f, boxes.size.toFloat())
                val whole = floor(revealed).toInt()
                val clip = Path()
                for (char in 0 until whole) clip.addRect(boxes[char])
                if (whole < boxes.size && revealed > whole) {
                    val box = boxes[whole]
                    // Keep the provider's character order; RTL glyph boxes fill from their right edge.
                    val rtl = Character.getDirectionality(line.text[whole]) in listOf(Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC)
                    val fraction = revealed - whole
                    clip.addRect(if (rtl) box.copy(left = box.right - box.width * fraction) else box.copy(right = box.left + box.width * fraction))
                }
                clipPath(clip) { drawText(layout, color = colors.ink) }
            }
        }
    }
}
