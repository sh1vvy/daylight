package com.music.bitchord.data.library

import android.content.Context
import com.music.bitchord.data.DislikeStorage
import java.security.MessageDigest

/** Small durable rating set, isolated by Google account and YouTube profile. */
class DislikedTracks(context: Context) : DislikeStorage {
    private val prefs = context.getSharedPreferences("daylight_disliked_tracks", Context.MODE_PRIVATE)
    private fun key(scope: String): String = MessageDigest.getInstance("SHA-256")
        .digest(scope.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    override fun read(scope: String): Set<String> = prefs.getStringSet(key(scope), emptySet()).orEmpty().toSet()
    override fun write(scope: String, videoIds: Set<String>) {
        prefs.edit().putStringSet(key(scope), videoIds.toSet()).apply()
    }
}
