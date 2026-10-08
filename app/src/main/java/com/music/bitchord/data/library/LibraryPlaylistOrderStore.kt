package com.music.bitchord.data.library

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.model.playlistCreationOrderKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Actual creates are recorded once; provider refreshes and renames never change this order. */
object LibraryPlaylistOrderStore {
    private const val PREF_NAME = "daylight_library_playlist_order"
    private const val KEY_CREATED_IDS = "created_playlist_ids"
    private var preferences: SharedPreferences? = null
    private val order = PlaylistCreationOrder { ids ->
        preferences?.edit()?.putString(KEY_CREATED_IDS, Json.encodeToString(ids))?.apply()
    }
    val createdPlaylistIds = order.createdPlaylistIds

    @Synchronized
    fun init(context: Context) {
        if (preferences != null) return
        preferences = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val saved = preferences?.getString(KEY_CREATED_IDS, null)?.let { raw ->
            runCatching { Json.decodeFromString<List<String>>(raw) }.getOrNull()
        }.orEmpty()
        order.restore(saved)
    }

    @Synchronized
    fun recordCreated(browseIdOrRawPlaylistId: String) = order.recordCreated(browseIdOrRawPlaylistId)
}

/** A small synchronized reducer keeps simultaneous creates from losing one another. */
internal class PlaylistCreationOrder(
    private val persist: (List<String>) -> Unit,
) {
    private val mutationLock = Any()
    private val _createdPlaylistIds = MutableStateFlow<List<String>>(emptyList())
    val createdPlaylistIds = _createdPlaylistIds.asStateFlow()

    fun restore(savedIds: List<String>) = synchronized(mutationLock) {
        // A caller that created before initialization must remain newer than disk history.
        val pending = _createdPlaylistIds.value
        val restored = (pending + savedIds)
            .map(::playlistCreationOrderKey)
            .filter { it.isNotBlank() }
            .distinct()
        _createdPlaylistIds.value = restored
        if (pending.isNotEmpty()) persist(restored)
    }

    fun recordCreated(id: String) = synchronized(mutationLock) {
        val key = playlistCreationOrderKey(id)
        if (key.isBlank() || key in _createdPlaylistIds.value) return@synchronized
        val updated = listOf(key) + _createdPlaylistIds.value
        _createdPlaylistIds.value = updated
        persist(updated)
    }
}
