package com.music.bitchord.ui.components

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem

/**
 * Keeps lazy item identity when a feed inserts a shelf or a playlist is sorted.
 * Providers can repeat the same track or shelf, so an occurrence suffix keeps
 * those legitimate duplicates distinct without tying every key to its position.
 */
fun <T> stableItemKeys(items: List<T>, identity: (T) -> String): List<String> {
    val occurrences = HashMap<String, Int>()
    return items.map { item ->
        val id = identity(item)
        val occurrence = occurrences[id] ?: 0
        occurrences[id] = occurrence + 1
        "$id:$occurrence"
    }
}

fun shelfItemKeys(items: List<ShelfItem>): List<String> = stableItemKeys(items) { item ->
    when {
        item.videoId != null -> "track:${item.videoId}"
        item.browseId != null -> "browse:${item.browseId}"
        else -> "item:${item.title.length}:${item.title}:${item.subtitle}"
    }
}

fun homeShelfKeys(shelves: List<HomeShelf>): List<String> = stableItemKeys(shelves) { shelf ->
    shelf.moreBrowseId?.let { "browse:$it" } ?: "shelf:${shelf.title}"
}
