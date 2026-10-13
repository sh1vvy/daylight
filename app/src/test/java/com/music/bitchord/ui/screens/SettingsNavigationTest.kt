/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-08 through 2026-10-13.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsNavigationTest {
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
    fun `search matches separate words across title and keywords`() {
        assertTrue(SettingsSearch("  audio  QUALITY ").matches("Audio quality", "Streaming"))
        assertTrue(SettingsSearch("lyrics BLUR").matches("Lyrics", "Focus blur"))
        assertFalse(SettingsSearch("lyrics unknown").matches("Lyrics", "Focus blur"))
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
