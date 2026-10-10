package com.music.bitchord.ui

import com.music.bitchord.data.model.DetailPage
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.CreatorProfilePage
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.PlaylistCreator
import com.music.bitchord.data.model.Song

/** Each navigation visit owns a viewport, even when its browse id is already on the stack. */
internal fun DetailPage.detailRouteKey(): String = "detail:$instanceId:$browseId"

/** The originating playlist survives loading/errors and is shown once after a profile fetch. */
internal fun CreatorProfilePage.creatorCollections(): List<BrowseItem> =
    (listOf(currentPlaylist) + (playlists as? UiState.Success)?.data.orEmpty())
        .distinctBy { it.browseId }

internal sealed interface HeaderCreditLink {
    data class Creator(val creator: PlaylistCreator) : HeaderCreditLink
    data class Artist(val browseId: String, val name: String) : HeaderCreditLink
}

/** A playlist author is not the recording artist of whichever song happens to be first. */
internal fun DetailPage.headerCreditLink(firstSong: Song?): HeaderCreditLink? = when (type) {
    BrowseType.PLAYLIST -> creator?.let(HeaderCreditLink::Creator)
    BrowseType.ALBUM -> firstSong?.let { song ->
        val credited = song.artists.firstOrNull { !it.browseId.isNullOrBlank() }
        (credited?.browseId ?: song.artistId)?.takeIf(String::isNotBlank)?.let {
            HeaderCreditLink.Artist(it, credited?.name ?: song.artist)
        }
    }
    else -> null
}
