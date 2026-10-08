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
    fun materialExpressiveRestoresWithoutChangingExistingThemes() {
        assertEquals(ThemeMode.MATERIAL_EXPRESSIVE, ThemeMode.fromPersistedName("MATERIAL_EXPRESSIVE"))
    }

    @Test
    fun missingOrUnknownPreferenceKeepsTheExistingDarkDefault() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromPersistedName(null))
        assertEquals(ThemeMode.DARK, ThemeMode.fromPersistedName("FUTURE_THEME"))
    }
}
