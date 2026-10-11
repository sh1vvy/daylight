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

/** A visible feed is a reading session: every refresh, including its tail, waits for the top. */
internal fun settledHomeShelves(current: List<HomeShelf>, incoming: List<HomeShelf>, atRestAtTop: Boolean): List<HomeShelf> =
    if (atRestAtTop || current.isEmpty()) incoming else current

/** Preserve YouTube's account-specific ranking and labels, with a bounded reading surface. */
internal fun prepareDaylightHome(shelves: List<HomeShelf>): List<HomeShelf> {
    val seen = HashSet<String>()
    val visibleTracks = HashSet<String>()
    return homeQuickPickShelves(shelves).asSequence().mapNotNull { shelf ->
        val items = shelf.items.filter { item ->
            // Keep unsupported podcasts and blank destinations out of this music-only screen.
            (item.videoId != null || item.browseId != null) &&
                item.browseId?.startsWith("MPSP") != true
        }.distinctBy { it.videoId ?: it.browseId }
            .filter { it.videoId == null || it.videoId !in visibleTracks }.take(12)
        items.mapNotNullTo(visibleTracks) { it.videoId }
        if (items.isEmpty()) null else {
            val signature = items.map { it.videoId ?: it.browseId }.sortedBy { it }.joinToString("|")
            if (!seen.add(signature)) null else shelf.copy(items = items, subtitle = "")
        }
    }.take(12).toList()
}
