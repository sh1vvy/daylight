package com.music.bitchord

import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.RetiredSettings
import org.junit.Assert.*
import org.junit.Test

class CanaryDefaultsTest {
    @Test fun optionalLyricsProvidersRemainSelectableButAreNotContactedByDefault() {
        assertFalse(LyricsSource.KUGOU in LyricsSource.defaults)
        assertFalse(LyricsSource.BETTER_LYRICS_PORTATO in LyricsSource.defaults)
        assertTrue(LyricsSource.offered.containsAll(listOf(LyricsSource.KUGOU, LyricsSource.BETTER_LYRICS_PORTATO)))
        assertEquals(LyricsSource.defaults.toSet(), AppSettings.lyricsSources.value)
        assertTrue(LyricsSource.defaults.containsAll(listOf(LyricsSource.LRC_RED, LyricsSource.LRCLIB)))
    }

    @Test fun oldBackupsCannotReviveRetiredSettingsAndStillPreservePlaybackPreferences() {
        val old = mapOf("legacy_mesh_gradient" to true, "webdav_url" to "https://old.example",
            "smb_password" to "old-secret", "lossless_quality" to "HI_RES", "theme_mode" to "PINK_CLOUD")
        assertEquals(setOf("lossless_quality", "theme_mode"), old.filterKeys { !RetiredSettings.isRetired(it) }.keys)
    }
}
