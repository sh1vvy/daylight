/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.library

import android.content.Context
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.model.UserPlaylist
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable private data class EditablePlaylistRecord(val id: String, val title: String, val subtitle: String, val cover: String?)
@Serializable private data class EditablePlaylistList(val rows: List<EditablePlaylistRecord>, val checkedAt: Long)

/** Persist only the provider's verified editable directory, never infer ownership from Library cards. */
object DaylightPlaylistDirectoryStorage {
    private var cache: SnapshotCache<EditablePlaylistList>? = null
    @Synchronized fun init(context: Context) {
        if (cache != null) return
        val json = Json { ignoreUnknownKeys = true }
        cache = SnapshotCache(File(context.filesDir, "daylight-editable-playlists-v1"), object : SnapshotCodec<EditablePlaylistList> {
            override fun encode(value: EditablePlaylistList) = json.encodeToString(EditablePlaylistList.serializer(), value).toByteArray()
            override fun decode(bytes: ByteArray) = json.decodeFromString(EditablePlaylistList.serializer(), bytes.toString(Charsets.UTF_8))
            override fun weight(value: EditablePlaylistList) = value.rows.size
        }, CacheBudget(diskBytes = 1024 * 1024, entryBytes = 256 * 1024, memoryRows = 2_000, memoryEntries = 4, entriesPerAccount = 3, diskEntries = 24))
    }
    private fun address(key: CollectionContext) = SnapshotKey("youtube:${key.identity ?: "guest"}", "editable:${key.language}")
    suspend fun read(key: CollectionContext): CollectionListing<Unit, UserPlaylist>? = try {
        cache?.read(address(key))?.let { saved -> CollectionListing(Unit, saved.rows.map { UserPlaylist(it.id, it.title, it.subtitle, it.cover) }, complete = true, checkedAt = saved.checkedAt) }
    } catch (_: IOException) { null }
    suspend fun save(key: CollectionContext, value: CollectionListing<Unit, UserPlaylist>, current: () -> Boolean) {
        try { cache?.write(address(key), EditablePlaylistList(value.rows.map { EditablePlaylistRecord(it.playlistId, it.title, it.subtitle, it.thumbnailUrl) }, value.checkedAt), current) }
        catch (_: IOException) { }
    }
    suspend fun remove(identity: String) { try { cache?.removeAccount("youtube:$identity") } catch (_: IOException) { } }
    suspend fun invalidate(key: CollectionContext) { try { cache?.remove(address(key)) } catch (_: IOException) { } }
}

class DaylightPlaylistDirectory(private val scope: CoroutineScope, context: Context, private val identity: () -> String?) {
    init { DaylightPlaylistDirectoryStorage.init(context) }
    private val repository = CollectionRepository<CollectionContext, Unit, UserPlaylist>(scope,
        first = { CollectionBatch(Unit, YtMusicRepository.userPlaylists().getOrThrow(), null) },
        next = { _, _ -> error("Editable directory is not paginated") },
        read = DaylightPlaylistDirectoryStorage::read,
        save = { key, value, current -> DaylightPlaylistDirectoryStorage.save(key, value) {
            current() && key.identity == identity() && key.sourceRevision == Innertube.responseCacheScope
        } }, rowKey = { it.playlistId }, recordingKey = { it.playlistId }, maxWarmEntries = 2, maxWarmRows = 2_000)
    fun key() = CollectionContext(identity(), Innertube.currentLanguage, Innertube.responseCacheScope, "editable")
    fun open(key: CollectionContext) = repository.open(key)
    fun release(key: CollectionContext) = repository.release(key)
    fun clear() = repository.clear()
    fun replace(rows: List<UserPlaylist>) {
        val key = key()
        val edited = repository.edit({ it.identity == key.identity }) { it.copy(rows = rows) }
        if (key !in edited) scope.launch(Dispatchers.IO) { DaylightPlaylistDirectoryStorage.invalidate(key) }
    }
}
