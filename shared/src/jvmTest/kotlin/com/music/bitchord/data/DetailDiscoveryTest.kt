package com.music.bitchord.data

import com.music.bitchord.data.model.*
import kotlin.test.*
import org.junit.Test

class DetailDiscoveryTest {
    @Test fun playlistUsesEachCreditedChannelOnceIncludingCollaborators() {
        val songs = listOf(Song("a", "Song", "A, B", null, artistId = "UCa",
            artists = listOf(ArtistRef("A", "UCa"), ArtistRef("B", "UCb"))),
            Song("b", "Song", "B", null, artistId = "UCb"),
            Song("c", "Song", "Unknown", null))
        assertEquals(listOf("UCa", "UCb"), featuredPlaylistArtists(songs).map { it.browseId })
    }
    @Test fun longPlaylistsKeepAllTheirArtistIdentities() {
        assertEquals(500, featuredPlaylistArtists((1..500).map { Song("$it", "Song", "Artist $it", null, artistId = "UC$it") }).size)
    }
    @Test fun albumsExcludeCurrentDuplicatesTracksAndOtherBrowseTypes() {
        fun card(id: String, video: String? = null) = ShelfItem(id, "", null, video, id)
        val shelves = listOf(HomeShelf("More", listOf(card("MPREbcurrent"), card("MPREbother"), card("UCartist"), card("VLplaylist"), card("MPREbtrack", "track"), card("MPREbother"))))
        assertEquals(listOf("MPREbother"), discoveryAlbumCards(shelves, "MPREbcurrent").map { it.browseId })
    }
    @Test fun radioAlbumsNeedRealEndpointsAndNames() {
        val songs = listOf(Song("a", "Song", "A", null, albumId = "MPREb1", albumName = "Album"),
            Song("b", "Song", "A", null, albumId = "MPREb1", albumName = "Album"),
            Song("c", "Song", "B", null, albumId = "MPREb2", albumName = "Other"),
            Song("d", "Song", "B", null, albumId = "MPREb3"))
        assertEquals(listOf("MPREb2"), recommendedAlbumCards(songs, setOf("MPREb1")).map { it.browseId })
    }
}
