package com.music.bitchord.ui.screens

import com.music.bitchord.sharedui.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalDensity
import com.music.bitchord.ui.player.PlayerSettings
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
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
import com.music.bitchord.ui.components.LIBRARY_GRID_SPACING
import com.music.bitchord.ui.components.MessageState
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.PullToRefresh
import com.music.bitchord.ui.components.SHELF_CARD_WIDTH
import com.music.bitchord.ui.components.libraryGrid
import com.music.bitchord.ui.components.shelfItemKeys
import com.music.bitchord.ui.components.rememberCoverSourceValue

/**
 * The signed-in library: the saved collections, as shelves of cards.
 *
 * Liked songs opens the liked collection from the shortcut list; saved playlists,
 * albums and artists stay in their own shelves. First-page liked data also
 * seeds the app's heart state without holding up the tab for the entire list.
 */
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
    /** Opens the full vertical collection grid, including the smaller shelf previews. */
    onShowAll: (HomeShelf) -> Unit,
    /** A compact entry to Replay; its statistics load only after opening it. */
    replay: @Composable () -> Unit,
    onSignIn: () -> Unit,
    onRetry: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    pullState: PullToRefreshState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    /**
     * The device's folders — downloads, local files, the remote libraries this
     * build supports — drawn as compact shortcuts under Replay. Built by the
     * app, since which of those exist is the platform's business.
     */
    links: List<LibraryLink>,
    /**
     * The "On device" shelf under that list: the releases kept on this device
     * whole. Left off the page entirely while there are none.
     */
    deviceItems: List<ShelfItem>,
    /** Whether the page draws its own Library heading. */
    showTitle: Boolean = true,
    /** Successful creation/addition history supplied by Android; absent on other platforms. */
    createdPlaylistIds: List<String> = emptyList(),
    /** Local creations share Playlists; downloaded collections keep On device. */
    personalPlaylists: List<ShelfItem> = emptyList(),
) {
    val livePinnedPlaylists by AppUi.host.pinnedPlaylists.collectAsStateWithLifecycle()
    val pinnedPlaylists = rememberCoverSourceValue("library-pins", livePinnedPlaylists)
    val visibleState = rememberCoverSourceValue("library-feed", state)
    val visiblePersonalPlaylists = rememberCoverSourceValue("library-personal", personalPlaylists)
    val visibleDeviceItems = rememberCoverSourceValue("library-device", deviceItems)
    val visibleCreatedIds = rememberCoverSourceValue("library-created", createdPlaylistIds)
    val onDevice = stringResource(Res.string.on_device)
    PullToRefresh(
        refreshing = refreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            if (showTitle) {
                item {
                    Text(
                        text = stringResource(Res.string.library),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
                    )
                }
            }
            item(key = "replay") { replay() }
            if (links.isNotEmpty()) {
                item(key = "links") { LibraryLinkList(links = links, onClick = onShelfItemClick) }
            }
            item(key = "shelf:$PLAYLISTS") {
                val remote = (visibleState as? UiState.Success)?.data?.shelves.orEmpty()
                    .filter { it.isPlaylistLibraryShelf() }.flatMap { it.items }
                val playlists = remember(remote, visiblePersonalPlaylists, pinnedPlaylists, visibleCreatedIds) {
                    HomeShelf(PLAYLISTS, visiblePersonalPlaylists + remote).withoutLikedMusic()
                        .orderedForLibrary(pinnedPlaylists, LibrarySort.DEFAULT, visibleCreatedIds)
                }
                PlaylistShelf(
                    shelf = playlists,
                    savedLocally = !signedIn,
                    loading = signedIn && visibleState is UiState.Loading && playlists.items.isEmpty(),
                    onItemClick = onShelfItemClick,
                    onItemLongPress = onShelfItemLongPress,
                    onNewPlaylist = onNewPlaylist,
                    onImportSpotifyPlaylist = onImportSpotifyPlaylist,
                    onShowAll = { onShowAll(playlists) },
                    pinnedPlaylists = pinnedPlaylists,
                    newestCreatedPlaylistId = playlists.newestCreatedPlaylistId(visibleCreatedIds),
                )
            }
            if (visibleDeviceItems.isNotEmpty()) {
                item(key = "shelf:$onDevice") {
                    val onDeviceShelf = remember(onDevice, visibleDeviceItems, visibleCreatedIds, pinnedPlaylists) {
                        HomeShelf(title = onDevice, items = visibleDeviceItems)
                            .orderedForLibrary(pinnedPlaylists, LibrarySort.DEFAULT, visibleCreatedIds)
                    }
                    LibraryGridShelf(
                        shelf = onDeviceShelf,
                        onItemClick = onShelfItemClick,
                        onItemLongPress = onShelfItemLongPress,
                        onShowAll = { onShowAll(onDeviceShelf) },
                    newestCreatedPlaylistId = onDeviceShelf.newestCreatedPlaylistId(visibleCreatedIds),
                    )
                }
            }
            if (!signedIn) {
                item {
                    MessageState(
                        message = stringResource(Res.string.library_sign_in_description),
                        actionLabel = stringResource(Res.string.sign_in),
                        onAction = onSignIn,
                    )
                }
                return@LazyColumn
            }
            when (visibleState) {
                is UiState.Loading -> Unit // The playlist grid reserves its loading space above.
                is UiState.Error -> item {
                    MessageState(visibleState.message, actionLabel = stringResource(Res.string.retry), onAction = onRetry)
                }
                is UiState.Success -> visibleState.data.shelves.filterNot { it.isPlaylistLibraryShelf() }.forEach { shelf ->
                    item(key = "shelf:${shelf.title}") {
                        LibraryGridShelf(
                            shelf = shelf,
                            onItemClick = onShelfItemClick,
                            onItemLongPress = onShelfItemLongPress,
                            onShowAll = { onShowAll(shelf) },
                        )
                    }
                }
            }
        }
    }
}

/** One of the Library's folder rows: the page [item] opens, behind [icon]. */
/** [logo], when set, is drawn in place of [icon] — a service's own mark. */
data class LibraryLink(val item: ShelfItem, val icon: ImageVector, val logo: DrawableResource? = null)

/** Compact music shortcuts leave the collection covers close to the top. */
@Composable
private fun LibraryLinkList(links: List<LibraryLink>, onClick: (ShelfItem) -> Unit) {
    Column(Modifier.padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        links.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { link ->
                    Surface(onClick = { onClick(link.item) }, shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (link.logo != null) {
                                Icon(painterResource(link.logo), contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                            } else {
                                Icon(link.icon, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            }
                            Text(link.item.title, style = MaterialTheme.typography.labelLarge,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Four visible columns and up to four rows; only playlists occupy cover slots. */
@Composable
private fun PlaylistShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: (ShelfItem) -> Unit,
    onNewPlaylist: () -> Unit,
    onImportSpotifyPlaylist: (() -> Unit)? = null,
    onShowAll: () -> Unit,
    pinnedPlaylists: List<String> = emptyList(),
    savedLocally: Boolean = false,
    loading: Boolean = false,
    newestCreatedPlaylistId: String? = null,
) {
    val reduceAnimation by PlayerSettings.reduceAnimation.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val previewItems = remember(shelf.items) { shelf.items.take(6) }
    val itemKeys = remember(previewItems) { shelfItemKeys(previewItems) }
    var optionsOpen by remember { mutableStateOf(false) }
    var observedNewest by remember { mutableStateOf(newestCreatedPlaylistId) }
    LaunchedEffect(newestCreatedPlaylistId) {
        if (observedNewest != newestCreatedPlaylistId) {
            observedNewest = newestCreatedPlaylistId
            if (newestCreatedPlaylistId != null) gridState.scrollToItem(0)
        }
    }
    Column(Modifier.padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = PAGE_GUTTER, end = PAGE_GUTTER - 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.playlists), style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            if (shelf.items.isNotEmpty()) {
                TextButton(onClick = onShowAll) { Text(stringResource(Res.string.show_all)) }
            }
            IconButton(onClick = onNewPlaylist) {
                    Icon(BitChordIcons.Plus, stringResource(Res.string.new_playlist),
                        tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(22.dp))
            }
            if (onImportSpotifyPlaylist != null) {
                Box {
                    IconButton(onClick = { optionsOpen = true }) {
                        Icon(Icons.Rounded.MoreHoriz, stringResource(Res.string.more),
                            tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(22.dp))
                    }
                    DropdownMenu(expanded = optionsOpen, onDismissRequest = { optionsOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.import_spotify)) },
                            leadingIcon = { Icon(painterResource(Res.drawable.spotify_logo), null, modifier = Modifier.size(20.dp)) },
                            onClick = { optionsOpen = false; onImportSpotifyPlaylist() },
                        )
                    }
                }
            }
        }
        if (shelf.items.isEmpty() && !loading) {
            Text(stringResource(if (savedLocally) Res.string.on_device else Res.string.saved_to_youtube_music),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = PAGE_GUTTER, vertical = 8.dp))
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth().animateContentSize(
                animationSpec = tween(if (reduceAnimation) 0 else 220))) {
                val gap = 12.dp
                val peek = if (!loading && previewItems.size > 4) 36.dp else 0.dp
                val cellWidth = ((maxWidth - PAGE_GUTTER * 2 - gap - peek) / 2).coerceAtLeast(48.dp)
                val cardWidth = cellWidth.coerceAtMost(136.dp)
                val count = if (loading) 4 else previewItems.size
                val rows = if (count <= 2) 1 else 2
                val labelHeight = with(LocalDensity.current) { MaterialTheme.typography.labelMedium.lineHeight.toDp() * 2 }
                val rowHeight = cardWidth + 6.dp + labelHeight
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(rows),
                    state = gridState,
                    contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().height(rowHeight * rows + 12.dp * (rows - 1)),
                ) {
                    if (loading) {
                        items(4, key = { "playlist-placeholder-$it" }) {
                            Box(Modifier.width(cellWidth), contentAlignment = Alignment.TopCenter) {
                                Box(Modifier.size(cardWidth).clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh))
                            }
                        }
                    } else {
                        itemsIndexed(previewItems, key = { index, _ -> itemKeys[index] }, contentType = { _, _ -> "playlist" }) { index, item ->
                            Box(Modifier.width(cellWidth).animateItem(
                                fadeInSpec = null, fadeOutSpec = null,
                                placementSpec = if (reduceAnimation) null else tween(220)),
                                contentAlignment = Alignment.TopCenter) {
                            ShelfCard(
                                item = item,
                                libraryCardKey = "shelf:${shelf.title}:${itemKeys[index]}",
                                onClick = { onItemClick(item) },
                                onLongPress = { onItemLongPress(item) },
                                isPinned = item.browseId in pinnedPlaylists,
                                compact = true,
                                modifier = Modifier.width(cardWidth),
                            )
                            }
                        }
                    }
                }
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
 * cap — see [PlaylistShelf].
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

/**
 * Everything a Library shelf's "Show all" opens onto — the same cards, at the
 * same [libraryGrid] width, run down the screen instead of stopping at one row.
 */
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
) {
    // Re-read live rather than trusting [shelf] to already be sorted: this page
    // is opened from a snapshot (see `libraryShowAll` in MainActivity), and a
    // pin toggled from this page's own long-press menu must move the card
    // immediately rather than waiting for the row underneath to be revisited.
    val pinnedPlaylists by AppUi.host.pinnedPlaylists.collectAsStateWithLifecycle()
    val librarySort by AppUi.host.librarySort.collectAsStateWithLifecycle()
    // Recently created/updated playlists stay easy to find after a title sort or provider
    // refresh. Pins and explicit sorting still arrange the older collections.
    val sortedShelf = remember(shelf, pinnedPlaylists, librarySort, createdPlaylistIds) {
        (if (shelf.isPlaylistLibraryShelf()) shelf.withoutLikedMusic() else shelf)
            .orderedForLibrary(pinnedPlaylists, librarySort, createdPlaylistIds)
    }
    val itemKeys = remember(sortedShelf.items) { shelfItemKeys(sortedShelf.items) }
    val newestActivity = sortedShelf.newestCreatedPlaylistId(createdPlaylistIds)
    var observedNewest by remember(gridState) { mutableStateOf(newestActivity) }
    LaunchedEffect(newestActivity) {
        if (observedNewest == newestActivity) return@LaunchedEffect
        observedNewest = newestActivity
        if (newestActivity != null) gridState.scrollToItem(0)
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = libraryGrid(maxWidth - PAGE_GUTTER * 2)
        LazyVerticalGrid(
            columns = GridCells.Fixed(grid.columns),
            state = gridState,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(LIBRARY_GRID_SPACING),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(horizontal = PAGE_GUTTER),
        ) {
            if (onNewPlaylist != null) {
                item(key = "heading", span = { GridItemSpan(maxLineSpan) }, contentType = "heading") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.playlists), style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                        IconButton(onClick = onNewPlaylist) {
                            Icon(BitChordIcons.Plus, stringResource(Res.string.new_playlist),
                                tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            }
            itemsIndexed(
                sortedShelf.items,
                key = { index, _ -> itemKeys[index] },
                contentType = { _, _ -> "shelf-card" },
            ) { index, item ->
                ShelfCard(
                    item = item,
                    libraryCardKey = "grid:${itemKeys[index]}",
                    onClick = { onItemClick(item) },
                    onLongPress = { onItemLongPress(item) },
                    modifier = Modifier.fillMaxWidth(),
                    isPinned = item.browseId != null && item.browseId in pinnedPlaylists,
                )
            }
        }
    }
}

/** The library feed whose cards are the account's own — see [PlaylistShelf]. */
private const val PLAYLISTS = YtMusicRepository.PLAYLISTS_SHELF
