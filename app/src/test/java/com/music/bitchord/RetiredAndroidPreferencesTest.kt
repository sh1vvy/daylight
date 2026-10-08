package com.music.bitchord

import android.content.SharedPreferences
import com.music.bitchord.data.settings.removeRetiredAndroidPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RetiredAndroidPreferencesTest {
    @Test
    fun `upgrades remove integrations and old lyric language while preserving Spotify login`() {
        val preferences = FakePreferences(
            "listenbrainz_enabled" to true,
            "listenbrainz_token" to "old-listening-token",
            "listenbrainz_primary_artist_only" to true,
            "spotify_canvas_auto_hide" to true,
            "prioritize_spotify_canvas" to true,
            "translation_language" to "ja",
            "spotify_spdc_token" to "playlist-session",
            "animated_canvas" to true,
            "theme_mode" to "PINK_CLOUD",
        )

        removeRetiredAndroidPreferences(preferences.prefs)

        assertEquals(
            mapOf(
                "spotify_spdc_token" to "playlist-session",
                "animated_canvas" to true,
                "theme_mode" to "PINK_CLOUD",
            ),
            preferences.values,
        )
        assertEquals(1, preferences.appliedEdits)
    }

    @Test
    fun `a restored preference file cannot revive retired features`() {
        val preferences = FakePreferences("translation_language" to "fr")
        removeRetiredAndroidPreferences(preferences.prefs)
        preferences.values["translation_language"] = "ko"
        preferences.values["listenbrainz_enabled"] = true
        preferences.values["listenbrainz_token"] = "restored-token"

        removeRetiredAndroidPreferences(preferences.prefs)

        assertFalse(preferences.values.containsKey("translation_language"))
        assertFalse(preferences.values.containsKey("listenbrainz_enabled"))
        assertFalse(preferences.values.containsKey("listenbrainz_token"))
        assertEquals(2, preferences.appliedEdits)
    }

    @Test
    fun `launches with current preferences perform no migration writes`() {
        val preferences = FakePreferences("spotify_spdc_token" to "playlist-session")
        removeRetiredAndroidPreferences(preferences.prefs)
        removeRetiredAndroidPreferences(preferences.prefs)

        assertEquals(0, preferences.appliedEdits)
        assertEquals(mapOf("spotify_spdc_token" to "playlist-session"), preferences.values)
    }

    /** These tests use the real migration against a small in-memory preference interface. */
    private class FakePreferences(vararg entries: Pair<String, Any>) {
        val values = entries.toMap().toMutableMap()
        var appliedEdits = 0
        private val removals = mutableSetOf<String>()
        private val editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "remove" -> { removals += args!![0] as String; proxy }
                "apply" -> {
                    removals.forEach(values::remove)
                    removals.clear()
                    appliedEdits++
                    null
                }
                else -> error("Unexpected editor method: ${method.name}")
            }
        } as SharedPreferences.Editor
        val prefs = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            when (method.name) {
                "contains" -> values.containsKey(args!![0] as String)
                "edit" -> editor
                else -> error("Unexpected preferences method: ${method.name}")
            }
        } as SharedPreferences
    }
}
