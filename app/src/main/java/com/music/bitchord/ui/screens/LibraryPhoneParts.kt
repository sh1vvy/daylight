package com.music.bitchord.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.spotify_logo
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.download.Downloads
import com.music.bitchord.download.SavedCollection
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.replay.ReplayStoryPage
import com.music.bitchord.ui.icons.BitChordIcons

// The phone's halves of the shared Library page: its list of folders, what
// sits on its "On device" shelf, and the way in to Replay at its head.

/**
 * Music shortcuts under Replay: liked songs, downloads, device files, the
 * optional cache folder and connected Spotify library.
 */
@Composable
fun libraryLinks(): List<LibraryLink> {
    val showCacheFolder by AppSettings.showCacheFolder.collectAsStateWithLifecycle()
    val spotifyConnected by AppSettings.spotifySpdcToken.collectAsStateWithLifecycle()
    fun link(icon: ImageVector, title: String, subtitle: String, browseId: String) = LibraryLink(
        item = ShelfItem(
            title = title,
            subtitle = subtitle,
            thumbnailUrl = null,
            videoId = null,
            browseId = browseId,
        ),
        icon = icon,
    )
    return listOfNotNull(
        link(
            Icons.Rounded.MusicNote,
            stringResource(R.string.auto_liked),
            "",
            YtMusicRepository.LIKED_MUSIC,
        ),
        link(
            Icons.Rounded.Download,
            stringResource(R.string.downloads),
            stringResource(R.string.downloaded_songs),
            "local:downloads",
        ),
        link(
            Icons.Rounded.Folder,
            stringResource(R.string.local_music),
            stringResource(R.string.audio_files_on_device),
            "local:all",
        ),
        // Opt-in from Settings → Storage: what the song cache is holding from
        // streaming sources. See [com.music.bitchord.playback.AudioCache.cachedSongs].
        link(
            Icons.Rounded.Storage,
            stringResource(R.string.cached_songs),
            stringResource(R.string.cached_songs_subtitle),
            CACHE_FOLDER_BROWSE_ID,
        ).takeIf { showCacheFolder },
        // Only while signed in to Spotify in Settings → Accounts; it opens that
        // account's playlists rather than a browse page.
        link(
            Icons.Rounded.Cloud,
            stringResource(R.string.spotify),
            stringResource(R.string.spotify_library_subtitle),
            SPOTIFY_BROWSE_ID,
        ).takeIf { spotifyConnected.isNotBlank() }?.copy(logo = Res.drawable.spotify_logo),
    )
}

/**
 * The phone's "On device" shelf: the playlists and albums downloaded whole —
 * the same promise the folders above it make, here, now, without a network.
 * Nothing is truncated: the shelf is a row that scrolls, so "all of them"
 * costs nothing.
 */
@Composable
fun libraryDeviceItems(downloadedReleases: List<SavedCollection>): List<ShelfItem> {
    val downloadedPlaylist = stringResource(R.string.downloaded_playlist)
    return downloadedReleases.map { release ->
        ShelfItem(
            title = release.title,
            // The credit the release was downloaded with, because this is also
            // what the page it opens bills itself by — see `headerLines`, which
            // reads the kind and the owner back out of it. Saying "Downloaded
            // playlist" here instead would make that header read "Downloaded
            // playlist" over "PLAYLIST • 12 SONGS", and the shelf this card is on
            // already says where it lives.
            subtitle = release.subtitle.ifBlank { if (release.playlist) downloadedPlaylist else "" },
            thumbnailUrl = release.thumbnailUrl,
            videoId = null,
            browseId = Downloads.pageIdFor(release.id),
        )
    }
}

/** The Library row that opens the connected Spotify account; handled by the app, not a browse page. */
const val SPOTIFY_BROWSE_ID = "app:spotify"

/** The Cached songs folder's page id — one of the `local:` device folders. */
const val CACHE_FOLDER_BROWSE_ID = "local:cache"

/** Replay stays reachable without reading or repeating its charts in Library. */
@Composable
fun LibraryReplayEntry(onOpenReplay: (ReplayStoryPage) -> Unit) {
    Surface(
        onClick = { onOpenReplay(ReplayStoryPage.INTRO) },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Text(stringResource(R.string.your_replay), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f))
            Icon(BitChordIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}
