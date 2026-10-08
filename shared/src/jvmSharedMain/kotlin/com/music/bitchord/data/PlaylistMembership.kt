package com.music.bitchord.data

/** One page is discarded before the next is read; membership needs no full song collection. */
internal data class PlaylistMembershipPage(val videoIds: List<String>, val continuation: String?)

internal suspend fun scanPlaylistMembership(
    videoId: String,
    first: suspend () -> PlaylistMembershipPage,
    next: suspend (String) -> PlaylistMembershipPage,
    maxPages: Int = 500,
): Boolean {
    require(videoId.isNotBlank())
    var page = first()
    var pagesRead = 1
    val seenTokens = HashSet<String>()
    while (true) {
        if (videoId in page.videoIds) return true
        val token = page.continuation ?: return false
        check(pagesRead < maxPages && seenTokens.add(token)) { "Playlist listing is incomplete" }
        page = next(token)
        pagesRead++
    }
}
