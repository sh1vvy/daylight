/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-08 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.ui.screens

import com.music.bitchord.sharedui.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import com.music.bitchord.data.settings.LibraryViewType
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.ui.components.LocalLibraryCoverMotion
import com.music.bitchord.ui.components.libraryCoverMotion
import com.music.bitchord.ui.components.longPressMenuClickable
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.foundation.Image
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import com.music.bitchord.ui.components.ShelfRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.playlistCreationOrderKey
import com.music.bitchord.data.settings.LibrarySort
import com.music.bitchord.ui.AppUi
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.components.MessageState
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.LIBRARY_GRID_SPACING
import com.music.bitchord.ui.components.PullToRefresh
import com.music.bitchord.ui.components.SHELF_CARD_WIDTH
import com.music.bitchord.ui.components.libraryGrid
import com.music.bitchord.ui.components.shelfItemKeys
import com.music.bitchord.ui.components.stableItemKeys
import com.music.bitchord.ui.components.rememberCoverSourceValue

/** The collection hub shares one lazy viewport with its Recently Added artwork. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    signedIn: Boolean,
    state: UiState<LibraryPage>,
    listState: LazyListState,
    onShelfItemClick: (ShelfItem) -> Unit,
    onShelfItemLongPress: (ShelfItem) -> Unit,
    onNewPlaylist: () -> Unit,
    onImportSpotifyPlaylist: (() -> Unit)? = null,
    onShowAll: (HomeShelf) -> Unit,
    replay: @Composable () -> Unit,
    onSignIn: () -> Unit,
    onRetry: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    pullState: PullToRefreshState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    links: List<LibraryLink>,
    deviceItems: List<ShelfItem>,
    showTitle: Boolean = true,
    createdPlaylistIds: List<String> = emptyList(),
    personalPlaylists: List<ShelfItem> = emptyList(),
) {
    val livePins by AppUi.host.pinnedPlaylists.collectAsStateWithLifecycle()
    val pins = rememberCoverSourceValue("library-pins", livePins)
    val visible = rememberCoverSourceValue("library-feed", state)
    val personal = rememberCoverSourceValue("library-personal", personalPlaylists)
    val activity = rememberCoverSourceValue("library-created", createdPlaylistIds)
    val device = rememberCoverSourceValue("library-device", deviceItems)
    val collections = remember(visible, personal, pins, activity) {
        libraryCollections((visible as? UiState.Success)?.data, personal, pins, activity)
    }
    val recentRows = remember(collections.recent) { collections.recent.chunked(2) }
    val recentKeys = remember(recentRows) { stableItemKeys(recentRows) { pair -> pair.joinToString("|") { it.browseId ?: it.title } } }
    val categories = listOf(
        LibraryLink(ShelfItem(stringResource(Res.string.playlists), "", null, null, "library:playlists"), Icons.Rounded.QueueMusic),
        LibraryLink(ShelfItem(stringResource(Res.string.artists), "", null, null, "library:artists"), Icons.Rounded.Mic),
        LibraryLink(ShelfItem(stringResource(Res.string.albums), "", null, null, "library:albums"), Icons.Rounded.LibraryMusic),
    ) + links.filter { it.item.browseId == YtMusicRepository.LIKED_MUSIC || it.item.browseId == "local:downloads" }
    val utilities = links.filterNot { it.item.browseId == YtMusicRepository.LIKED_MUSIC || it.item.browseId == "local:downloads" }
    fun openCategory(item: ShelfItem) {
        when (item.browseId) {
            "library:playlists" -> onShowAll(collections.playlists)
            "library:artists" -> onShowAll(collections.artists)
            "library:albums" -> onShowAll(collections.albums)
            else -> onShelfItemClick(item)
        }
    }
    PullToRefresh(refreshing, onRefresh, pullState, modifier) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("library-overview"),
            contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(0.dp)) {
            if (showTitle) item(key = "library-heading", contentType = "heading") {
                Row(Modifier.fillMaxWidth().padding(horizontal = PAGE_GUTTER + 4.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.library), style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    replay()
                }
            }
            item(key = "library-categories", contentType = "categories") {
                LibraryCategoryRows(categories, ::openCategory)
            }
            if (visible is UiState.Error) item(key = "library-error", contentType = "message") {
                MessageState(visible.message, actionLabel = stringResource(Res.string.retry), onAction = onRetry)
            }
            if (recentRows.isNotEmpty() || signedIn && visible is UiState.Loading) {
                item(key = "recent-heading", contentType = "heading") {
                    Text(stringResource(Res.string.library_recently_added), style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = PAGE_GUTTER + 4.dp, end = PAGE_GUTTER + 4.dp, top = 26.dp, bottom = 18.dp))
                }
                if (recentRows.isEmpty()) items(2, key = { "library-placeholder-$it" }, contentType = { "placeholder" }) {
                    Row(Modifier.padding(horizontal = PAGE_GUTTER + 4.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        repeat(2) { Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)) }
                    }
                }
                itemsIndexed(recentRows, key = { index, _ -> "recent:${recentKeys[index]}" },
                    contentType = { _, _ -> "cover-pair" }) { _, pair ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = PAGE_GUTTER + 4.dp).padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        pair.forEach { item ->
                            ShelfCard(item = item, libraryCardKey = "recent:${item.browseId}",
                                onClick = { onShelfItemClick(item) }, onLongPress = { onShelfItemLongPress(item) },
                                isPinned = item.browseId in pins, modifier = Modifier.weight(1f))
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else item(key = "library-empty", contentType = "message") {
                Text(stringResource(Res.string.library_collections_empty), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = PAGE_GUTTER + 4.dp, vertical = 24.dp))
            }
            if (!signedIn) item(key = "library-sign-in", contentType = "message") {
                MessageState(stringResource(Res.string.library_sign_in_description),
                    actionLabel = stringResource(Res.string.sign_in), onAction = onSignIn)
            }
            if (device.isNotEmpty()) item(key = "library-device", contentType = "device") {
                LibraryGridShelf(HomeShelf(stringResource(Res.string.on_device), device), onShelfItemClick,
                    onShelfItemLongPress, onShowAll = { onShowAll(HomeShelf("On device", device)) })
            }
            item(key = "library-tools", contentType = "categories") {
                LibraryCategoryRows(utilities, onShelfItemClick)
            }
        }
    }
}

/** One of the Library's folder rows: the page [item] opens, behind [icon]. */
/** [logo], when set, is drawn in place of [icon] — a service's own mark. */
data class LibraryLink(val item: ShelfItem, val icon: ImageVector, val logo: DrawableResource? = null)

/** Soft inset separators and a single hierarchy work with all three themes. */
@Composable
private fun LibraryCategoryRows(links: List<LibraryLink>, onClick: (ShelfItem) -> Unit) {
    Column(Modifier.padding(horizontal = PAGE_GUTTER + 4.dp)) {
        links.forEach { link ->
            Column(Modifier.fillMaxWidth().testTag("library-category:${link.item.browseId}").clickable { onClick(link.item) }) {
                Row(Modifier.fillMaxWidth().heightIn(min = 62.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (link.logo != null) Icon(painterResource(link.logo), null,
                        tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(27.dp))
                    else Icon(link.icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp))
                    Text(link.item.title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal),
                        color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(BitChordIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.size(20.dp))
                }
                HorizontalDivider(Modifier.padding(start = 46.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            }
        }
    }
}

/** A Library shelf's preview row never swipes past this many cards. */
private const val LIBRARY_ROW_MAX_ITEMS = 5

/**
 * A Library shelf: a sideways-scrolling row of [SHELF_CARD_WIDTH] cards, the
 * same as every other shelf, but stopped at [LIBRARY_ROW_MAX_ITEMS] rather
 * than left to run the shelf's whole length — with a "Show all" beside the
 * title whenever there's more than that, opening the rest as a
 * vertically-scrolling grid instead. See [LibraryGridPage].
 *
 * [leadingCard], if given, occupies the first slot and counts against that
 * cap.
 */
@Composable
internal fun LibraryGridShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: (ShelfItem) -> Unit,
    onShowAll: () -> Unit,
    leadingCard: (@Composable () -> Unit)? = null,
    pinnedPlaylists: List<String> = emptyList(),
    newestCreatedPlaylistId: String? = null,
) {
    val leadingCount = if (leadingCard != null) 1 else 0
    val visibleItems = remember(shelf.items, leadingCount) {
        shelf.items.take((LIBRARY_ROW_MAX_ITEMS - leadingCount).coerceAtLeast(0))
    }
    val itemKeys = remember(visibleItems) { shelfItemKeys(visibleItems) }
    val rowState = rememberLazyListState()
    // LazyRow retains the old first visible key after a prepend. Reveal an
    // real recent activity, retaining scroll on normal refreshes/renames.
    // A playlist can become newest repeatedly after other playlists are edited.
    var observedNewest by remember { mutableStateOf(newestCreatedPlaylistId) }
    LaunchedEffect(newestCreatedPlaylistId) {
        if (observedNewest == newestCreatedPlaylistId) return@LaunchedEffect
        observedNewest = newestCreatedPlaylistId
        val createdId = newestCreatedPlaylistId ?: return@LaunchedEffect
        val index = visibleItems.indexOfFirst { item ->
            item.browseId?.let(::playlistCreationOrderKey) == createdId
        }
        if (index >= 0) rowState.scrollToItem(index + leadingCount)
    }
    Column(Modifier.padding(bottom = 26.dp)) {
        SectionHeader(
            title = shelf.title,
            subtitle = shelf.subtitle,
            onShowAll = onShowAll.takeIf { shelf.items.size + leadingCount > LIBRARY_ROW_MAX_ITEMS },
        )
        ShelfRow(
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(LIBRARY_GRID_SPACING),
            state = rowState,
        ) {
            leadingCard?.let { card -> item(key = "leading", contentType = "leading-card") { card() } }
            itemsIndexed(
                visibleItems,
                key = { index, _ -> itemKeys[index] },
                contentType = { _, _ -> "shelf-card" },
            ) { index, item ->
                ShelfCard(
                    item = item,
                    libraryCardKey = "shelf:${shelf.title}:${itemKeys[index]}",
                    onClick = { onItemClick(item) },
                    onLongPress = { onItemLongPress(item) },
                    isPinned = item.browseId != null && item.browseId in pinnedPlaylists,
                )
            }
        }
    }
}

/** Full collection browser, with retained grid/list viewports and local search. */
@Composable
fun LibraryGridPage(
    shelf: HomeShelf,
    gridState: LazyGridState,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: (ShelfItem) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onNewPlaylist: (() -> Unit)? = null,
    createdPlaylistIds: List<String> = emptyList(),
    listState: LazyListState = rememberLazyListState(),
    searchQuery: MutableState<String> = rememberSaveable(shelf.title) { mutableStateOf("") },
    resolveArtistArtwork: (suspend (ShelfItem) -> String?)? = null,
) {
    val pins by AppUi.host.pinnedPlaylists.collectAsStateWithLifecycle()
    val sort by AppUi.host.librarySort.collectAsStateWithLifecycle()
    val views by AppUi.host.libraryCollectionViews.collectAsStateWithLifecycle()
    val artists = shelf.title == "Artists"
    val view = if (artists) LibraryViewType.LIST else views[shelf.title] ?: LibraryViewType.GRID
    var query by searchQuery
    val visibleShelf = rememberCoverSourceValue("library-browser:${shelf.title}", shelf)
    val ordered = remember(visibleShelf, pins, sort, createdPlaylistIds) {
        (if (visibleShelf.isPlaylistLibraryShelf()) visibleShelf.withoutLikedMusic() else visibleShelf)
            .orderedForLibrary(pins, if (artists && sort == LibrarySort.DEFAULT) LibrarySort.TITLE_ASC else sort, createdPlaylistIds)
    }
    val filtered = remember(ordered.items, query) {
        val text = query.trim()
        if (text.isEmpty()) ordered.items else ordered.items.filter {
            it.title.contains(text, ignoreCase = true) || it.subtitle.contains(text, ignoreCase = true)
        }
    }
    val keys = remember(filtered) { shelfItemKeys(filtered) }
    val groups = remember(filtered, artists, sort) {
        if (artists && sort != LibrarySort.TITLE_DESC) filtered.groupBy { libraryInitial(it.title) }.entries
            .sortedBy { if (it.key == "#") "~" else it.key } else emptyList()
    }
    val groupPositions = remember(groups) {
        var index = 1 // Collection heading occupies item zero.
        groups.associate { (letter, items) -> (letter to index).also { index += items.size + 1 } }
    }
    val scope = rememberCoroutineScope()
    val newest = ordered.newestCreatedPlaylistId(createdPlaylistIds)
    var observed by remember(shelf.title) { mutableStateOf(newest) }
    LaunchedEffect(newest) {
        if (observed != newest && newest != null) { gridState.scrollToItem(0); listState.scrollToItem(0) }
        observed = newest
    }
    @Composable fun Heading() {
        Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(collectionTitle(shelf.title), style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                onNewPlaylist?.let { create -> IconButton(create) { Icon(BitChordIcons.Plus, stringResource(Res.string.new_playlist),
                    tint = MaterialTheme.colorScheme.onSurface) } }
            }
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(query, { query = it }, singleLine = true,
                placeholder = { Text(stringResource(Res.string.library_search_collection, collectionTitle(shelf.title))) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, modifier = Modifier.size(21.dp)) },
                trailingIcon = if (query.isNotEmpty()) { { IconButton({ query = "" }) {
                    Icon(Icons.Rounded.Close, stringResource(Res.string.library_clear_search)) } } } else null,
                shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().testTag("library-collection-search"),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent, focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer))
            Spacer(Modifier.height(14.dp))
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val horizontal = PAGE_GUTTER + 4.dp
        if (view == LibraryViewType.GRID) {
            val grid = libraryGrid(maxWidth - horizontal * 2)
            LazyVerticalGrid(GridCells.Fixed(grid.columns), state = gridState, contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = horizontal).testTag("library-collection-grid")) {
                item(key = "heading", span = { GridItemSpan(maxLineSpan) }, contentType = "heading") { Heading() }
                if (filtered.isEmpty()) item(key = "empty", span = { GridItemSpan(maxLineSpan) }, contentType = "message") {
                    Text(stringResource(Res.string.library_collection_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                itemsIndexed(filtered, key = { index, _ -> keys[index] }, contentType = { _, _ -> "collection" }) { index, item ->
                    ShelfCard(item, onClick = { onItemClick(item) }, onLongPress = { onItemLongPress(item) },
                        modifier = Modifier.fillMaxWidth(), isPinned = item.browseId in pins, libraryCardKey = "grid:${keys[index]}")
                }
            }
        } else {
            LazyColumn(state = listState, contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize().padding(start = horizontal, end = if (groups.isEmpty()) horizontal else 36.dp)
                    .testTag("library-collection-list")) {
                item(key = "heading", contentType = "heading") { Heading() }
                if (filtered.isEmpty()) item(key = "empty", contentType = "message") {
                    Text(stringResource(Res.string.library_collection_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (groups.isEmpty()) itemsIndexed(filtered, key = { index, _ -> keys[index] }, contentType = { _, _ -> "collection-row" }) { index, item ->
                    LibraryCollectionRow(item, artists, "list:${keys[index]}", { onItemClick(item) }, { onItemLongPress(item) }, resolveArtistArtwork)
                } else groups.forEach { (letter, entries) ->
                    val entryKeys = shelfItemKeys(entries)
                    item(key = "initial:$letter", contentType = "initial") {
                        Text(letter, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                    }
                    itemsIndexed(entries, key = { index, _ -> "artist:$letter:${entryKeys[index]}" }, contentType = { _, _ -> "artist-row" }) { index, item ->
                        LibraryCollectionRow(item, true, "artist:$letter:${entryKeys[index]}", { onItemClick(item) }, { onItemLongPress(item) }, resolveArtistArtwork)
                    }
                }
            }
            if (groups.isNotEmpty()) {
                LazyColumn(Modifier.align(Alignment.CenterEnd).width(32.dp)
                    .heightIn(max = (maxHeight - contentPadding.calculateTopPadding() - contentPadding.calculateBottomPadding()).coerceAtLeast(48.dp))
                    .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    groupPositions.forEach { (letter, index) ->
                        item(key = letter) {
                            Text(letter, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth().clickable { scope.launch { listState.scrollToItem(index) } }
                                    .padding(vertical = 2.dp), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryCollectionRow(item: ShelfItem, artist: Boolean, key: String, onClick: () -> Unit, onLongClick: () -> Unit,
    resolveArtwork: (suspend (ShelfItem) -> String?)? = null) {
    val motion = LocalLibraryCoverMotion.current
    val artwork by produceState(item.thumbnailUrl, item.browseId, item.thumbnailUrl, item.title, resolveArtwork) {
        if (artist && value.isNullOrBlank()) value = resolveArtwork?.invoke(item)
    }
    Column {
    Row(Modifier.fillMaxWidth().longPressMenuClickable(onClick = {
        if (!artist) item.browseId?.let { motion?.onSelectCard?.invoke(it, key, 10f) }
        onClick()
    }, onLongClick = onLongClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(54.dp).then(if (artist) Modifier.clip(CircleShape) else Modifier.libraryCoverMotion(item.browseId, key, 10f))
            .then(if (!artist && motion?.source != true) Modifier.clip(RoundedCornerShape(10.dp)) else Modifier)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            if (artwork.isNullOrBlank()) Icon(if (artist) Icons.Rounded.Person else Icons.Rounded.LibraryMusic, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
            else AsyncImage(artwork.artworkAt(ROW_ART_PX), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!artist && item.subtitle.isNotBlank()) Text(item.subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(BitChordIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
    }
        HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    }
}

@Composable
fun collectionTitle(key: String): String = when (key) {
    "Playlists" -> stringResource(Res.string.playlists)
    "Albums" -> stringResource(Res.string.albums)
    "Artists" -> stringResource(Res.string.artists)
    "On device" -> stringResource(Res.string.on_device)
    else -> key
}
