/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord

import com.music.bitchord.data.model.*
import com.sh1vvy.daylight.library.*
import org.junit.Assert.*
import org.junit.Test

class LibraryOverviewStoreTest {
    @Test fun cachedCardsPreserveNavigationAndPartialRefreshOnlyReplacesVerifiedSections() {
        val card = LibraryCard("Album", "Artist", "https://art", "MPREalbum", null)
        val stored = LibraryOverview(listOf(LibrarySection("Albums", listOf(card), "saved", "feed", "params")), 1000)
        val cached = LibraryOverviewStore.page(stored)
        assertEquals("MPREalbum", cached.shelves.single().items.single().browseId)
        assertEquals("params", cached.shelves.single().moreParams)
        assertFalse(cached.likedLoaded)
        assertEquals(cached.shelves, LibraryOverviewStore.retainingFailedSections(
            LibraryPage(emptyList(), emptyList(), emptyList(), loadedShelfTitles = emptySet()), cached).shelves)
        assertTrue(LibraryOverviewStore.retainingFailedSections(
            LibraryPage(emptyList(), emptyList(), emptyList(), loadedShelfTitles = setOf("Albums")), cached).shelves.isEmpty())
        assertTrue(LibraryOverviewStore.isFresh(stored, 1001))
        assertFalse(LibraryOverviewStore.isFresh(stored, 61_001))
        assertFalse(LibraryOverviewStore.isFresh(stored, 999))
    }
}
