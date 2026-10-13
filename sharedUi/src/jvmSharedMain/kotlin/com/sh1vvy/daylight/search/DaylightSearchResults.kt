/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-12 through 2026-10-13.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.search

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.sh1vvy.daylight.motion.SwipeActionGate
import com.sh1vvy.daylight.motion.observeSwipeRelease
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged

enum class SearchHitKind { TRACK, ALBUM, ARTIST, PLAYLIST, QUERY }
@Immutable
data class SearchHit(val key: String, val title: String, val subtitle: String, val artwork: String?,
    val kind: SearchHitKind, val playing: Boolean = false)
data class SearchLabels(val title: String, val recent: String, val clear: String, val empty: String,
    val noResults: String, val retry: String, val more: String, val remove: String, val suggestions: String)

/** Independent Daylight presentation. The host supplies provider-neutral hits and actions. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DaylightSearchResults(
    query: String, editing: Boolean, suggestions: List<String>, previews: List<SearchHit>,
    results: List<SearchHit>?, history: List<SearchHit>, loading: Boolean, loadingMore: Boolean,
    error: String?, pageError: String?, labels: SearchLabels,
    filters: List<String>, selectedFilter: Int, onFilter: (Int) -> Unit,
    onHit: (SearchHit) -> Unit, onMenu: (SearchHit) -> Unit, onQueue: (SearchHit) -> Unit,
    onHistory: (SearchHit) -> Unit, onRemoveHistory: (SearchHit) -> Unit, onClearHistory: () -> Unit,
    onSuggestion: (String) -> Unit, onLoadMore: () -> Unit, onRetry: () -> Unit, onRetryPage: () -> Unit,
    listState: LazyListState, scrollEpoch: Int, padding: PaddingValues, modifier: Modifier = Modifier,
) {
    val currentLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(scrollEpoch) { if (scrollEpoch > 0) listState.scrollToItem(0) }
    LaunchedEffect(listState, results, loading, loadingMore, editing, pageError) {
        if (results == null || loading || editing || pageError != null) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            layout.totalItemsCount > 0 && (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) >= layout.totalItemsCount - 4
        }.distinctUntilChanged().collect { if (it && !loadingMore) currentLoadMore() }
    }
    LazyColumn(state = listState, modifier = modifier.fillMaxSize().testTag("daylight-search-results"),
        contentPadding = padding, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item("heading", "heading") {
            Text(labels.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
        }
        when {
            query.isBlank() -> {
                if (history.isEmpty()) item("empty", "message") {
                    Text(labels.empty, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp))
                } else {
                    item("recent-heading", "heading") {
                        Row(Modifier.padding(start = 24.dp, end = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(labels.recent, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = onClearHistory) { Text(labels.clear) }
                        }
                    }
                    items(history, key = { "history:${it.key}" }, contentType = { "history" }) { hit ->
                        HitRow(hit, { onHistory(hit) }, { onRemoveHistory(hit) }, labels.remove, remove = true)
                    }
                }
            }
            editing -> {
                items(suggestions.take(4), key = { "suggest:$it" }, contentType = { "suggestion" }) { term ->
                    Row(Modifier.fillMaxWidth().clickable { onSuggestion(term) }.padding(horizontal = 24.dp).heightIn(min = 52.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Text(term, Modifier.weight(1f).padding(horizontal = 16.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyLarge)
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
                if (previews.isNotEmpty()) item("preview-heading", "heading") {
                    Text(labels.suggestions, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                }
                items(previews, key = { "preview:${it.key}" }, contentType = { it.kind }) { hit ->
                    HitRow(hit, { onHit(hit) }, { onMenu(hit) }, labels.more, onQueue = if (hit.kind == SearchHitKind.TRACK) ({ onQueue(hit) }) else null)
                }
            }
            else -> {
                if (filters.isNotEmpty()) item("filters", "filters") {
                    LazyRow(modifier = Modifier.selectableGroup(), contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        itemsIndexed(filters, key = { index, _ -> index }) { index, label ->
                            SearchFilterTab(label, index == selectedFilter) { onFilter(index) }
                        }
                    }
                }
                if (loading && results.isNullOrEmpty()) items(6, key = { "loading:$it" }, contentType = { "loading" }) {
                    Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh))
                        Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.fillMaxWidth(.65f).height(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh))
                            Box(Modifier.fillMaxWidth(.4f).height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer))
                        }
                    }
                }
                items(results.orEmpty(), key = { "result:${it.key}" }, contentType = { it.kind }) { hit ->
                    HitRow(hit, { onHit(hit) }, { onMenu(hit) }, labels.more, onQueue = if (hit.kind == SearchHitKind.TRACK) ({ onQueue(hit) }) else null)
                }
                if (!loading && results?.isEmpty() == true && error == null) item("no-results", "message") {
                    Text(labels.noResults, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp))
                }
                if (error != null || pageError != null) item("error", "message") {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                        Text(error ?: pageError.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = if (pageError != null) onRetryPage else onRetry) { Text(labels.retry) }
                    }
                }
                if (loadingMore || loading && !results.isNullOrEmpty()) item("paging", "loading") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HitRow(hit: SearchHit, onClick: () -> Unit, onMore: () -> Unit, moreLabel: String, remove: Boolean = false, onQueue: (() -> Unit)? = null) {
    if (onQueue != null) {
        val queue by rememberUpdatedState(onQueue)
        val commitGate = remember(hit.key) { SwipeActionGate() }
        val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { direction ->
            if (direction == SwipeToDismissBoxValue.StartToEnd) commitGate.commit { queue() }
            false
        })
        SwipeToDismissBox(state = swipe, modifier = Modifier.observeSwipeRelease(commitGate), enableDismissFromStartToEnd = true, enableDismissFromEndToStart = false,
            backgroundContent = {
                Box(Modifier.fillMaxSize().drawWithContent {
                    val exposed = runCatching { swipe.requireOffset() }.getOrDefault(0f).let { kotlin.math.abs(it) }.coerceAtMost(size.width)
                    if (layoutDirection == LayoutDirection.Rtl) clipRect(left = size.width - exposed) { this@drawWithContent.drawContent() }
                    else clipRect(right = exposed) { this@drawWithContent.drawContent() }
                }.padding(horizontal = 24.dp, vertical = 8.dp).clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.CenterStart) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null, tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(start = 16.dp).size(24.dp))
                }
            }) { HitRow(hit, onClick, onMore, moreLabel, remove) }
        return
    }
    val scheme = MaterialTheme.colorScheme
    val shape = if (hit.kind == SearchHitKind.ARTIST) CircleShape else RoundedCornerShape(12.dp)
    Column(Modifier.padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (hit.playing) scheme.secondaryContainer.copy(alpha = .45f) else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = if (!remove && hit.kind != SearchHitKind.QUERY) onMore else null)
            .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(shape).background(scheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                if (hit.artwork != null) AsyncImage(hit.artwork, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(if (hit.kind == SearchHitKind.QUERY) Icons.Rounded.History else Icons.Rounded.MusicNote, null,
                    tint = scheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f).padding(start = 14.dp, end = 8.dp)) {
                Text(hit.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (hit.playing) scheme.primary else scheme.onBackground)
                if (hit.subtitle.isNotBlank()) Text(hit.subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (remove || hit.kind != SearchHitKind.QUERY) IconButton(onClick = onMore, modifier = Modifier.size(48.dp)) {
                Icon(if (remove) Icons.Rounded.Close else Icons.Rounded.MoreHoriz, moreLabel,
                    tint = scheme.onSurfaceVariant, modifier = Modifier.size(if (remove) 18.dp else 22.dp))
            }
        }
        HorizontalDivider(Modifier.padding(start = 70.dp), color = scheme.outlineVariant.copy(alpha = .18f))
    }
}

@Composable
private fun SearchFilterTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val ink by animateColorAsState(if (selected) scheme.primary else scheme.onSurfaceVariant, label = "search-filter-ink")
    val underline = animateFloatAsState(if (selected) 1f else 0f, spring(.9f, 500f), label = "search-filter-line")
    Box(Modifier.height(48.dp).widthIn(min = 48.dp).clip(RoundedCornerShape(8.dp))
        .selectable(selected, role = Role.Tab, onClick = onClick).padding(horizontal = 10.dp)
        .drawWithContent {
            drawContent()
            val fraction = underline.value.coerceIn(0f, 1f)
            val lineWidth = size.width * fraction
            if (lineWidth > 0f) drawRoundRect(scheme.primary.copy(alpha = fraction),
                topLeft = androidx.compose.ui.geometry.Offset((size.width - lineWidth) / 2f, size.height - 4.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(lineWidth, 2.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
        }, contentAlignment = Alignment.Center) {
        Text(label, color = ink, style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }
}
