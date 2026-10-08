package com.music.bitchord.ui

import com.music.bitchord.data.model.HomeFeed
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.withoutRepeatsOf
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.Locale

/** Keep the visible feed intact until the core and its leading history shelf settle. */
internal suspend fun refreshedHomeFeed(
    previous: List<HomeShelf>,
    loadFeed: suspend () -> Result<HomeFeed>,
    loadRecents: (suspend () -> Result<HomeShelf?>)?,
): Result<HomeFeed> = coroutineScope {
    val recent = loadRecents?.let { async { it() } }
    val feed = loadFeed()
    val recentResult = recent?.await()
    feed.map { refreshed ->
        val leading = when {
            recentResult == null -> null
            recentResult.isSuccess -> recentResult.getOrNull()
            else -> previous.firstOrNull { it.title.equals("Recents", ignoreCase = true) }
        }
        val shelves = if (recentResult == null) refreshed.shelves else {
            refreshed.shelves.filterNot { it.title.equals("Recents", ignoreCase = true) }
        }
        refreshed.copy(shelves = (listOfNotNull(leading) + shelves)
            .distinctBy { it.title.lowercase(Locale.ROOT) }
            .withoutRepeatsOf(emptyList()))
    }
}
