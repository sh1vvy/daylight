package com.music.bitchord.data

import com.music.bitchord.data.model.*
import kotlin.test.*
import org.junit.Test

class HomeQuickMixTest {
    private fun track(id: String) = ShelfItem(id, "Artist", null, id, null)
    @Test fun recentAndRecommendedTracksShareEveryPage() {
        val shelves = listOf(HomeShelf("Recents", (1..10).map { track("recent-$it") }),
            HomeShelf("Quick picks", (1..10).map { track("new-$it") }))
        assertEquals(listOf("recent-1", "new-1", "new-2", "recent-2", "new-3", "new-4"), homeQuickMix(shelves).take(6).map { it.videoId })
        assertEquals(12, homeQuickMix(shelves).size)
    }
    @Test fun albumOnlyRecommendationsUseTheSupplementalTrackPool() {
        val shelves = listOf(HomeShelf("Recents", listOf(track("old"))),
            HomeShelf("For you", listOf(ShelfItem("Album", "", null, null, "MPRE-1"))))
        assertEquals(listOf("old", "fresh", "new"), homeQuickMix(shelves, listOf(track("old"), track("fresh"), track("new"))).map { it.videoId })
    }
    @Test fun listenAgainIsHistoryRatherThanFreshDiscovery() {
        val shelves = listOf(HomeShelf("Listen again", listOf(track("old"))), HomeShelf("Recommendations", listOf(track("old"), track("new"))))
        assertEquals(listOf("new"), homeDiscoveryTracks(shelves).map { it.videoId })
        assertEquals(listOf("old", "new"), homeQuickMix(shelves).map { it.videoId })
    }
    @Test fun emptyDiscoveryKeepsHistoryUsableOffline() {
        assertEquals(12, homeQuickMix(listOf(HomeShelf("Recents", (1..30).map { track("$it") }))).size)
        assertTrue(homeQuickMix(emptyList()).isEmpty())
    }
    @Test fun historySeededDiscoveriesTakePriorityOverGenericFeedPicks() {
        val shelves = listOf(HomeShelf("Recents", listOf(track("recent"))), HomeShelf("Quick picks", listOf(track("generic"))))
        assertEquals(listOf("recent", "seeded", "generic"), homeQuickMix(shelves, listOf(track("seeded"))).map { it.videoId })
    }
}
