/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-12 through 2026-10-12.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.search

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.music.bitchord.R
import com.music.bitchord.data.model.*

/** Transitional provider mapping only. The new screen has no dependency on BitChord's search UI. */
@Composable
fun SearchExperience(
    state: SearchSnapshot<SearchResult>, query: String, filter: SearchFilter,
    library: Boolean, libraryResults: UiState<List<Song>>?, currentSong: Song?,
    history: List<SearchHistoryEntity>, onFilter: (SearchFilter) -> Unit,
    onSong: (Song) -> Unit, onLocalSong: (List<Song>, Int) -> Unit,
    onSongMenu: (Song) -> Unit, onSongQueue: (Song) -> Unit, onBrowse: (BrowseItem) -> Unit, onBrowseMenu: (BrowseItem) -> Unit,
    onHistory: (SearchHistoryEntity) -> Unit, onRemoveHistory: (String) -> Unit, onClearHistory: () -> Unit,
    onSuggestion: (String) -> Unit, onLoadMore: () -> Unit, onRetry: () -> Unit, onRetryPage: () -> Unit,
    listState: LazyListState, padding: PaddingValues, modifier: Modifier = Modifier,
) {
    val local = (libraryResults as? UiState.Success)?.data.orEmpty()
    val raw = if (library) local.map(SearchResult::Track) else if (state.stage == SearchStage.EDITING) state.previews else state.rows.orEmpty()
    fun id(row: SearchResult) = when (row) {
        is SearchResult.Track -> "v:${row.song.videoId}"
        is SearchResult.TopTrack -> "v:${row.song.videoId}"
        is SearchResult.Browse -> "b:${row.item.browseId}"
    }
    val rows = remember(raw, currentSong?.videoId) { raw.distinctBy(::id).map { row ->
        when (row) {
            is SearchResult.Track, is SearchResult.TopTrack -> {
                val song = if (row is SearchResult.Track) row.song else (row as SearchResult.TopTrack).song
                SearchHit(id(row), song.title, song.artist, song.thumbnailUrl.artworkAt(112), SearchHitKind.TRACK,
                    song.isSamePlaybackItemAs(currentSong))
            }
            is SearchResult.Browse -> SearchHit(id(row), row.item.title, row.item.subtitle, row.item.thumbnailUrl.artworkAt(112),
                when (row.item.type) { BrowseType.ARTIST -> SearchHitKind.ARTIST; BrowseType.ALBUM -> SearchHitKind.ALBUM; else -> SearchHitKind.PLAYLIST })
        }
    } }
    val recent = remember(history) { history.map { entity -> SearchHit(entity.id, entity.title, entity.subtitle,
        entity.artworkUrl.artworkAt(112), if (entity.id.startsWith("q:")) SearchHitKind.QUERY else when (entity.entityType) {
            EntityType.ARTIST -> SearchHitKind.ARTIST; EntityType.ALBUM -> SearchHitKind.ALBUM
            EntityType.PLAYLIST -> SearchHitKind.PLAYLIST; else -> SearchHitKind.TRACK
        }) } }
    fun click(hit: SearchHit, menu: Boolean) {
        when (val item = raw.firstOrNull { id(it) == hit.key }) {
            is SearchResult.Track -> if (menu) onSongMenu(item.song) else if (library) onLocalSong(local, local.indexOf(item.song)) else onSong(item.song)
            is SearchResult.TopTrack -> if (menu) onSongMenu(item.song) else onSong(item.song)
            is SearchResult.Browse -> if (menu) onBrowseMenu(item.item) else onBrowse(item.item)
            null -> Unit
        }
    }
    val filters = listOf(stringResource(R.string.daylight_search_all), stringResource(R.string.songs), stringResource(R.string.daylight_search_videos),
        stringResource(R.string.albums), stringResource(R.string.artists), stringResource(R.string.playlists))
    DaylightSearchResults(query, editing = !library && state.stage == SearchStage.EDITING,
        suggestions = state.suggestions, previews = if (!library && state.stage == SearchStage.EDITING) rows else emptyList(),
        results = if (library) (libraryResults as? UiState.Success)?.let { rows } else state.rows?.let { rows },
        history = recent, loading = if (library) libraryResults is UiState.Loading else state.loading,
        loadingMore = !library && state.loadingMore, error = if (library) (libraryResults as? UiState.Error)?.message else state.error,
        pageError = if (library) null else state.pageError,
        labels = SearchLabels(stringResource(R.string.search), stringResource(R.string.recent_searches), stringResource(R.string.clear),
            stringResource(if (library) R.string.daylight_search_library_hint else R.string.search_empty), stringResource(R.string.no_results),
            stringResource(R.string.try_again), stringResource(R.string.more), stringResource(R.string.remove), stringResource(R.string.daylight_search_matches)),
        filters = if (library) emptyList() else filters, selectedFilter = filter.ordinal,
        onFilter = { onFilter(SearchFilter.entries[it]) },
        onHit = { click(it, false) }, onMenu = { click(it, true) },
        onQueue = { hit -> when (val row = raw.firstOrNull { id(it) == hit.key }) {
            is SearchResult.Track -> onSongQueue(row.song); is SearchResult.TopTrack -> onSongQueue(row.song); else -> Unit
        } },
        onHistory = { hit -> history.firstOrNull { it.id == hit.key }?.let(onHistory) },
        onRemoveHistory = { onRemoveHistory(it.key) }, onClearHistory = onClearHistory,
        onSuggestion = onSuggestion, onLoadMore = onLoadMore, onRetry = onRetry, onRetryPage = onRetryPage,
        listState = listState, scrollEpoch = state.scrollEpoch, padding = padding, modifier = modifier)
}
