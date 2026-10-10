package com.music.bitchord.data.library

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem

/** Keep the feed's own shelf and insert real played collections once, with their provider routes. */
fun withPlayedCollections(shelves: List<HomeShelf>, played: List<ShelfItem>): List<HomeShelf> {
    if (played.isEmpty()) return shelves
    val additions = played.distinctBy { it.browseId }
    val index = shelves.indexOfFirst { it.title.equals("Listen again", true) || it.title.equals("Nghe lại", true) || it.title == "もう一度聴く" }
    return if (index >= 0) shelves.toMutableList().also { list ->
        val shelf = list[index]
        val ids = additions.mapTo(HashSet()) { it.browseId }
        list[index] = shelf.copy(items = additions + shelf.items.filterNot { it.browseId in ids })
    } else {
        val afterRecents = shelves.indexOfFirst { it.title.equals("Recents", true) || it.title.equals("Recently played", true) } + 1
        shelves.toMutableList().also { it.add(afterRecents, HomeShelf("Listen again", additions)) }
    }
}
