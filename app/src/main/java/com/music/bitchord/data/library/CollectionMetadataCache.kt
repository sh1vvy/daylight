/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-10 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.data.library

import android.content.Context
import com.music.bitchord.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/** Catalogue rows only: never audio bytes, stream URLs, account cookies or queue state. */
data class CollectionSnapshot(
    val browseId: String,
    val title: String,
    val subtitle: String = "",
    val thumbnailUrl: String? = null,
    val creator: PlaylistCreator? = null,
    val songs: List<Song>,
    val complete: Boolean,
    val updatedAt: Long,
    val lastPlayedAt: Long = 0,
    /** Spotify recording ids stay paired with their matches, including repeated entries. */
    val trackIds: List<String> = emptyList(),
    val description: String? = null,
    val owned: Boolean? = null,
    val suggestions: List<Song> = emptyList(),
    val sections: List<HomeShelf> = emptyList(),
    val language: String? = null,
    val library: LibraryState? = null,
) {
    fun isFresh(now: Long = System.currentTimeMillis()) = complete && now - updatedAt in 0 until 300_000L
    fun shelfItem() = ShelfItem(title, subtitle, thumbnailUrl, videoId = null, browseId = browseId)
}

object CollectionMetadataStore {
    private var cache: CollectionMetadataCache? = null
    fun init(context: Context) {
        if (cache == null) cache = CollectionMetadataCache(File(context.filesDir, "collection-metadata-v1"))
    }
    val changes get() = requireNotNull(cache).changes
    fun peek(scope: String?, id: String): CollectionSnapshot? = scope?.let { cache?.peek(it, id) }
    suspend fun read(scope: String?): List<CollectionSnapshot> = try {
        scope?.let { cache?.read(it) }.orEmpty()
    } catch (_: java.io.IOException) { emptyList() }
    suspend fun readCollection(scope: String?, id: String): CollectionSnapshot? = try {
        scope?.let { cache?.readCollection(it, id) }
    } catch (_: java.io.IOException) { null }
    suspend fun save(scope: String?, snapshot: CollectionSnapshot, isCurrent: () -> Boolean = { true }) { safeWrite { scope?.let { cache?.save(it, snapshot, isCurrent) } } }
    suspend fun played(scope: String?, id: String) { safeWrite { scope?.let { cache?.played(it, id) } } }
    suspend fun removeScope(scope: String?) { safeWrite { scope?.let { cache?.removeScope(it) } } }
    suspend fun edit(scope: String, id: String, current: () -> Boolean, transform: (CollectionSnapshot) -> CollectionSnapshot) {
        safeWrite { cache?.edit(scope, id, current, transform) }
    }
    suspend fun remove(scope: String, id: String) { safeWrite { cache?.remove(scope, id) } }
    private suspend fun safeWrite(block: suspend () -> Unit) {
        try { block() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: java.io.IOException) { /* A full/unavailable disk must not interrupt playback or browsing. */ }
    }
}

/** Provider and identity partition the cache. The cookie itself never reaches a file or log. */
fun spotifyMetadataScope(cookie: String): String? = cookie.takeIf(String::isNotBlank)?.let { "spotify:${metadataDigest(it)}" }
fun likedMetadataScope(listener: String?): String? = listener?.let { "youtube:$it" }
private fun metadataDigest(value: String) = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

/** Transitional adapter: existing callers/models now use Daylight's independent storage engine. */
internal class CollectionMetadataCache(
    private val directory: File,
    private val now: () -> Long = System::currentTimeMillis,
    maxBytes: Long = 24L * 1024 * 1024,
) {
    private val edits = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val records = com.sh1vvy.daylight.library.SnapshotCache(
        File(directory, "records-v2"),
        object : com.sh1vvy.daylight.library.SnapshotCodec<CollectionSnapshot> {
            override fun encode(value: CollectionSnapshot) = json.encodeToString(StoredCollection.serializer(), StoredCollection.from(value)).toByteArray(Charsets.UTF_8)
            override fun decode(bytes: ByteArray) = json.decodeFromString(StoredCollection.serializer(), bytes.toString(Charsets.UTF_8)).restore()
            override fun weight(value: CollectionSnapshot) = value.songs.size + value.suggestions.size + value.sections.sumOf { it.items.size }
        },
        com.sh1vvy.daylight.library.CacheBudget(diskBytes = maxBytes),
        preferredCollections = setOf("VLLM"),
    )
    private val _changes = MutableStateFlow(0L)
    val changes = _changes.asStateFlow()
    private fun key(scope: String, id: String) = com.sh1vvy.daylight.library.SnapshotKey(scope, id)

    fun peek(scope: String, id: String): CollectionSnapshot? = records.peek(key(scope, id))

    suspend fun read(scope: String): List<CollectionSnapshot> = withContext(Dispatchers.IO) {
        edits.withLock { load(scope) }
    }

    suspend fun readCollection(scope: String, id: String): CollectionSnapshot? = withContext(Dispatchers.IO) {
        edits.withLock { one(scope, id) }
    }

    private suspend fun one(scope: String, id: String): CollectionSnapshot? {
        if (File(directory, metadataDigest(scope) + ".json").isFile) load(scope)
        return records.read(key(scope, id))
    }

    /** One-time conversion; keep the original file if any whole collection cannot be retained. */
    private suspend fun load(scope: String): List<CollectionSnapshot> {
        val current = records.readAccount(scope)
        val legacy = File(directory, metadataDigest(scope) + ".json")
        if (!legacy.isFile || legacy.length() > 8L * 1024 * 1024) return ordered(current)
        val previous = try {
            json.decodeFromString<StoredCollections>(legacy.readText()).takeIf { it.version == 1 }
                ?.collections?.take(24)?.map { it.restore() }.orEmpty()
        } catch (_: IllegalArgumentException) { emptyList() }
        val present = current.mapTo(HashSet()) { it.browseId }
        var transferred = true
        for (old in previous) if (old.browseId !in present) {
            if (!records.write(key(scope, old.browseId), old)) transferred = false
        }
        if (transferred) legacy.delete()
        return ordered(records.readAccount(scope))
    }

    suspend fun save(scope: String, snapshot: CollectionSnapshot, isCurrent: () -> Boolean = { true }) = withContext(Dispatchers.IO) {
        edits.withLock {
            if (!isCurrent()) return@withLock
            val previous = one(scope, snapshot.browseId)
            val metadata = StoredCollection.from(snapshot).restore()
            val merged = metadata.copy(lastPlayedAt = maxOf(metadata.lastPlayedAt, previous?.lastPlayedAt ?: 0))
            if (merged != previous && records.write(key(scope, merged.browseId), merged, isCurrent)) _changes.value++
        }
    }

    suspend fun played(scope: String, id: String) = withContext(Dispatchers.IO) {
        edits.withLock {
            val previous = one(scope, id) ?: return@withLock
            val time = now()
            if (time - previous.lastPlayedAt in 0 until 60_000L) return@withLock
            if (records.write(key(scope, id), previous.copy(lastPlayedAt = time))) _changes.value++
        }
    }

    suspend fun edit(scope: String, id: String, current: () -> Boolean, transform: (CollectionSnapshot) -> CollectionSnapshot) = withContext(Dispatchers.IO) {
        edits.withLock {
            if (!current()) return@withLock
            val previous = one(scope, id) ?: return@withLock
            val updated = transform(previous)
            if (updated != previous && records.write(key(scope, id), updated, current)) _changes.value++
        }
    }

    suspend fun remove(scope: String, id: String) = withContext(Dispatchers.IO) {
        edits.withLock { records.remove(key(scope, id)); _changes.value++ }
    }

    suspend fun removeScope(scope: String) = withContext(Dispatchers.IO) {
        edits.withLock {
            records.removeAccount(scope)
            val legacy = File(directory, metadataDigest(scope) + ".json")
            if (legacy.exists() && !legacy.delete()) throw java.io.IOException("Metadata removal failed")
            _changes.value++
        }
    }

    private fun ordered(values: List<CollectionSnapshot>) = values.sortedByDescending { maxOf(it.lastPlayedAt, it.updatedAt) }
}

@Serializable private data class StoredCollections(val version: Int = 1, val collections: List<StoredCollection>)
@Serializable private data class StoredCollection(
    val id: String, val title: String, val subtitle: String, val cover: String?,
    val creator: StoredCreator?, val songs: List<CatalogueSong>, val complete: Boolean,
    val updated: Long, val played: Long, val trackIds: List<String> = emptyList(),
    val description: String? = null, val owned: Boolean? = null,
    val suggestions: List<CatalogueSong> = emptyList(),
    val sections: List<com.sh1vvy.daylight.library.LibrarySection> = emptyList(),
    val language: String? = null,
    val library: StoredLibrary? = null,
) {
    fun restore() = CollectionSnapshot(id, title, subtitle, cover, creator?.restore(), songs.map { it.restore() }, complete, updated, played, trackIds,
        description, owned, suggestions.map { it.restore() }, com.sh1vvy.daylight.library.LibraryOverviewStore.page(
            com.sh1vvy.daylight.library.LibraryOverview(sections, 0)).shelves, language, library?.let { LibraryState(it.id, it.saved) })
    companion object {
        fun from(s: CollectionSnapshot) = StoredCollection(s.browseId, s.title, s.subtitle, s.thumbnailUrl,
            s.creator?.let(StoredCreator::from), s.songs.map(CatalogueSong::from), s.complete, s.updatedAt, s.lastPlayedAt, s.trackIds,
            s.description, s.owned, s.suggestions.map(CatalogueSong::from), s.sections.map { section ->
                com.sh1vvy.daylight.library.LibrarySection(section.title, section.items.map { card ->
                    com.sh1vvy.daylight.library.LibraryCard(card.title, card.subtitle, card.thumbnailUrl, card.browseId, card.videoId)
                }, section.subtitle, section.moreBrowseId, section.moreParams)
            }, s.language, s.library?.let { StoredLibrary(it.playlistId, it.saved) })
    }
}
@Serializable private data class StoredLibrary(val id: String, val saved: Boolean)
@Serializable private data class StoredCreator(val name: String, val id: String?, val cover: String?, val provider: String) {
    fun restore() = PlaylistCreator(name, id, cover, CreatorProvider.valueOf(provider))
    companion object { fun from(c: PlaylistCreator) = StoredCreator(c.name, c.browseId, c.thumbnailUrl, c.provider.name) }
}
@Serializable private data class CatalogueArtist(val name: String, val id: String?)
@Serializable private data class CatalogueSong(
    val id: String, val title: String, val artist: String, val cover: String?, val duration: String?,
    val artistId: String?, val artists: List<CatalogueArtist>, val albumId: String?, val album: String?,
    val video: Boolean, val videoOrigin: Boolean, val entry: String?, val explicit: Boolean?,
) {
    fun restore() = Song(id, title, artist, cover, duration, artistId, artists.map { ArtistRef(it.name, it.id) },
        albumId, album, video, videoOrigin, entry, isExplicit = explicit)
    companion object {
        fun from(s: Song) = CatalogueSong(s.videoId, s.title, s.artist, s.thumbnailUrl, s.durationText,
            s.artistId, s.artists.map { CatalogueArtist(it.name, it.browseId) }, s.albumId, s.albumName,
            s.isVideo, s.isVideoOrigin, s.setVideoId, s.isExplicit)
    }
}
