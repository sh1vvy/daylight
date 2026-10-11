/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.data.model

const val LIBRARY_ARTIST_SONGS_PREFIX = "library:artist-songs:"

/** Match credited artists, including topic aliases and featured credits, without substring guesses. */
fun likedSongsForArtist(songs: List<Song>, artist: ArtistRef): List<Song> {
    val name = normalizedArtistName(artist.name)
    val id = artist.browseId?.takeIf { it.startsWith("UC") }
    if (name.isEmpty() && id == null) return emptyList()
    return songs.filter { song ->
        song.artists.ifEmpty { listOf(ArtistRef(song.artist, song.artistId)) }.any { credit ->
            (name.isNotEmpty() && normalizedArtistName(credit.name) == name) || (id != null && credit.browseId == id)
        }
    }.distinctBy { it.videoId }
}
