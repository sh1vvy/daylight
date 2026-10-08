package com.music.bitchord.ui.screens

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsNavigationTest {
    @Test
    fun `categories start collapsed and can be opened independently`() {
        val initial = SettingsCategoryExpansion()
        assertFalse(initial.isExpanded("appearance", ""))
        val selected = initial.toggle("appearance").toggle("playback")
        assertTrue(selected.isExpanded("appearance", ""))
        assertTrue(selected.isExpanded("playback", ""))
        assertFalse(selected.isExpanded("downloads_storage", ""))
        val collapsed = selected.toggle("appearance")
        assertFalse(collapsed.isExpanded("appearance", ""))
        assertTrue(collapsed.isExpanded("playback", ""))
    }

    @Test
    fun `clearing a search restores manual expansion choices`() {
        val selected = SettingsCategoryExpansion().toggle("appearance")
        assertTrue(selected.isExpanded("playback", " equalizer "))
        assertFalse(selected.isExpanded("playback", ""))
        assertTrue(selected.isExpanded("appearance", ""))
        assertFalse(selected.isExpanded("playback", "   "))
    }

    @Test
    fun `saved categories restore after returning from another screen or recreation`() {
        val original = SettingsCategoryExpansion().toggle("appearance").toggle("downloads_storage")
        val scope = object : SaverScope {
            override fun canBeSaved(value: Any): Boolean = true
        }
        val saved = with(SettingsCategoryExpansion.Saver) { scope.save(original) }
        assertNotNull(saved)
        val restored = SettingsCategoryExpansion.Saver.restore(requireNotNull(saved))!!
        assertTrue(restored.isExpanded("appearance", ""))
        assertTrue(restored.isExpanded("downloads_storage", ""))
        assertFalse(restored.isExpanded("playback", ""))
    }

    @Test
    fun `search finds titles and category summaries while ignoring case and keyboard whitespace`() {
        val search = SettingsSearch("  LYRIC  ")
        assertTrue(search.active)
        assertFalse(search.anyMatch)
        assertFalse(search.matches("Appearance", "Theme", null))
        assertTrue(search.matches("Lyrics & accessibility", "Romanization"))
        assertTrue(search.anyMatch)
        assertFalse(search.matches("Downloads", "Storage"))
        assertTrue(search.anyMatch)
    }

    @Test
    fun `unknown search leaves an empty result without restoring obsolete rows`() {
        val search = SettingsSearch("listenbrainz")
        assertFalse(search.matches("Account & integrations", "last.fm", "spotify", "credits"))
        assertFalse(search.matches("Appearance", "Animated cover art"))
        assertFalse(search.anyMatch)
        val blank = SettingsSearch(" ")
        assertFalse(blank.active)
        assertTrue(blank.matches("Appearance"))
    }
}
