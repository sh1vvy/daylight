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
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
    suspend fun read(scope: String?): List<CollectionSnapshot> = scope?.let { cache?.read(it) }.orEmpty()
    suspend fun save(scope: String?, snapshot: CollectionSnapshot) { safeWrite { scope?.let { cache?.save(it, snapshot) } } }
    suspend fun played(scope: String?, id: String) { safeWrite { scope?.let { cache?.played(it, id) } } }
    suspend fun removeScope(scope: String?) { safeWrite { scope?.let { cache?.removeScope(it) } } }
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

/** All parsing/writing runs off Main, serialized; two warm identities and 24 MiB on disk. */
internal class CollectionMetadataCache(
    private val directory: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val maxBytes: Long = 24L * 1024 * 1024,
) {
    private val mutex = Mutex()
    private val memoryLock = Any()
    private val memory = LinkedHashMap<String, List<CollectionSnapshot>>(4, 0.75f, true)
    private val json = Json { ignoreUnknownKeys = true }
    private val _changes = MutableStateFlow(0L)
    val changes = _changes.asStateFlow()

    fun peek(scope: String, id: String): CollectionSnapshot? = synchronized(memoryLock) {
        memory[scope]?.firstOrNull { it.browseId == id }
    }
    suspend fun read(scope: String): List<CollectionSnapshot> = withContext(Dispatchers.IO) {
        mutex.withLock { readLocked(scope) }
    }
    private fun readLocked(scope: String): List<CollectionSnapshot> {
        synchronized(memoryLock) { memory[scope]?.let { return it } }
        val file = file(scope)
        val result = if (!file.isFile || file.length() > MAX_FILE_BYTES) emptyList() else runCatching {
            json.decodeFromString<StoredCollections>(file.readText()).takeIf { it.version == 1 }
                ?.collections?.take(MAX_COLLECTIONS)?.map { it.restore() }.orEmpty()
        }.getOrDefault(emptyList())
        remember(scope, result)
        return result
    }
    suspend fun save(scope: String, snapshot: CollectionSnapshot) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val existing = readLocked(scope)
            val prior = existing.firstOrNull { it.browseId == snapshot.browseId }
            // A background refresh must not forget a play recorded while it was loading.
            val next = snapshot.copy(lastPlayedAt = maxOf(snapshot.lastPlayedAt, prior?.lastPlayedAt ?: 0))
            persistLocked(scope, (listOf(next) + existing.filterNot { it.browseId == next.browseId })
                .sortedByDescending { maxOf(it.lastPlayedAt, it.updatedAt) }.take(MAX_COLLECTIONS), next.browseId)
        }
    }
    suspend fun played(scope: String, id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val existing = readLocked(scope)
            val item = existing.firstOrNull { it.browseId == id } ?: return@withLock
            // Pauses, resume and every next song in the same playlist are not new shelf entries.
            if (now() - item.lastPlayedAt in 0 until 60_000) return@withLock
            persistLocked(scope, existing.map { if (it.browseId == id) it.copy(lastPlayedAt = now()) else it }, id)
        }
    }
    suspend fun removeScope(scope: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            synchronized(memoryLock) { memory.remove(scope) }
            file(scope).delete()
            _changes.value++
        }
    }
    private fun persistLocked(scope: String, snapshots: List<CollectionSnapshot>, retainedId: String) {
        var retained = snapshots
        fun encode() = json.encodeToString(StoredCollections.serializer(), StoredCollections(collections = retained.map(StoredCollection::from))).toByteArray()
        var bytes = encode()
        val ceiling = minOf(MAX_FILE_BYTES, maxBytes)
        // Evict whole older collections, preserving every row of the collection being saved.
        while (bytes.size > ceiling && retained.size > 1) {
            val oldest = retained.filterNot { it.browseId == retainedId }.minByOrNull { maxOf(it.lastPlayedAt, it.updatedAt) } ?: break
            retained = retained.filterNot { it.browseId == oldest.browseId }
            bytes = encode()
        }
        if (bytes.size > ceiling) return // Never persist a silently truncated collection.
        directory.mkdirs()
        val destination = file(scope)
        val temporary = File(directory, destination.name + ".tmp")
        temporary.outputStream().use { it.write(bytes); it.flush() }
        try {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        remember(scope, retained)
        // Keep the just-written identity; evict oldest other bundles until the disk ceiling holds.
        val files = directory.listFiles { f -> f.extension == "json" }.orEmpty()
        var total = files.sumOf(File::length)
        files.filterNot { it == destination }.sortedBy(File::lastModified).forEach { old ->
            val length = old.length()
            if (total > maxBytes && old.delete()) {
                total -= length
                synchronized(memoryLock) { memory.keys.removeAll { file(it) == old } }
            }
        }
        _changes.value++
    }
    private fun remember(scope: String, data: List<CollectionSnapshot>) = synchronized(memoryLock) {
        memory[scope] = data
        while (memory.size > 2) memory.remove(memory.keys.first())
    }
    private fun file(scope: String) = File(directory, metadataDigest(scope) + ".json")
    companion object {
        private const val MAX_COLLECTIONS = 24
        private const val MAX_FILE_BYTES = 8L * 1024 * 1024
    }
}

@Serializable private data class StoredCollections(val version: Int = 1, val collections: List<StoredCollection>)
@Serializable private data class StoredCollection(
    val id: String, val title: String, val subtitle: String, val cover: String?,
    val creator: StoredCreator?, val songs: List<CatalogueSong>, val complete: Boolean,
    val updated: Long, val played: Long, val trackIds: List<String> = emptyList(),
) {
    fun restore() = CollectionSnapshot(id, title, subtitle, cover, creator?.restore(), songs.map { it.restore() }, complete, updated, played, trackIds)
    companion object {
        fun from(s: CollectionSnapshot) = StoredCollection(s.browseId, s.title, s.subtitle, s.thumbnailUrl,
            s.creator?.let(StoredCreator::from), s.songs.map(CatalogueSong::from), s.complete, s.updatedAt, s.lastPlayedAt, s.trackIds)
    }
}
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
