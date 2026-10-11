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
        assertEquals(old, settledHomeShelves(old, fresh, false))
        assertEquals(fresh, settledHomeShelves(old, fresh, true))
    }
    @Test fun newTailWaitsUntilReturnToTopAsWell() {
        val old = listOf(HomeShelf("Quick picks", listOf(track("old"))))
        val incoming = old + HomeShelf("More", listOf(track("new")))
        assertEquals(old, settledHomeShelves(old, incoming, false))
        assertEquals(incoming, settledHomeShelves(old, incoming, true))
    }

    @Test fun preparedHomeKeepsProviderOrderAndBoundsWorkWithoutUnsupportedItems() {
        val shelves = (1..50).map { index -> HomeShelf("Personal $index", (1..50).map {
            ShelfItem("Album $index-$it", "Artist", null, null, "MPRE-$index-$it")
        }, "Long description") }
        val result = prepareDaylightHome(shelves)
        assertEquals((1..12).map { "Personal $it" }, result.map { it.title })
        assertTrue(result.all { it.items.size == 12 && it.subtitle.isEmpty() })
        val duplicate = result.first().copy(title = "Duplicate")
        assertEquals(1, prepareDaylightHome(listOf(result.first(), duplicate)).size)
        assertTrue(prepareDaylightHome(listOf(HomeShelf("Shows", listOf(ShelfItem("Podcast", "", null, null, "MPSPshow"))))).isEmpty())
    }

    @Test fun songsAlreadyInQuickPicksDoNotClutterOtherShelves() {
        val picks = (1..12).map { track("$it") }
        val more = (1..20).map { track("$it") }
        val result = prepareDaylightHome(listOf(HomeShelf("Quick picks", picks), HomeShelf("Recommended", more)))
        assertEquals(listOf("13", "14", "15", "16", "17", "18", "19", "20"), result.last().items.map { it.videoId })
    }

}
