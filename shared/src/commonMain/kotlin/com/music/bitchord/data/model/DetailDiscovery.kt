package com.music.bitchord.data.model

/** Stable channel identities in the playlist's original running order. */
fun featuredPlaylistArtists(songs: List<Song>): List<ArtistRef> = songs.asSequence()
    .flatMap { song ->
        (song.artists.ifEmpty {
            song.artistId?.let { listOf(ArtistRef(song.artist, it)) }.orEmpty()
        }).asSequence()
    }
    .filter { it.name.isNotBlank() && it.browseId?.startsWith("UC") == true }
    .distinctBy { it.browseId }.toList()

fun discoveryAlbumCards(shelves: List<HomeShelf>, currentId: String): List<ShelfItem> = shelves
    .flatMap { it.items }.filter { it.browseId?.startsWith("MPREb") == true &&
        it.browseId != currentId && it.videoId == null }
    .distinctBy { it.browseId }.take(20)

/** Radio returns recordings; only recordings with real album endpoints become cards. */
fun recommendedAlbumCards(songs: List<Song>, excluding: Set<String>): List<ShelfItem> = songs
    .filter { it.albumId?.startsWith("MPREb") == true && it.albumId !in excluding &&
        !it.albumName.isNullOrBlank() }
    .distinctBy { it.albumId }.take(20)
    .map { ShelfItem(it.albumName!!, it.artist, it.thumbnailUrl, null, it.albumId) }
