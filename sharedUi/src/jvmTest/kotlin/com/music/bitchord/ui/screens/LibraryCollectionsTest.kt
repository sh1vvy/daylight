/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.ui.screens

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.ShelfItem
import kotlin.test.*

class LibraryCollectionsTest {
    private fun item(id: String, title: String = id) = ShelfItem(title, "", null, null, id)
    private fun page(vararg shelves: HomeShelf) = LibraryPage(emptyList(), emptyList(), shelves.toList())

    @Test fun `overview preserves all collections but removes duplicate system likes and subscribed artists`() {
        val a = item("UCartist", "Artist")
        val collections = libraryCollections(page(
            HomeShelf("Playlists", listOf(item("VLLM"), item("VLPLone"), item("PLone"))),
            HomeShelf("Albums", listOf(item("MPREalbum"))),
            HomeShelf("Artists", listOf(a)), HomeShelf("Subscriptions", listOf(a, item("UCother"))),
        ), listOf(item("spotify:playlist:mine"), item("local:playlist:mine")), emptyList(), emptyList())
        assertEquals(listOf("spotify:playlist:mine", "local:playlist:mine", "VLPLone"), collections.playlists.items.map { it.browseId })
        assertTrue(collections.artists.items.isEmpty())
        assertEquals(4, collections.recent.size)
        assertFalse(collections.recent.any { it.browseId?.startsWith("UC") == true })
    }

    @Test fun `known playlist activity leads without replacing provider album order`() {
        val albums = listOf(item("MPREnew"), item("MPREold"))
        val collections = libraryCollections(page(HomeShelf("Albums", albums),
            HomeShelf("Playlists", listOf(item("VLPLone"), item("VLPLtwo"), item("VLPLthree")))),
            emptyList(), emptyList(), listOf("PLthree", "missing"))
        assertEquals(listOf("VLPLthree", "MPREnew", "VLPLone", "MPREold", "VLPLtwo"), collections.recent.map { it.browseId })
        assertEquals(albums, collections.albums.items)
    }

    @Test fun `local library remains usable before remote data or sign in`() {
        val local = item("local:playlist:one")
        val collections = libraryCollections(null, listOf(local), emptyList(), listOf(local.browseId!!))
        assertEquals(listOf(local), collections.recent)
        assertEquals(listOf(local), collections.playlists.items)
        assertTrue(collections.albums.items.isEmpty())
    }

    @Test fun `localized shelves use ids and never mistake album playlists for personal playlists`() {
        val collections = libraryCollections(page(HomeShelf("Albums enregistrés", listOf(item("VLOLAKalbum"))),
            HomeShelf("Artistes", listOf(item("UCone"))), HomeShelf("Listes", listOf(item("VLPLone")))),
            emptyList(), emptyList(), emptyList())
        assertEquals("VLOLAKalbum", collections.albums.items.single().browseId)
        assertTrue(collections.artists.items.isEmpty())
        assertEquals("VLPLone", collections.playlists.items.single().browseId)
    }

    @Test fun `alphabet index handles accented names punctuation empty names and non Latin scripts`() {
        assertEquals("A", libraryInitial(" ADÉLA"))
        assertEquals("E", libraryInitial("Édith Piaf"))
        assertEquals("#", libraryInitial("123"))
        assertEquals("#", libraryInitial("宇多田ヒカル"))
        assertEquals("#", libraryInitial(""))
    }
    @Test fun `only artists credited on liked songs join the library`() {
        val liked = Song("liked", "Song", "Lead & Guest", null, artists = listOf(ArtistRef("Lead", "UClead"), ArtistRef("Guest", "UCguest")))
        val cards = listOf(item("UClead", "Lead").copy(thumbnailUrl = "lead-art"), item("UCunliked", "Unliked"))
        val page = LibraryPage(listOf(liked), listOf(Song("playlist-only", "Song", "Unliked", null, artistId = "UCunliked")),
            listOf(HomeShelf("Artists", cards)))
        val artists = libraryCollections(page, emptyList(), emptyList(), emptyList()).artists.items
        assertEquals(listOf("Lead", "Guest"), artists.map { it.title })
        assertEquals("lead-art", artists.first().thumbnailUrl)
        assertEquals("UCguest", artists.last().browseId)
    }
    @Test fun `repeated credits topic aliases and missing ids produce one artist row`() {
        val songs = listOf(Song("one", "One", "ADÉLA", null),
            Song("two", "Two", "Adéla", null, artistId = "UCreal"),
            Song("three", "Three", "ADÉLA", null, artistId = "UCtopic"),
            Song("four", "Four", "ADÉLA", null, artistId = "UCreal"))
        val artists = likedLibraryArtists(songs, emptyList())
        assertEquals(1, artists.size); assertEquals("UCreal", artists.single().browseId)
    }
    @Test fun `unlinked liked credits remain reachable without guessing a profile`() {
        val artists = likedLibraryArtists(listOf(Song("one", "One", "Missing", null)),
            listOf(item("UCa", "Missing"), item("UCb", "Missing")))
        assertEquals(LIKED_ARTIST_LOOKUP_PREFIX + "missing", artists.single().browseId)
        assertNull(artists.single().thumbnailUrl)
    }

}
