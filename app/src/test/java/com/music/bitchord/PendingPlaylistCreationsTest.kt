package com.music.bitchord

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.ui.PendingPlaylistCreations
import com.music.bitchord.ui.LatestLibraryRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingPlaylistCreationsTest {
    private fun playlist(id: String, title: String = id) = UserPlaylist(id, title, "", null)
    private fun UserPlaylist.card() = ShelfItem(title, subtitle, thumbnailUrl, null, browseId)
    private fun page(vararg cards: ShelfItem) = LibraryPage(
        likedSongs = emptyList(),
        librarySongs = emptyList(),
        shelves = if (cards.isEmpty()) emptyList() else listOf(HomeShelf(YtMusicRepository.PLAYLISTS_SHELF, cards.toList())),
    )

    @Test
    fun `successful creates remain visible while empty or old provider shelves catch up`() {
        val pending = PendingPlaylistCreations()
        val first = playlist("PLfirst", "Z first")
        val newer = playlist("PLnewer", "A newer")
        pending.remember("listener", first)
        pending.remember("listener", newer)
        val emptyProvider = pending.mergeLibrary("listener", page())
        assertFalse(emptyProvider.isEmpty)
        assertEquals(listOf(newer.card(), first.card()), emptyProvider.shelves.single().items)
        val old = playlist("PLold").card()
        assertEquals(
            listOf(newer.card(), first.card(), old),
            pending.mergeLibrary("listener", page(old)).shelves.single().items,
        )
    }

    @Test
    fun `acknowledgement uses provider metadata without duplicates or resurrecting removed provider cards`() {
        val pending = PendingPlaylistCreations()
        val created = playlist("PLnew")
        pending.remember("listener", created)
        val provider = created.card().copy(browseId = "PLnew", title = "Server title", subtitle = "Server owner")
        assertEquals(listOf(provider), pending.mergeLibrary("listener", page(provider)).shelves.single().items)
        assertTrue(pending.mergeLibrary("listener", page()).isEmpty)
    }

    @Test
    fun `account and profile changes never share pending library or picker cards`() {
        val pending = PendingPlaylistCreations()
        val personal = playlist("PLpersonal")
        pending.remember("account:personal", personal)
        assertTrue(pending.mergeLibrary("account:brand", page()).isEmpty)
        assertTrue(pending.mergeOwnPlaylists("account:brand", emptyList()).isEmpty())
        pending.clear()
        assertTrue(pending.mergeLibrary("account:personal", page()).isEmpty)
        val brand = playlist("PLbrand")
        pending.remember("account:brand", brand)
        assertEquals(listOf(brand), pending.mergeOwnPlaylists("account:brand", emptyList()))
    }

    @Test
    fun `deletion purges optimistic cards and rename updates them without creating another card`() {
        val pending = PendingPlaylistCreations()
        val created = playlist("PLnew", "Before")
        pending.remember("listener", created)
        pending.rename("listener", created.browseId, "After")
        assertEquals("After", pending.mergeLibrary("listener", page()).shelves.single().items.single().title)
        pending.remove("listener", created.browseId)
        assertTrue(pending.mergeLibrary("listener", page()).isEmpty)
        assertTrue(pending.mergeOwnPlaylists("listener", emptyList()).isEmpty())
    }

    @Test
    fun `picker refresh retains pending creates and prefers provider metadata for existing cards`() {
        val pending = PendingPlaylistCreations()
        val created = playlist("PLnew")
        pending.remember("listener", created)
        val older = playlist("PLolder")
        assertEquals(listOf(created, older), pending.mergeOwnPlaylists("listener", listOf(older)))
        val updated = created.copy(title = "Provider name")
        assertEquals(listOf(updated, older), pending.mergeOwnPlaylists("listener", listOf(updated, older)))
    }

    @Test
    fun `an older pre-create library response cannot remove the card a newer response acknowledged`() {
        val requests = LatestLibraryRequest()
        val pending = PendingPlaylistCreations()
        val older = requests.begin()
        val beforeCreation = page()
        val created = playlist("PLnew", "Created while older request was in flight")
        pending.remember("listener", created)
        val newer = requests.begin()
        val provider = page(created.card().copy(title = "Latest provider title"))
        var published: LibraryPage? = null
        if (requests.isCurrent(newer)) published = pending.mergeLibrary("listener", provider)
        if (requests.isCurrent(older)) published = pending.mergeLibrary("listener", beforeCreation)
        assertEquals(provider, published)
        assertEquals("Latest provider title", published?.shelves?.single()?.items?.single()?.title)
    }

    @Test
    fun `switching away and back to the same listener invalidates its earlier in-flight library read`() {
        val requests = LatestLibraryRequest()
        val previousVisit = requests.begin()
        requests.invalidate()
        assertFalse(requests.isCurrent(previousVisit))
        val returningVisit = requests.begin()
        assertFalse(requests.isCurrent(previousVisit))
        assertTrue(requests.isCurrent(returningVisit))
    }
}
