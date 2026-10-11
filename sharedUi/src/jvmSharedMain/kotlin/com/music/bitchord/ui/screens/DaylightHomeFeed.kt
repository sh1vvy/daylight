/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.music.bitchord.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.LocalPlatformContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.music.bitchord.data.model.*
import com.music.bitchord.sharedui.resources.*
import com.music.bitchord.ui.components.*
import com.music.bitchord.ui.player.PlayerPlatform
import com.music.bitchord.ui.theme.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.flow.filterNotNull

private val LocalHomeReducedMotion = staticCompositionLocalOf { false }

/** Retained by the tab host so returning from a collection preserves its reading order. */
@Stable
class HomeFeedPresentation {
    internal var shelves by mutableStateOf<List<HomeShelf>>(emptyList())
    internal var quickTrackId by mutableStateOf<String?>(null)
}

@Composable
fun rememberHomeFeedPresentation(vararg identity: Any?): HomeFeedPresentation =
    remember(*identity) { HomeFeedPresentation() }

/** Home has a finite, prepared reading order. It never asks a provider to load while scrolling. */
@Composable
fun HomeScreen(
    state: UiState<List<HomeShelf>>,
    listState: LazyListState,
    onItemClick: (ShelfItem, String) -> Unit,
    onRetry: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    pullState: PullToRefreshState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    title: String?,
    signedIn: Boolean = false,
    onItemLongPress: ((ShelfItem) -> Unit)? = null,
    brandContent: (@Composable () -> Unit)? = null,
    onPlayMix: (() -> Unit)? = null,
    mixLoading: Boolean = false,
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    presentation: HomeFeedPresentation = rememberHomeFeedPresentation(),
) {
    val reducedMotion by PlayerPlatform.host.settings.reduceAnimation.collectAsStateWithLifecycle()
    val visibleState = rememberCoverSourceValue("home-feed", state)
    val source = (visibleState as? UiState.Success)?.data.orEmpty()
    // Recommendations are from this same snapshot; no late radio request reshuffles a page.
    val incoming = remember(source) { prepareDaylightHome(source) }
    var retained by presentation::shelves
    val shelves = retained.ifEmpty { incoming }
    val quickItems = shelves.firstOrNull { it.title == QUICK_PICKS_TITLE }?.items.orEmpty()
    val quickIds = remember(quickItems) { quickItems.map { it.videoId } }
    val quickState = if (quickItems.isNotEmpty()) rememberPagerState(
        initialPage = quickPickLoopPage(quickItems.size, quickIds.indexOf(presentation.quickTrackId).coerceAtLeast(0)),
        pageCount = { if (quickItems.size == 1) 1 else QUICK_PICK_LOOP_PAGES }) else null
    LaunchedEffect(quickState, quickIds) {
        val pager = quickState ?: return@LaunchedEffect
        val focusedIndex = quickIds.indexOf(presentation.quickTrackId).coerceAtLeast(0)
        if (quickPickLoopIndex(pager.currentPage, quickIds.size) != focusedIndex) {
            pager.scrollToPage(quickPickLoopPage(quickIds.size, focusedIndex))
        }
        snapshotFlow { if (pager.isScrollInProgress) null else pager.settledPage }
            .filterNotNull().collect { page ->
                val index = quickPickLoopIndex(page, quickIds.size)
                presentation.quickTrackId = quickIds[index]
                // A modest virtual window keeps accessibility offsets precise.
                // Recenter only at rest, preserving this cover and both neighbours.
                if (quickIds.size > 1 && (page < 24 || page > QUICK_PICK_LOOP_PAGES - 25)) {
                    pager.scrollToPage(quickPickLoopPage(quickIds.size, index))
                }
            }
    }
    val atTop by remember(listState, quickState) { derivedStateOf {
        !listState.isScrollInProgress && quickState?.isScrollInProgress != true &&
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
    } }
    LaunchedEffect(visibleState, incoming, atTop) {
        retained = when {
            visibleState is UiState.Loading -> emptyList()
            else -> settledHomeShelves(retained, incoming, atTop)
        }
    }
    val keys = remember(shelves) { homeShelfKeys(shelves) }
    // Preserve deferred padding reads: the Android host measures the floating
    // controls at layout, including their folded height and system navigation.
    val feedPadding = remember(contentPadding, title) {
        object : PaddingValues {
            override fun calculateLeftPadding(layoutDirection: LayoutDirection) =
                contentPadding.calculateLeftPadding(layoutDirection)
            override fun calculateRightPadding(layoutDirection: LayoutDirection) =
                contentPadding.calculateRightPadding(layoutDirection)
            override fun calculateTopPadding() =
                (contentPadding.calculateTopPadding() - if (title != null) 28.dp else 0.dp).coerceAtLeast(0.dp)
            override fun calculateBottomPadding() = contentPadding.calculateBottomPadding()
        }
    }
    CompositionLocalProvider(LocalHomeReducedMotion provides reducedMotion) {
        MaterialTheme(colorScheme = daylightHomeColorScheme(MaterialTheme.colorScheme, LocalPinkCloud.current)) {
            PullToRefresh(refreshing, onRefresh, pullState, modifier.fillMaxSize()) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("home-feed"),
                    contentPadding = feedPadding) {
                    if (title != null) item("home-title", contentType = "brand") { HomeBrand(brandContent) }
                    when (visibleState) {
                        is UiState.Loading -> homePreparedSkeleton()
                        is UiState.Error -> item("home-error", contentType = "message") {
                            MessageState(visibleState.message, actionLabel = stringResource(Res.string.retry), onAction = onRetry)
                        }
                        is UiState.Success -> {
                            shelves.forEachIndexed { index, shelf ->
                                item(keys[index], contentType = if (shelf.items.all { it.videoId != null }) "tracks" else "artwork") {
                                    HomeShelfHeading(shelf.title)
                                    if (shelf.title == QUICK_PICKS_TITLE && quickState != null) {
                                        HomeQuickPickCarousel(shelf.items, quickState, { onItemClick(it, shelf.title) },
                                            onItemLongPress, currentSong, isPlaying, reducedMotion)
                                    } else if (shelf.items.all { it.videoId != null }) {
                                        HomeTrackPages(shelf, null,
                                            { onItemClick(it, shelf.title) }, onItemLongPress, currentSong, isPlaying)
                                    } else {
                                        HomeArtworkShelf(shelf, keys[index], { onItemClick(it, shelf.title) }, onItemLongPress, currentSong, isPlaying)
                                    }
                                    val mixFollows = shelf.title == QUICK_PICKS_TITLE && signedIn && onPlayMix != null
                                    if (index < shelves.lastIndex || mixFollows) Spacer(Modifier.height(30.dp))
                                }
                                if (shelf.title == QUICK_PICKS_TITLE && signedIn && onPlayMix != null) {
                                    item("daylight-mix", contentType = "action") { HomeMixAction(onPlayMix, mixLoading) }
                                }
                            }
                            if (shelves.none { it.title == QUICK_PICKS_TITLE } && signedIn && onPlayMix != null) {
                                item("daylight-mix", contentType = "action") { HomeMixAction(onPlayMix, mixLoading) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun HomeBrand(content: (@Composable () -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = PAGE_GUTTER, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Icon(painterResource(Res.drawable.ic_logo), null, Modifier.width(38.dp).aspectRatio(100f / 72f).testTag("home-butterfly"),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(24.dp))
        if (content != null) content() else Text("daylight", style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable private fun HomeShelfHeading(title: String) {
    Text(localizeShelfTitle(title), Modifier.padding(horizontal = PAGE_GUTTER).padding(bottom = 14.dp),
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable private fun HomeMixAction(onClick: () -> Unit, loading: Boolean) {
    FilledTonalButton(onClick, enabled = !loading, shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        modifier = Modifier.padding(horizontal = PAGE_GUTTER).padding(bottom = 30.dp).fillMaxWidth().heightIn(min = 48.dp)
            .testTag("home-play-my-mix")) {
        if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else Icon(Icons.Rounded.PlayArrow, null, Modifier.size(23.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.home_play_my_mix), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable private fun HomeTrackPages(shelf: HomeShelf, providedState: LazyListState?,
    onClick: (ShelfItem) -> Unit, onLongPress: ((ShelfItem) -> Unit)?, currentSong: Song?, isPlaying: Boolean) {
    val columns = remember(shelf.items) { shelf.items.chunked(3) }
    val rowHeight = maxOf(76f, 44f * LocalDensity.current.fontScale + 20f).dp
    val rowState = providedState ?: rememberLazyListState(
        cacheWindow = remember { LazyLayoutCacheWindow(aheadFraction = 0.5f, behindFraction = 0.5f) })
    BoxWithConstraints {
        val width = (maxWidth - PAGE_GUTTER * 2 - 18.dp).coerceAtLeast(200.dp)
        ShelfRow(PaddingValues(start = PAGE_GUTTER, end = PAGE_GUTTER + 18.dp), Arrangement.spacedBy(12.dp),
            modifier = Modifier.testTag(if (shelf.title == QUICK_PICKS_TITLE) "home-quick-picks-pages" else "home-track-pages"),
            state = rowState, flingBehavior = rememberHomeRowFling(rowState)) {
            itemsIndexed(columns, key = { index, _ -> "${shelf.title}:column:$index" }, contentType = { _, _ -> "three-tracks" }) { _, column ->
                Column(Modifier.width(width).clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.42f))) {
                    column.forEachIndexed { index, item ->
                        HomeTrackRow(item, rowHeight, { onClick(item) }, onLongPress?.let { { it(item) } }, currentSong, isPlaying)
                        if (index < column.lastIndex) HorizontalDivider(Modifier.padding(start = 76.dp, end = 12.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f))
                    }
                    if (column.size < 3) Spacer(Modifier.height(rowHeight * (3 - column.size)))
                }
            }
        }
    }
}

@Composable private fun HomeTrackRow(item: ShelfItem, rowHeight: androidx.compose.ui.unit.Dp, onClick: () -> Unit, onLongPress: (() -> Unit)?, song: Song?, playing: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val current = item.videoId == song?.videoId && item.videoId != null
    Row(Modifier.fillMaxWidth().heightIn(min = rowHeight).homePress(interaction)
        .longPressMenuClickable(onClick, onLongPress, interactionSource = interaction).padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            HomeImage(item, ROW_ART_PX, Modifier.fillMaxSize())
            if (current && playing) SearchPlayingBars(Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp), maxLines = 1,
                overflow = TextOverflow.Ellipsis, color = if (current) PlayingAccent else MaterialTheme.colorScheme.onSurface)
            Text(localizeCardSubtitle(item.subtitle), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onLongPress != null) IconButton(onLongPress, Modifier.size(48.dp)) {
            Icon(Icons.Rounded.MoreHoriz, stringResource(Res.string.more), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun HomeArtworkShelf(shelf: HomeShelf, key: String, onClick: (ShelfItem) -> Unit,
    onLongPress: ((ShelfItem) -> Unit)?, song: Song?, playing: Boolean) {
    val keys = remember(shelf.items) { shelfItemKeys(shelf.items) }
    val rowState = rememberLazyListState(cacheWindow = remember { LazyLayoutCacheWindow(aheadFraction = 0.5f, behindFraction = 0.5f) })
    BoxWithConstraints {
        val width = ((maxWidth - PAGE_GUTTER * 2 - 14.dp) / 2.12f).coerceIn(140.dp, 210.dp)
        ShelfRow(PaddingValues(horizontal = PAGE_GUTTER), Arrangement.spacedBy(14.dp), state = rowState) {
            itemsIndexed(shelf.items, key = { index, _ -> keys[index] }, contentType = { _, item -> if (item.browseId?.startsWith("UC") == true) "artist" else "collection" }) { index, item ->
                HomeArtworkCard(item, "$key:cover:${keys[index]}", width, { onClick(item) },
                    onLongPress?.let { { it(item) } }, song, playing)
            }
        }
    }
}

@Composable private fun HomeArtworkCard(item: ShelfItem, key: String, width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit, onLongPress: (() -> Unit)?, song: Song?, playing: Boolean) {
    val artist = item.browseId?.startsWith("UC") == true
    val shape = if (artist) CircleShape else RoundedCornerShape(8.dp)
    val motion = LocalLibraryCoverMotion.current
    val motionKey = key.takeIf { !artist && item.videoId == null && !item.browseId.isNullOrBlank() && !item.thumbnailUrl.isNullOrBlank() }
    val interaction = remember { MutableInteractionSource() }
    Column(Modifier.width(width).homePress(interaction).longPressMenuClickable(onClick = {
        if (motionKey != null) item.browseId?.let { motion?.onSelectCard?.invoke(it, motionKey, 8f) }
        onClick()
    }, onLongClick = onLongPress, interactionSource = interaction)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).libraryCoverMotion(item.browseId, motionKey, 8f)
            .then(if (motion?.source != true || motionKey == null) Modifier.clip(shape) else Modifier)
            .background(MaterialTheme.colorScheme.surfaceVariant)) {
            HomeImage(item, CARD_ART_PX, Modifier.fillMaxSize())
            if (item.videoId != null && item.videoId == song?.videoId && playing) SearchPlayingBars(Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (item.subtitle.isNotBlank()) Text(localizeCardSubtitle(item.subtitle), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun HomeImage(item: ShelfItem, pixels: Int, modifier: Modifier) {
    val context = LocalPlatformContext.current
    val request = remember(item.thumbnailUrl, pixels, context) {
        ImageRequest.Builder(context).data(item.thumbnailUrl.artworkAt(pixels)).size(pixels).crossfade(false).build()
    }
    AsyncImage(request, null, modifier, contentScale = ContentScale.Crop)
}

/** Animate a graphics layer on touch only; scroll offsets never recompose artwork. */
@Composable private fun Modifier.homePress(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val reduced = LocalHomeReducedMotion.current
    val scale by animateFloatAsState(if (pressed && !reduced) 0.97f else 1f,
        spring(dampingRatio = 0.8f, stiffness = 650f), label = "home-touch")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

private fun LazyListScope.homePreparedSkeleton() {
    item("home-skeleton", contentType = "placeholder") {
        Column(Modifier.padding(horizontal = PAGE_GUTTER)) {
            Spacer(Modifier.height(26.dp).width(130.dp).clip(RoundedCornerShape(8.dp)).background(androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.1f)))
            Spacer(Modifier.height(14.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                Spacer(Modifier.width((maxWidth * QUICK_PICK_COVER_FRACTION).coerceAtMost(300.dp))
                    .aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                    .background(androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.1f)))
            }
            Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(24.dp).width(160.dp).clip(RoundedCornerShape(6.dp)).background(androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.1f)))
                Spacer(Modifier.height(6.dp))
                Spacer(Modifier.height(18.dp).width(100.dp).clip(RoundedCornerShape(6.dp)).background(androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.1f)))
            }
        }
    }
}
