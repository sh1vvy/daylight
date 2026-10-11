/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.music.bitchord.data.model.*
import com.music.bitchord.ui.components.SearchPlayingBars
import com.music.bitchord.ui.components.longPressMenuClickable
import com.music.bitchord.ui.components.PlayingAccent
import com.music.bitchord.ui.components.PAGE_GUTTER
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

internal const val QUICK_PICK_LOOP_PAGES = 100_001
internal const val QUICK_PICK_COVER_FRACTION = .61f
internal fun quickPickLoopIndex(page: Int, count: Int): Int = if (count > 0) Math.floorMod(page, count) else 0
internal fun quickPickLoopPage(count: Int, index: Int): Int {
    if (count <= 1) return 0
    val middle = QUICK_PICK_LOOP_PAGES / 2
    return middle - middle % count + index.coerceIn(0, count - 1)
}

internal fun quickPickDepth(offset: Float): Float {
    val distance = abs(offset).coerceIn(0f, 1f)
    return distance * distance * (3f - 2f * distance)
}

internal fun quickPickScale(offset: Float): Float {
    val depth = quickPickDepth(offset)
    // A small passing dip leaves room for both sleeves inside the page gutters.
    return 1f - .32f * depth - .36f * depth * (1f - depth)
}

/** The two passing covers clear one another before their depth order changes. */
internal fun quickPickPosition(offset: Float): Float {
    val remaining = 1f - abs(offset).coerceIn(0f, 1f)
    return sign(offset) * .455f * (1f - remaining * remaining * remaining * remaining)
}

internal fun quickPickOpacity(offset: Float): Float =
    (1f - .14f * quickPickDepth(offset)) * (1f - (abs(offset) - 1f).coerceIn(0f, .5f) * 2f)

/** A bounded native pager. Only its drawing layers track the drag fraction. */
@Composable
internal fun HomeQuickPickCarousel(
    items: List<ShelfItem>,
    state: PagerState,
    onPlay: (ShelfItem) -> Unit,
    onMore: ((ShelfItem) -> Unit)?,
    song: Song?,
    playing: Boolean,
    reducedMotion: Boolean,
) {
    if (items.isEmpty()) return
    val scope = rememberCoroutineScope()
    val context = LocalPlatformContext.current
    val focused = items[quickPickLoopIndex(state.currentPage, items.size)]
    val coverShape = remember { RoundedCornerShape(16.dp) }
    val snap = remember { homeSettlingSpring() }
    val fling = rememberHomePagerFling(state)
    val shadowPixels = with(LocalDensity.current) { 12.dp.toPx() }
    val captionShift = with(LocalDensity.current) { 4.dp.toPx() }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = PAGE_GUTTER)
            .testTag("home-quick-picks-frame")) {
            val coverWidth = (maxWidth * QUICK_PICK_COVER_FRACTION).coerceAtMost(300.dp)
            val sideInset = ((maxWidth - coverWidth) / 2).coerceAtLeast(0.dp)
            val coverPixels = with(LocalDensity.current) { coverWidth.toPx() }
            HorizontalPager(state, pageSize = PageSize.Fixed(coverWidth),
                contentPadding = PaddingValues(horizontal = sideInset, vertical = 16.dp),
                beyondViewportPageCount = 1,
                flingBehavior = fling,
                modifier = Modifier.fillMaxWidth().semantics { collectionInfo = CollectionInfo(1, items.size) }
                    .testTag("home-quick-picks-pages"),
                key = { "quick-loop:$it" }) { page ->
                val trackIndex = quickPickLoopIndex(page, items.size)
                val item = items[trackIndex]
                val active = item.videoId == song?.videoId
                val request = remember(item.thumbnailUrl, context) {
                    ImageRequest.Builder(context).data(item.thumbnailUrl.artworkAt(720)).size(720).crossfade(false).build()
                }
                Box(Modifier.width(coverWidth).aspectRatio(1f)
                    // Depth changes only when the nearest page changes, not on every drag pixel.
                    .zIndex(if (state.currentPage == page) 1f else 0f)
                    .graphicsLayer {
                        val offset = (state.currentPage - page) + state.currentPageOffsetFraction
                        val depth = quickPickDepth(offset)
                        scaleX = quickPickScale(offset)
                        scaleY = scaleX
                        rotationY = if (reducedMotion) 0f else -sign(offset) * 24f * depth
                        rotationZ = if (reducedMotion) 0f else sign(offset) * 3f * depth
                        // One continuous path in global pager coordinates. Never
                        // clamp the pager offset: doing so makes buffered pages jump.
                        translationX = (offset - quickPickPosition(offset)) * coverPixels
                        alpha = quickPickOpacity(offset)
                        cameraDistance = coverPixels * 6f
                        shape = coverShape
                        shadowElevation = shadowPixels * (1f - depth)
                        ambientShadowColor = Color.Black.copy(alpha = .28f)
                        spotShadowColor = Color.Black.copy(alpha = .4f)
                    }
                    .testTag(when (page - state.currentPage) {
                        0 -> "quick-cover:${item.videoId}"
                        -1 -> "quick-neighbor:left"
                        1 -> "quick-neighbor:right"
                        else -> "quick-buffer:$page"
                    })
                    .semantics {
                        contentDescription = "${item.title}, ${item.subtitle}"
                        collectionItemInfo = CollectionItemInfo(0, 1, trackIndex, 1)
                    }
                    .longPressMenuClickable(onClick = {
                        if (state.currentPage != page) scope.launch {
                            if (reducedMotion) state.scrollToPage(page) else state.animateScrollToPage(page, animationSpec = snap)
                        }
                        onPlay(item)
                    }, onLongClick = onMore?.let { { it(item) } })
                    .clip(coverShape).background(MaterialTheme.colorScheme.surfaceContainer)) {
                    AsyncImage(request, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    if (active && playing) SearchPlayingBars(Modifier.align(Alignment.Center))
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 4.dp)
            .graphicsLayer {
                // Caption changes at the invisible midpoint, in step with the covers.
                val fade = if (reducedMotion) 0f else quickPickDepth(abs(state.currentPageOffsetFraction) * 2f)
                alpha = 1f - fade
                translationY = captionShift * fade
            },
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(focused.title, Modifier.fillMaxWidth().testTag("quick-pick-title"),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.35).sp),
                color = if (focused.videoId == song?.videoId) PlayingAccent else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(localizeCardSubtitle(focused.subtitle), Modifier.fillMaxWidth().testTag("quick-pick-artist"),
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
