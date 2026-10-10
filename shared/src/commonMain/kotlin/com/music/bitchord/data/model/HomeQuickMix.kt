package com.music.bitchord.data.model

fun HomeShelf.isRecentHomeShelf(): Boolean = title.equals("Recents", true) ||
    title.equals("Recently played", true) || title.contains("listen again", true)

fun HomeShelf.isQuickPicksHomeShelf(): Boolean = title.contains("quick picks", true) ||
    title.contains("speed dial", true)

fun homeRecentTracks(shelves: List<HomeShelf>): List<ShelfItem> = shelves
    .filter { it.isRecentHomeShelf() }.flatMap { it.items }
    .filter { !it.videoId.isNullOrBlank() }.distinctBy { it.videoId }.take(24)

fun homeDiscoveryTracks(shelves: List<HomeShelf>): List<ShelfItem> {
    val recentIds = homeRecentTracks(shelves).mapTo(HashSet()) { it.videoId }
    val discovery = shelves.filterNot { it.isRecentHomeShelf() }
    return (discovery.filter { it.isQuickPicksHomeShelf() } + discovery.filterNot { it.isQuickPicksHomeShelf() })
        .flatMap { it.items }.filter { !it.videoId.isNullOrBlank() && it.videoId !in recentIds }
        .distinctBy { it.videoId }.take(24)
}

/** Every three-row page offers a familiar track and two discoveries when available. */
fun homeQuickMix(shelves: List<HomeShelf>, supplemental: List<ShelfItem> = emptyList()): List<ShelfItem> {
    val familiar = homeRecentTracks(shelves)
    val familiarIds = familiar.mapTo(HashSet()) { it.videoId }
    val discoveries = (supplemental + homeDiscoveryTracks(shelves))
        .filter { !it.videoId.isNullOrBlank() && it.videoId !in familiarIds }.distinctBy { it.videoId }
    val result = ArrayList<ShelfItem>(12)
    var recent = 0
    var fresh = 0
    while (result.size < 12 && (recent < familiar.size || fresh < discoveries.size)) {
        if (recent < familiar.size) result += familiar[recent++]
        repeat(2) { if (result.size < 12 && fresh < discoveries.size) result += discoveries[fresh++] }
    }
    return result.take(12)
}
