package com.music.bitchord.ui.screens

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.playlistCreationOrderKey
import com.music.bitchord.data.settings.LibrarySort

/** IDs identify collection shelves even when their display title is localized. */
fun HomeShelf.isPlaylistLibraryShelf(): Boolean = title == YtMusicRepository.PLAYLISTS_SHELF ||
    (items.isNotEmpty() && items.all { it.browseId?.isPlaylistLibraryId() == true })

private fun String.isPlaylistLibraryId(): Boolean = this == "LM" || startsWith("local:playlist:") ||
    startsWith("spotify:playlist:") || startsWith("PL") ||
    (startsWith("VL") && !startsWith("VLOLAK") && !startsWith("VLMPRE"))

/** Liked songs has its own shortcut; identify the system collection by id, never its translated name. */
internal fun HomeShelf.withoutLikedMusic(): HomeShelf = copy(items = items.filterNot {
    it.browseId == YtMusicRepository.LIKED_MUSIC || it.browseId == "LM"
})

/** Deleting an item or changing another shelf must not reveal an older playlist. */
internal fun HomeShelf.newestCreatedPlaylistId(createdPlaylistIds: List<String>): String? {
    val newest = createdPlaylistIds.firstOrNull()?.let(::playlistCreationOrderKey) ?: return null
    return newest.takeIf { key -> items.any { it.browseId?.let(::playlistCreationOrderKey) == key } }
}

/** Only successful creation/addition events override sorting; other cards keep their normal order. */
internal fun HomeShelf.orderedForLibrary(
    pinned: List<String>,
    sort: LibrarySort,
    createdPlaylistIds: List<String>,
): HomeShelf {
    val hasPlaylists = isPlaylistLibraryShelf() || items.any { it.browseId?.isPlaylistLibraryId() == true }
    val pinRank = if (hasPlaylists) {
        pinned.withIndex().associate { playlistCreationOrderKey(it.value) to it.index }
    } else emptyMap()
    val pinnedFirst = if (pinRank.isEmpty()) items else items.sortedBy { item ->
        item.browseId?.let { pinRank[playlistCreationOrderKey(it)] } ?: Int.MAX_VALUE
    }
    val sorted = when (sort) {
        LibrarySort.DEFAULT -> pinnedFirst
        LibrarySort.TITLE_ASC -> pinnedFirst.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        LibrarySort.TITLE_DESC -> pinnedFirst.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
    }
    if (!hasPlaylists || createdPlaylistIds.isEmpty()) return copy(items = sorted)
    val createdRank = createdPlaylistIds.map(::playlistCreationOrderKey).distinct()
        .withIndex().associate { it.value to it.index }
    return copy(items = sorted.sortedBy { item ->
        item.browseId?.let { createdRank[playlistCreationOrderKey(it)] } ?: Int.MAX_VALUE
    })
}
