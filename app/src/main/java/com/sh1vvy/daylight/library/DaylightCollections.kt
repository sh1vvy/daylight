/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.library

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.library.CollectionMetadataStore
import com.music.bitchord.data.library.CollectionSnapshot
import com.music.bitchord.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CollectionContext(val identity: String?, val language: String, val sourceRevision: Long, val id: String) {
    val storageScope: String get() = "youtube:${identity ?: "guest"}"
}
data class CollectionHeader(val title: String, val subtitle: String, val artwork: String?, val creator: PlaylistCreator?,
    val owned: Boolean?, val library: LibraryState?, val description: String?, val sections: List<HomeShelf>)

/** Provider/model bridge; all paging and reusable-list ownership live in the independent core. */
class DaylightCollections(private val scope: CoroutineScope, private val currentIdentity: () -> String?) {
    private val edits = Mutex()
    private fun write(block: suspend () -> Unit) = scope.launch(start = CoroutineStart.UNDISPATCHED) { edits.withLock { block() } }
    private val records = CollectionRepository<CollectionContext, CollectionHeader, Song>(scope,
        first = { key ->
            val page = YtMusicRepository.browseSongs(key.id).getOrThrow()
            CollectionBatch(CollectionHeader(page.header?.title.orEmpty(), page.header?.subtitle.orEmpty(), page.header?.thumbnailUrl,
                page.creator, page.owned, page.library, page.description, page.sections), page.songs, page.continuation, page.suggested)
        },
        next = { _, token -> YtMusicRepository.moreSongs(token).getOrThrow().let { CollectionBatch(null, it.songs, it.continuation, it.suggested) } },
        read = { key -> CollectionMetadataStore.readCollection(key.storageScope, key.id)?.takeIf { it.language == key.language }?.let { saved ->
            CollectionListing(CollectionHeader(saved.title, saved.subtitle, saved.thumbnailUrl, saved.creator, saved.owned, saved.library,
                saved.description, saved.sections), saved.songs, saved.suggestions, saved.complete, saved.updatedAt)
        } },
        save = { key, value, current ->
            val header = value.header
            CollectionMetadataStore.save(key.storageScope, CollectionSnapshot(key.id, header?.title.orEmpty(), header?.subtitle.orEmpty(),
                header?.artwork, header?.creator, value.rows, value.complete, value.checkedAt,
                description = header?.description, owned = header?.owned, suggestions = value.suggestions, sections = header?.sections.orEmpty(), language = key.language,
                library = header?.library)) {
                current() && key.identity == currentIdentity() && key.sourceRevision == Innertube.responseCacheScope
            }
        }, rowKey = { it.setVideoId ?: it.videoId }, recordingKey = { it.videoId })

    fun key(id: String) = CollectionContext(currentIdentity(), Innertube.currentLanguage, Innertube.responseCacheScope, id)
    fun open(key: CollectionContext) = records.open(key)
    fun release(key: CollectionContext) = records.release(key)
    suspend fun songs(key: CollectionContext): List<Song> {
        try {
            val state = records.open(key).first { !it.loading }
            val listing = state.listing
            if (listing?.complete == true) return listing.rows
            throw state.failure ?: IllegalStateException("Collection is incomplete")
        } finally { records.release(key) }
    }
    fun clear() = records.clear()
    private fun edit(id: String, warm: (CollectionListing<CollectionHeader, Song>) -> CollectionListing<CollectionHeader, Song>,
                     stored: (CollectionSnapshot) -> CollectionSnapshot) {
        val key = key(id)
        val edited = records.edit({ it.id == id && it.identity == key.identity }, warm)
        if (key !in edited) write {
            CollectionMetadataStore.edit(key.storageScope, id, { key.identity == currentIdentity() && key.sourceRevision == Innertube.responseCacheScope }) {
                stored(it).copy(updatedAt = System.currentTimeMillis())
            }
        }
    }
    fun rename(id: String, title: String) = edit(id, { it.copy(header = it.header?.copy(title = title)) }, { it.copy(title = title) })
    fun append(id: String, song: Song) = edit(id, { listing ->
        if (song.setVideoId != null && listing.rows.any { it.setVideoId == song.setVideoId }) listing
        else listing.copy(rows = listing.rows + song, suggestions = listing.suggestions.filterNot { it.videoId == song.videoId })
    }, { listing ->
        if (song.setVideoId != null && listing.songs.any { it.setVideoId == song.setVideoId }) listing
        else listing.copy(songs = listing.songs + song, suggestions = listing.suggestions.filterNot { it.videoId == song.videoId })
    })
    fun remove(id: String, occurrence: String) = edit(id, { it.copy(rows = it.rows.filterNot { it.setVideoId == occurrence }) },
        { it.copy(songs = it.songs.filterNot { it.setVideoId == occurrence }) })
    fun reorder(id: String, songs: List<Song>) = edit(id, { it.copy(rows = songs) }, { it.copy(songs = songs) })
    fun saved(id: String, saved: Boolean) = edit(id, { it.copy(header = it.header?.let { header -> header.copy(library = header.library?.copy(saved = saved)) }) },
        { it.copy(library = it.library?.copy(saved = saved)) })
    fun delete(id: String) {
        val key = key(id)
        records.invalidate { it.id == id && it.identity == key.identity }
        write { CollectionMetadataStore.remove(key.storageScope, id) }
    }
}
