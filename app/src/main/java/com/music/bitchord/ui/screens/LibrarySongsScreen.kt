/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.model.ROW_ART_PX
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.settings.SongSort
import com.music.bitchord.ui.components.MessageState
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.SongRow

/** The cached liked collection opens directly into its rows, with no artwork/header lookup. */
@Composable
fun LibrarySongsScreen(
    title: String,
    state: UiState<List<Song>>,
    listState: LazyListState,
    sort: SongSort,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
    onPlay: (List<Song>) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onMore: ((List<Song>) -> Unit)?,
    contentPadding: PaddingValues,
    artistHeader: Boolean = false,
    artistArtworkUrl: String? = null,
) {
    val raw = (state as? UiState.Success)?.data.orEmpty()
    val songs = remember(raw, sort) { raw.sortedForDetail(sort) }
    LazyColumn(state = listState, contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize().testTag(if (artistHeader) "library-artist-liked-list" else "library-liked-list")) {
        item(key = "liked-header", contentType = "header") {
            Column(Modifier.padding(horizontal = PAGE_GUTTER + 4.dp).padding(bottom = 20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (artistHeader) {
                        Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer),
                            contentAlignment = Alignment.Center) {
                            if (artistArtworkUrl.isNullOrBlank()) Icon(Icons.Rounded.Person, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            else AsyncImage(artistArtworkUrl.artworkAt(ROW_ART_PX), null, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.width(14.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = if (artistHeader) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onSurface)
                        if (artistHeader) Text(stringResource(R.string.auto_liked), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (onMore != null) IconButton(onClick = { onMore(songs) }, enabled = songs.isNotEmpty()) {
                        Icon(Icons.Rounded.MoreHoriz, stringResource(R.string.more), tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    FilledTonalButton(onClick = { onPlay(songs) }, enabled = songs.isNotEmpty(), shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.weight(1f).height(48.dp)) {
                        Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(21.dp)); Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.play))
                    }
                    FilledTonalButton(onClick = { onShuffle(songs) }, enabled = songs.isNotEmpty(), shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.weight(1f).height(48.dp)) {
                        Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(21.dp)); Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.shuffle))
                    }
                }
            }
        }
        when (state) {
            is UiState.Loading -> item(key = "loading", contentType = "message") {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
            is UiState.Error -> item(key = "error", contentType = "message") { MessageState(state.message) }
            is UiState.Success -> if (songs.isEmpty()) item(key = "empty", contentType = "message") {
                MessageState(stringResource(R.string.library_liked_empty))
            }
        }
        itemsIndexed(songs, key = { index, song -> "${song.videoId}:$index" }, contentType = { _, _ -> "song" }) { index, song ->
            SongRow(song, onClick = { onSongClick(songs, index) }, onLongPress = { onSongLongPress(song) },
                onSwipeToQueue = { onSongSwipe(song) }, isCurrent = currentSong?.videoId == song.videoId,
                isPlaying = isPlaying, modifier = Modifier.padding(horizontal = PAGE_GUTTER + 4.dp))
            HorizontalDivider(Modifier.padding(start = PAGE_GUTTER + 4.dp + 62.dp, end = PAGE_GUTTER + 4.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        }
    }
}
