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

/** Refreshes may replace a feed at its top; paging may only extend its existing tail. */
internal fun stableHomeShelves(current: List<HomeShelf>, incoming: List<HomeShelf>, atRestAtTop: Boolean): List<HomeShelf> {
    if (atRestAtTop || current.isEmpty()) return incoming
    if (!homeTailCompatible(current, incoming)) return current
    val keys = com.music.bitchord.ui.components.homeShelfKeys(current).toHashSet()
    return current + incoming.zip(com.music.bitchord.ui.components.homeShelfKeys(incoming)).filter { it.second !in keys }.map { it.first }
}

internal fun homeTailCompatible(current: List<HomeShelf>, incoming: List<HomeShelf>): Boolean {
    // The generated collection shelf follows the feed; keep its existing position when paging.
    fun keys(shelves: List<HomeShelf>) = com.music.bitchord.ui.components.homeShelfKeys(shelves.filterNot { it.title == "Back in rotation" })
    val before = keys(current)
    return keys(incoming).take(before.size) == before
}
