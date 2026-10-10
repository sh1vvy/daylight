package com.music.bitchord.ui

import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.data.model.playlistCreationOrderKey

/** Same successful-create/add history as Library. Preserve provider order for older choices. */
internal fun recentPlaylistChoices(playlists: List<UserPlaylist>, recentIds: List<String>): List<UserPlaylist> {
    val ranks = recentIds.map(::playlistCreationOrderKey).distinct().withIndex().associate { it.value to it.index }
    return playlists.distinctBy { it.playlistId }.sortedBy {
        ranks[playlistCreationOrderKey(it.playlistId)] ?: Int.MAX_VALUE
    }
}
