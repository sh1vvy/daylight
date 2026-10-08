package com.music.bitchord.ui

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UserPlaylist
import kotlinx.coroutines.CancellationException

/** Checks are separate from writes so cancelling the duplicate prompt changes no playlists. */
data class PlaylistAddPlan(
    val song: Song,
    val playlists: List<UserPlaylist>,
    val duplicates: List<UserPlaylist>,
    internal val accountKey: String? = null,
)

data class PlaylistAddResult(val added: Int, val failed: Int)

internal suspend fun planPlaylistAdd(
    playlists: List<UserPlaylist>,
    song: Song,
    accountKey: String?,
    contains: suspend (UserPlaylist, Song) -> Boolean,
): PlaylistAddPlan {
    require(song.videoId.isNotBlank() && playlists.isNotEmpty())
    val destinations = playlists.distinctBy { it.playlistId }
    val duplicates = destinations.filter { contains(it, song) }
    return PlaylistAddPlan(song, destinations, duplicates, accountKey)
}

internal suspend fun executePlaylistAdd(
    plan: PlaylistAddPlan,
    allowDuplicates: Boolean,
    add: suspend (UserPlaylist, Song) -> Boolean,
): PlaylistAddResult {
    // Even a forgotten UI guard must not silently accept a duplicate.
    if (plan.duplicates.isNotEmpty() && !allowDuplicates) return PlaylistAddResult(0, 0)
    var added = 0
    var failed = 0
    for (playlist in plan.playlists) {
        val saved = try {
            add(playlist, plan.song)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        if (saved) added++ else failed++
    }
    return PlaylistAddResult(added, failed)
}
