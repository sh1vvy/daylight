package com.music.bitchord.ui.components

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ListKeysTest {
    private fun track(id: String, title: String = id) = ShelfItem(
        title = title,
        subtitle = "Artist",
        thumbnailUrl = null,
        videoId = id,
        browseId = null,
    )

    @Test
    fun `inserting recents retains the existing shelf keys`() {
        val shelves = listOf(HomeShelf("For you", emptyList()), HomeShelf("New releases", emptyList()))
        val before = homeShelfKeys(shelves)
        val after = homeShelfKeys(listOf(HomeShelf("Recents", emptyList())) + shelves)
        assertEquals(before, after.drop(1))
    }

    @Test
    fun `sort and metadata refresh retain a track key`() {
        val before = shelfItemKeys(listOf(track("a"), track("b")))
        val after = shelfItemKeys(listOf(track("b", "Updated title"), track("a")))
        assertEquals(before.reversed(), after)
    }

    @Test
    fun `duplicate provider tracks and headings have distinct keys`() {
        val tracks = listOf(track("repeat"), track("other"), track("repeat"))
        val trackKeys = shelfItemKeys(tracks)
        assertEquals(tracks.size, trackKeys.toSet().size)
        assertNotEquals(trackKeys.first(), trackKeys.last())

        val shelves = List(3) { HomeShelf("Recommended", emptyList()) }
        assertEquals(shelves.size, homeShelfKeys(shelves).toSet().size)
    }

    @Test
    fun `track and browse identity do not collide`() {
        val items = listOf(
            track("same-id"),
            track("unused").copy(videoId = null, browseId = "same-id"),
            track("unused").copy(videoId = null, title = "same-id"),
        )
        assertEquals(items.size, shelfItemKeys(items).toSet().size)
    }
}
