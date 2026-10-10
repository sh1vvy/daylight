package com.music.bitchord.data

import com.music.bitchord.data.model.LikeStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ratings shared by the UI and playback service; dislikes restore from per-listener storage.
 *
 * The library remains the source for ratings that were already known at load time;
 * these overrides win over it so a notification tap and a player-screen tap paint
 * the same result immediately.
 */
interface DislikeStorage {
    fun read(scope: String): Set<String>
    fun write(scope: String, videoIds: Set<String>)
}

object LikeState {
    private var storage: DislikeStorage? = null
    private var scope: String = "device"

    fun installStorage(value: DislikeStorage?, listener: String?) {
        storage = value
        scope = listener ?: "device"
        restoreDislikes()
    }

    fun selectScope(listener: String?) {
        scope = listener ?: "device"
        restoreDislikes()
    }

    private fun restoreDislikes() {
        _overrides.value = storage?.read(scope).orEmpty().associateWith { LikeStatus.DISLIKE }
    }

    fun isDisliked(videoId: String): Boolean = _overrides.value[videoId] == LikeStatus.DISLIKE

    private val _overrides = MutableStateFlow<Map<String, LikeStatus>>(emptyMap())
    val overrides: StateFlow<Map<String, LikeStatus>> = _overrides.asStateFlow()

    fun set(videoId: String, status: LikeStatus) {
        val wasDisliked = isDisliked(videoId)
        _overrides.value += (videoId to status)
        if (wasDisliked != (status == LikeStatus.DISLIKE)) {
            storage?.write(scope, _overrides.value.filterValues { it == LikeStatus.DISLIKE }.keys)
        }
    }

    /**
     * Records a rating read off a track's own menu, but only when the menu
     * actually states one. A missing like button or an absent rating (null)
     * must never overwrite what this session already knows — kept null
     * rather than INDIFFERENT so the two stay distinct — and an explicit
     * override already made this session always wins.
     */
    fun rememberStated(videoId: String, stated: LikeStatus?) {
        if (stated != null && stated != LikeStatus.INDIFFERENT && videoId !in _overrides.value) {
            set(videoId, stated)
        }
    }

    /** Seeds only ratings not already changed explicitly during this session. */
    fun seedLiked(videoIds: Set<String>) {
        if (videoIds.isEmpty()) return
        val next = _overrides.value.toMutableMap()
        videoIds.forEach { next.putIfAbsent(it, LikeStatus.LIKE) }
        if (next != _overrides.value) _overrides.value = next
    }

    fun clear() {
        _overrides.value = emptyMap()
    }
}
