package com.music.bitchord.ui.screens

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import org.junit.Test
import kotlin.test.*

class HomeQuickPicksTest {
    private fun track(id: String) = ShelfItem("Track $id", "Artist", null, id, null)
    @Test fun recentsAndDiscoveryAreInterleavedWithoutDuplicateTracks() {
        val feed = listOf(HomeShelf("Recents", listOf(track("old"), track("same"))),
            HomeShelf("Recommendations", listOf(track("same"), track("new"))),
            HomeShelf("Quick picks", listOf(track("pick"), track("new"))))
        val result = homeQuickPickShelves(feed)
        assertEquals(listOf("old", "pick", "new", "same"), result.first().items.map { it.videoId })
        assertEquals(1, result.count { it.title == QUICK_PICKS_TITLE })
        assertFalse(result.any { it.title == "Recents" })
    }
    @Test fun collectionShelvesRemainNavigableAndPreviewIsBounded() {
        val album = ShelfItem("Album", "Artist", null, null, "MPREb-test")
        val result = homeQuickPickShelves(listOf(HomeShelf("Recents", listOf(album)),
            HomeShelf("Discovery", (1..100).map { track("$it") })))
        assertEquals(12, result.first().items.size)
        assertTrue(result.any { album in it.items })
    }
    @Test fun mixedQuickPicksKeepTheirCollectionsWithoutRepeatingTheSection() {
        val album = ShelfItem("Album", "Artist", null, null, "MPREb-test")
        val result = homeQuickPickShelves(listOf(HomeShelf("Quick picks", listOf(track("one"), album))))
        assertEquals(1, result.count { it.title == QUICK_PICKS_TITLE })
        assertEquals(listOf(album), result.last().items)
    }
    @Test fun anAlbumOnlyFeedKeepsItsCollections() {
        val playlist = ShelfItem("My playlist", "", null, null, "VLPLone")
        val result = homeQuickPickShelves(listOf(HomeShelf("Recents", listOf(playlist))))
        assertEquals(listOf(playlist), result.single().items)
        assertEquals("Back in rotation", result.single().title)
    }
}
