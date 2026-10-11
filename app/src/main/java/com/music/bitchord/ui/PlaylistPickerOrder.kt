/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-10 through 2026-10-10.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

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
