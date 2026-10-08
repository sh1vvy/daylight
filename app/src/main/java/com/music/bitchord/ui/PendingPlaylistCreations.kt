package com.music.bitchord.ui

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.data.model.playlistCreationOrderKey
import java.util.concurrent.atomic.AtomicLong

/** An older Library read cannot acknowledge or replace a newer read's result. */
internal class LatestLibraryRequest {
    private val generation = AtomicLong(0L)
    fun begin(): Long = generation.incrementAndGet()
    fun isCurrent(request: Long): Boolean = request == generation.get()
    fun invalidate() { generation.incrementAndGet() }
}

/** Keeps successful creates visible while YouTube's library feed catches up. */
internal class PendingPlaylistCreations {
    private var listener: String? = null
    private val playlists = linkedMapOf<String, UserPlaylist>()

    fun remember(identity: String?, playlist: UserPlaylist) {
        if (listener != identity) clear()
        listener = identity
        playlists[playlistCreationOrderKey(playlist.browseId)] = playlist
    }

    /** A provider card acknowledges the create; its authoritative metadata then wins. */
    fun mergeLibrary(identity: String?, page: LibraryPage): LibraryPage {
        if (listener != identity || playlists.isEmpty()) return page
        val shelf = page.shelves.firstOrNull { it.title == YtMusicRepository.PLAYLISTS_SHELF }
        val acknowledged = shelf?.items.orEmpty().mapNotNull { item ->
            item.browseId?.let(::playlistCreationOrderKey)
        }.toSet()
        acknowledged.forEach(playlists::remove)
        if (playlists.isEmpty()) return page
        val pending = playlists.values.toList().asReversed().map { it.libraryCard() }
        val merged = shelf?.copy(items = pending + shelf.items)
            ?: HomeShelf(YtMusicRepository.PLAYLISTS_SHELF, pending)
        return page.copy(shelves = if (shelf == null) listOf(merged) + page.shelves else {
            page.shelves.map { if (it === shelf) merged else it }
        })
    }

    /** The picker uses a different eventually-consistent endpoint from the Library feed. */
    fun mergeOwnPlaylists(identity: String?, provider: List<UserPlaylist>): List<UserPlaylist> {
        if (listener != identity || playlists.isEmpty()) return provider
        val known = provider.map { playlistCreationOrderKey(it.browseId) }.toSet()
        return playlists.values.toList().asReversed().filterNot {
            playlistCreationOrderKey(it.browseId) in known
        } + provider
    }

    fun rename(identity: String?, browseId: String, title: String) {
        if (listener != identity) return
        val key = playlistCreationOrderKey(browseId)
        playlists[key]?.let { playlists[key] = it.copy(title = title) }
    }

    fun remove(identity: String?, browseId: String) {
        if (listener == identity) playlists.remove(playlistCreationOrderKey(browseId))
    }

    fun clear() {
        playlists.clear()
        listener = null
    }

    private fun UserPlaylist.libraryCard() = ShelfItem(title, subtitle, thumbnailUrl, null, browseId)
}
