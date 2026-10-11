/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.ui.screens

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.playlistCreationOrderKey
import com.music.bitchord.data.settings.LibrarySort
import java.text.Normalizer
import java.util.Locale

/** Built once per library snapshot, never in a lazy row or a scroll callback. */
data class LibraryCollections(
    val playlists: HomeShelf,
    val albums: HomeShelf,
    val artists: HomeShelf,
    val recent: List<ShelfItem>,
)

fun libraryCollections(
    page: LibraryPage?,
    personal: List<ShelfItem>,
    pinned: List<String>,
    activity: List<String>,
): LibraryCollections {
    val shelves = page?.shelves.orEmpty()
    val playlists = HomeShelf("Playlists", uniqueCollections(personal + shelves
        .filter { it.isPlaylistLibraryShelf() }.flatMap { it.items }))
        .withoutLikedMusic().orderedForLibrary(pinned, LibrarySort.DEFAULT, activity)
    val albums = HomeShelf("Albums", uniqueCollections(shelves.filter {
        it.title == "Albums" || (it.items.isNotEmpty() && it.items.all { item ->
            item.browseId?.let { id -> id.startsWith("MPRE") || id.startsWith("VLOLAK") } == true
        })
    }.flatMap { it.items }))
    val artists = HomeShelf("Artists", uniqueCollections(shelves.filter {
        it.title == "Artists" || it.title == "Subscriptions" ||
            (it.items.isNotEmpty() && it.items.all { item -> item.browseId?.startsWith("UC") == true })
    }.flatMap { it.items }))
    // The provider gives recency within each collection feed, not cross-feed dates.
    // Keep that order, interleave the feeds, and promote only known local activity.
    // Never invent a saved timestamp or treat simply opening an album as adding it.
    val recent = ArrayList<ShelfItem>(playlists.items.size + albums.items.size)
    val activityKeys = activity.map(::playlistCreationOrderKey).toSet()
    recent += playlists.items.filter { it.browseId?.let(::playlistCreationOrderKey) in activityKeys }
    val remaining = playlists.items.filterNot { it.browseId?.let(::playlistCreationOrderKey) in activityKeys }
    repeat(maxOf(remaining.size, albums.items.size)) { index ->
        albums.items.getOrNull(index)?.let(recent::add)
        remaining.getOrNull(index)?.let(recent::add)
    }
    return LibraryCollections(playlists, albums, artists, uniqueCollections(recent))
}

internal fun uniqueCollections(items: List<ShelfItem>): List<ShelfItem> {
    val seen = HashSet<String>()
    return items.filter { item ->
        val id = item.browseId?.let(::playlistCreationOrderKey) ?: item.videoId
        id == null || seen.add(id)
    }
}

/** Accent-insensitive alphabetical navigation; non-Latin names remain reachable under #. */
internal fun libraryInitial(title: String): String {
    val normalized = Normalizer.normalize(title.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").uppercase(Locale.ROOT)
    return normalized.firstOrNull()?.takeIf { it in 'A'..'Z' }?.toString() ?: "#"
}
