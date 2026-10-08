package com.music.bitchord.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.CreatorProfilePage
import com.music.bitchord.data.model.CreatorProvider
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.ui.creatorCollections

/** A small real creator profile, with its originating playlist always available. */
@Composable
fun CreatorProfileScreen(
    profile: CreatorProfilePage,
    contentPadding: PaddingValues,
    onPlaylistClick: (BrowseItem) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val colours = MaterialTheme.colorScheme
    val collections = remember(profile.currentPlaylist, profile.playlists) { profile.creatorCollections() }
    val provider = when (profile.creator.provider) {
        CreatorProvider.YOUTUBE_MUSIC -> "YouTube Music"
        CreatorProvider.SPOTIFY -> "Spotify"
        CreatorProvider.LOCAL -> stringResource(R.string.creator_on_device)
    }
    LazyColumn(
        modifier = modifier.background(colours.background),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "creator_header") {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(104.dp).clip(CircleShape).background(colours.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    val avatar = profile.creator.thumbnailUrl
                    if (!avatar.isNullOrBlank()) {
                        AsyncImage(
                            model = avatar.artworkAt(320),
                            contentDescription = null,
                            modifier = Modifier.size(104.dp),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(Icons.Rounded.Person, null, Modifier.size(52.dp), tint = colours.onPrimaryContainer)
                    }
                }
                Text(
                    profile.creator.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colours.onBackground,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${stringResource(R.string.playlist_creator)} · $provider",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colours.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                profile.description?.takeIf(String::isNotBlank)?.let { description ->
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colours.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        item(key = "collections_title") {
            Text(
                // The originating playlist may be private even when the
                // creator's other collections came from its public profile.
                stringResource(R.string.playlists),
                style = MaterialTheme.typography.titleMedium,
                color = colours.onBackground,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        items(collections, key = { it.browseId }, contentType = { "creator_playlist" }) { playlist ->
            Row(
                Modifier.padding(horizontal = 24.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colours.surfaceContainerHigh)
                    .clickable { onPlaylistClick(playlist) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)).background(colours.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    if (playlist.thumbnailUrl.isNullOrBlank()) {
                        Icon(Icons.Rounded.QueueMusic, null, tint = colours.onPrimaryContainer)
                    } else {
                        AsyncImage(
                            model = playlist.thumbnailUrl.artworkAt(ROW_ART_PX),
                            contentDescription = null,
                            modifier = Modifier.size(58.dp),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        playlist.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colours.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        profile.creator.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = colours.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = colours.onSurfaceVariant)
            }
        }
        when (val state = profile.playlists) {
            UiState.Loading -> item(key = "creator_loading") {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
            is UiState.Error -> item(key = "creator_error") {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(state.message, color = colours.onSurfaceVariant, textAlign = TextAlign.Center)
                    FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
            }
            else -> Unit
        }
        item(key = "creator_footer") { Spacer(Modifier.height(12.dp)) }
    }
}
