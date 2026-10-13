/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-13 through 2026-10-13.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.settings

import org.junit.Assert.*
import org.junit.Test

class SettingsSectionTest {
    @Test fun persistedRoutesRestoreAndUnknownRoutesFallBackToHub() {
        SettingsSection.entries.forEach { assertEquals(it, SettingsSection.fromId(it.id)) }
        assertEquals(SettingsSection.entries.size, SettingsSection.entries.map { it.id }.toSet().size)
        assertNull(SettingsSection.fromId(null))
        assertNull(SettingsSection.fromId("removed-category"))
    }

    @Test fun queriesFindLocalizedTitlesAndSeparateTopicWords() {
        assertTrue(SettingsSection.PLAYBACK.matches("  AUDIO   automix ", "Playback", "Streaming and transitions"))
        assertTrue(SettingsSection.APPEARANCE.matches("apparence", "Apparence", "Thèmes et lecteur"))
        assertFalse(SettingsSection.STORAGE.matches("audio automix", "Storage", "Downloads"))
        assertFalse(SettingsSection.PLAYBACK.matches("audio nonsense", "Playback", "Streaming"))
        assertTrue(SettingsSection.DATA.matches(" \n ", "Your data", "Backups"))
    }
}
