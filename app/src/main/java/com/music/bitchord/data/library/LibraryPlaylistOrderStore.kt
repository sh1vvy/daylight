package com.music.bitchord.data.library

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.model.playlistCreationOrderKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Successful creates/additions determine recency; reads, renames and refreshes never do. */
object LibraryPlaylistOrderStore {
    private const val PREF_NAME = "daylight_library_playlist_order"
    private const val KEY_CREATED_IDS = "created_playlist_ids"
    private const val KEY_RECENT_IDS = "recent_playlist_ids"
    private var preferences: SharedPreferences? = null
    private val order = PlaylistCreationOrder { ids ->
        preferences?.edit()?.putString(KEY_RECENT_IDS, Json.encodeToString(ids))?.apply()
    }
    val createdPlaylistIds = order.createdPlaylistIds
    /** Includes successful song additions as well as creation, newest activity first. */
    val recentPlaylistIds = order.createdPlaylistIds

    @Synchronized
    fun init(context: Context) {
        if (preferences != null) return
        preferences = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val saved = playlistActivityHistory(
            preferences?.getString(KEY_RECENT_IDS, null),
            preferences?.getString(KEY_CREATED_IDS, null),
        )
        order.restore(saved)
    }

    @Synchronized
    fun recordCreated(browseIdOrRawPlaylistId: String) = order.recordCreated(browseIdOrRawPlaylistId)

    @Synchronized
    fun recordActivity(browseIdOrRawPlaylistId: String) = order.recordActivity(browseIdOrRawPlaylistId)
}

/** Old creation history stays useful when upgrading to activity-based ordering. */
internal fun playlistActivityHistory(recent: String?, legacyCreated: String?): List<String> =
    listOfNotNull(recent, legacyCreated).firstNotNullOfOrNull { raw ->
        runCatching { Json.decodeFromString<List<String>>(raw) }.getOrNull()
    }.orEmpty()

/** A synchronized reducer keeps simultaneous creations/additions from losing one another. */
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
        promote(key)
    }

    /** Called only after a successful song write; duplicate cancellation never reaches it. */
    fun recordActivity(id: String) = synchronized(mutationLock) {
        val key = playlistCreationOrderKey(id)
        if (key.isBlank() || _createdPlaylistIds.value.firstOrNull() == key) return@synchronized
        promote(key)
    }

    private fun promote(key: String) {
        val updated = listOf(key) + _createdPlaylistIds.value.filterNot { it == key }
        _createdPlaylistIds.value = updated
        persist(updated)
    }
}
