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
        assertEquals(listOf("UCartist", "UCother"), collections.artists.items.map { it.browseId })
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
        assertEquals("UCone", collections.artists.items.single().browseId)
        assertEquals("VLPLone", collections.playlists.items.single().browseId)
    }

    @Test fun `alphabet index handles accented names punctuation empty names and non Latin scripts`() {
        assertEquals("A", libraryInitial(" ADÉLA"))
        assertEquals("E", libraryInitial("Édith Piaf"))
        assertEquals("#", libraryInitial("123"))
        assertEquals("#", libraryInitial("宇多田ヒカル"))
        assertEquals("#", libraryInitial(""))
    }
}
