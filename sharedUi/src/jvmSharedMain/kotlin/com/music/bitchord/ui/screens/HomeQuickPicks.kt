package com.music.bitchord.ui.screens

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.homeQuickMix

internal const val QUICK_PICKS_TITLE = "Quick picks"

/** Reuse loaded and cached track pools; rendering does not start network requests. */
internal fun homeQuickPickShelves(shelves: List<HomeShelf>, recommendedTracks: List<ShelfItem> = emptyList()): List<HomeShelf> {
    val recent = shelves.filter { it.title.equals("Recents", true) }
    val recommended = shelves.filterNot { it in recent || it.title.contains("listen again", true) }
    val dedicated = recommended.filter {
        it.title.contains("quick picks", true) || it.title.contains("speed dial", true)
    }
    val picks = homeQuickMix(shelves, recommendedTracks)
    val recentCollections = (recent + dedicated).flatMap { it.items }.filter { it.videoId == null }
        .distinctBy { it.browseId ?: it.title }
    if (picks.isEmpty()) return shelves.map { if (it in recent) it.copy(title = "Back in rotation") else it }
    // Keep album/playlist shelves intact; omit a consumed all-track Quick picks shelf.
    val rest = shelves.filterNot { it in recent || it in dedicated }
    return (listOf(HomeShelf(QUICK_PICKS_TITLE, picks)) + rest +
        listOfNotNull(recentCollections.takeIf { it.isNotEmpty() }?.let { HomeShelf("Back in rotation", it) })).map { it.copy(subtitle = "") }
}
