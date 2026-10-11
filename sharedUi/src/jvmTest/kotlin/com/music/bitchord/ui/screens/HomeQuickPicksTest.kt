/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-10 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

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
    @Test fun lateRefreshCannotInsertOrReplaceRowsUnderAScrollingFinger() {
        val old = listOf(HomeShelf("Quick picks", listOf(track("old"))), HomeShelf("Listen again", listOf(track("listen"))))
        val fresh = listOf(HomeShelf("Quick picks", listOf(track("new"))), HomeShelf("Inserted", listOf(track("insert"))), old.last())
        assertEquals(old, stableHomeShelves(old, fresh, false))
        assertEquals(fresh, stableHomeShelves(old, fresh, true))
    }
    @Test fun paginationExtendsTheBottomWithoutReplacingVisibleCards() {
        val old = listOf(HomeShelf("Quick picks", listOf(track("old"))), HomeShelf("Extra", listOf(track("extra"))))
        val tail = HomeShelf("More", listOf(track("tail")))
        val incoming = listOf(old.first().copy(items = listOf(track("new"))), old.last(), tail)
        assertEquals(old + tail, stableHomeShelves(old, incoming, false))
        assertEquals(incoming, stableHomeShelves(emptyList(), incoming, false))
    }

    @Test fun generatedCollectionShelfDoesNotBlockOrJumpOverNewPages() {
        val picks = HomeShelf("Quick picks", listOf(track("one")))
        val collections = HomeShelf("Back in rotation", listOf(ShelfItem("Album", "", null, null, "MPREone")))
        val more = HomeShelf("More", listOf(track("tail")))
        val old = listOf(picks, collections)
        val incoming = listOf(picks, more, collections)
        assertTrue(homeTailCompatible(old, incoming))
        assertEquals(old + more, stableHomeShelves(old, incoming, false))
    }

}
