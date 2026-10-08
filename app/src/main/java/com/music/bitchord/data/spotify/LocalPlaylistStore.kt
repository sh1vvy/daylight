package com.music.bitchord.data.spotify

import android.content.Context
import android.util.Log
import com.music.bitchord.data.library.LibraryPlaylistOrderStore
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

data class LocalPlaylist(
    val id: String,
    val title: String,
    val songs: List<Song>,
) {
    val browseId: String get() = "local:playlist:$id"
}

object LocalPlaylistStore {
    private const val PREF_NAME = "bitchord_local_playlists"
    private const val KEY_PLAYLISTS = "playlists_json"

    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var writer: LocalPlaylistSnapshotWriter? = null
    private val collection = LocalPlaylistCollection { snapshot -> writer?.enqueue(snapshot) }
    val playlists = collection.playlists

    @Synchronized
    fun init(context: Context) {
        // AppSettings can be initialized again without replacing a snapshot
        // that is still being saved by the application's single writer.
        if (writer != null) return
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val loaded = prefs.getString(KEY_PLAYLISTS, null)?.let { raw ->
            runCatching { LocalPlaylistSnapshotCodec.decode(raw) }.getOrNull()
        }.orEmpty()
        collection.restore(loaded)
        writer = LocalPlaylistSnapshotWriter(persistenceScope) { snapshot ->
            val encoded = LocalPlaylistSnapshotCodec.encode(snapshot)
            // This is already on IO. Completing each disk write before taking
            // the next snapshot prevents an old save from replacing a new one.
            check(prefs.edit().putString(KEY_PLAYLISTS, encoded).commit())
        }
    }

    @Synchronized
    fun savePlaylist(title: String, songs: List<Song>): LocalPlaylist =
        collection.savePlaylist(title, songs).also { LibraryPlaylistOrderStore.recordCreated(it.browseId) }
    fun deletePlaylist(id: String) = collection.deletePlaylist(id)
    fun renamePlaylist(id: String, newTitle: String) = collection.renamePlaylist(id, newTitle)
    fun getPlaylist(id: String): LocalPlaylist? = collection.getPlaylist(id)
    fun addSong(id: String, song: Song): Boolean = collection.addSong(id, song)
}

/** Mutations publish immediately; the only work under the lock is copying lists. */
internal class LocalPlaylistCollection(
    private val persistSnapshot: (List<LocalPlaylist>) -> Unit,
) {
    private val mutationLock = Any()
    private val _playlists = MutableStateFlow<List<LocalPlaylist>>(emptyList())
    val playlists = _playlists.asStateFlow()

    fun restore(playlists: List<LocalPlaylist>) = synchronized(mutationLock) {
        _playlists.value = playlists.map { it.copy(songs = it.songs.toList()) }
    }

    fun savePlaylist(title: String, songs: List<Song>): LocalPlaylist = synchronized(mutationLock) {
        // Old timestamp ids remain valid; a UUID prevents simultaneous creates
        // from overwriting one another in the same millisecond.
        val id = "sp_local_" + UUID.randomUUID()
        val playlist = LocalPlaylist(id = id, title = title, songs = songs.toList())
        val updated = listOf(playlist) + _playlists.value
        _playlists.value = updated
        persistSnapshot(updated)
        playlist
    }

    fun deletePlaylist(id: String) = synchronized(mutationLock) {
        val updated = _playlists.value.filterNot { it.id == id || it.browseId == id || it.id == id.removePrefix("local:playlist:") }
        if (updated == _playlists.value) return@synchronized
        _playlists.value = updated
        persistSnapshot(updated)
    }

    fun renamePlaylist(id: String, newTitle: String) = synchronized(mutationLock) {
        val cleanId = id.removePrefix("local:playlist:").removePrefix("VL")
        val updated = _playlists.value.map {
            if (it.id == cleanId || it.id == id || it.browseId == id) {
                it.copy(title = newTitle)
            } else it
        }
        if (updated == _playlists.value) return@synchronized
        _playlists.value = updated
        persistSnapshot(updated)
    }

    fun getPlaylist(id: String): LocalPlaylist? {
        val cleanId = id.removePrefix("local:playlist:").removePrefix("VL")
        return _playlists.value.firstOrNull { it.id == cleanId || it.id == id || it.browseId == id }
    }

    /** The caller has already confirmed duplicates; each append is one occurrence. */
    fun addSong(id: String, song: Song): Boolean = synchronized(mutationLock) {
        val cleanId = id.removePrefix("local:playlist:").removePrefix("VL")
        val index = _playlists.value.indexOfFirst { it.id == cleanId || it.id == id || it.browseId == id }
        if (index < 0) return@synchronized false
        val updated = _playlists.value.mapIndexed { position, playlist ->
            if (position == index) playlist.copy(songs = playlist.songs + song.copy(setVideoId = null))
            else playlist
        }
        _playlists.value = updated
        persistSnapshot(updated)
        true
    }
}

/** One writer and one pending snapshot, even during a burst of edits. */
internal class LocalPlaylistSnapshotWriter(
    scope: CoroutineScope,
    persist: suspend (List<LocalPlaylist>) -> Unit,
) {
    private val snapshots = Channel<List<LocalPlaylist>>(Channel.CONFLATED)

    init {
        scope.launch {
            for (snapshot in snapshots) {
                try {
                    persist(snapshot)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    // A failed save must not terminate the writer and silently
                    // discard every later edit for the rest of the session.
                    Log.w("LocalPlaylistStore", "Unable to persist local playlists", error)
                }
            }
        }
    }

    fun enqueue(snapshot: List<LocalPlaylist>) {
        snapshots.trySend(snapshot)
    }
}

/** Same stored field names as the original JSON, with no Android JSON work on Main. */
internal object LocalPlaylistSnapshotCodec {
    fun encode(playlists: List<LocalPlaylist>): String = buildJsonArray {
        playlists.forEach { playlist ->
            add(buildJsonObject {
                put("id", playlist.id)
                put("title", playlist.title)
                put("songs", buildJsonArray {
                    playlist.songs.forEach { song ->
                        add(buildJsonObject {
                            put("videoId", song.videoId)
                            put("title", song.title)
                            put("artist", song.artist)
                            put("thumbnailUrl", song.thumbnailUrl)
                            put("durationText", song.durationText)
                            put("artistId", song.artistId)
                            put("albumId", song.albumId)
                            put("albumName", song.albumName)
                            put("isVideo", song.isVideo)
                        })
                    }
                })
            })
        }
    }.toString()

    fun decode(raw: String): List<LocalPlaylist> = Json.parseToJsonElement(raw).jsonArray.map { element ->
        val playlist = element.jsonObject
        LocalPlaylist(
            id = playlist.getValue("id").jsonPrimitive.content,
            title = playlist.getValue("title").jsonPrimitive.content,
            songs = playlist.getValue("songs").jsonArray.map { value ->
                val song = value.jsonObject
                Song(
                    videoId = song.text("videoId").orEmpty(),
                    title = song.text("title").orEmpty(),
                    artist = song.text("artist").orEmpty(),
                    thumbnailUrl = song.optionalText("thumbnailUrl"),
                    durationText = song.optionalText("durationText"),
                    artistId = song.optionalText("artistId"),
                    albumId = song.optionalText("albumId"),
                    albumName = song.optionalText("albumName"),
                    isVideo = song["isVideo"]?.jsonPrimitive?.booleanOrNull ?: false,
                )
            },
        )
    }

    private fun JsonObject.text(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull
    private fun JsonObject.optionalText(key: String): String? = text(key)?.takeUnless { it.isBlank() || it == "null" }
}
