/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-08 through 2026-10-09.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {
    @Test
    fun existingPreferencesRetainTheirTheme() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPersistedName("SYSTEM"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromPersistedName("LIGHT"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromPersistedName("DARK"))
    }

    @Test
    fun pinkCloudRestoresAfterRestart() {
        assertEquals(ThemeMode.PINK_CLOUD, ThemeMode.fromPersistedName("PINK_CLOUD"))
    }

    @Test
    fun retiredMaterialExpressiveMigratesToSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPersistedName("MATERIAL_EXPRESSIVE"))
    }

    @Test
    fun missingOrUnknownPreferenceKeepsTheExistingDarkDefault() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromPersistedName(null))
        assertEquals(ThemeMode.DARK, ThemeMode.fromPersistedName("FUTURE_THEME"))
    }
}
