package com.music.bitchord.data.settings

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LosslessQualitySettingsTest {
    @Test
    fun oldBetaListenersRetainTheFullCeilingAndOneGibCacheFloor() {
        val preferences = FakePreferences(
            "lossless_beta" to true,
            AUDIO_CACHE_LIMIT_PREFERENCE to AppSettings.DEFAULT_CACHE_LIMIT_BYTES,
        )

        val quality = readAndMigrateLosslessQuality(preferences.prefs)
        val cacheLimit = readAndMigrateAudioCacheLimit(preferences.prefs, quality)

        assertEquals(LosslessQuality.HI_RES, quality)
        assertEquals("HI_RES", preferences.values[LOSSLESS_QUALITY_PREFERENCE])
        assertEquals(true, preferences.values["lossless_beta"])
        assertEquals(AppSettings.MIN_LOSSLESS_CACHE_LIMIT_BYTES, cacheLimit)
        assertEquals(cacheLimit, preferences.values[AUDIO_CACHE_LIMIT_PREFERENCE])
        assertEquals(2, preferences.appliedEdits)
    }

    @Test
    fun freshAndPreviouslyDisabledInstallsStayOptOutWithBoundedNormalCache() {
        for (preferences in listOf(FakePreferences(), FakePreferences("lossless_beta" to false))) {
            val quality = readAndMigrateLosslessQuality(preferences.prefs)
            assertEquals(LosslessQuality.OFF, quality)
            assertEquals(AppSettings.DEFAULT_CACHE_LIMIT_BYTES,
                readAndMigrateAudioCacheLimit(preferences.prefs, quality))
            assertEquals(false, preferences.values["lossless_beta"])
            assertFalse(preferences.values.containsKey(AUDIO_CACHE_LIMIT_PREFERENCE))
        }
    }

    @Test
    fun anExplicitCurrentChoiceWinsOverTheOldSwitchInRestoredBackups() {
        val off = FakePreferences(LOSSLESS_QUALITY_PREFERENCE to "OFF", "lossless_beta" to true)
        assertEquals(LosslessQuality.OFF, readAndMigrateLosslessQuality(off.prefs))
        assertEquals(false, off.values["lossless_beta"])

        val lossless = FakePreferences(LOSSLESS_QUALITY_PREFERENCE to "LOSSLESS", "lossless_beta" to false)
        assertEquals(LosslessQuality.LOSSLESS, readAndMigrateLosslessQuality(lossless.prefs))
        assertEquals(true, lossless.values["lossless_beta"])
    }

    @Test
    fun anUnrecognizedNewChoiceCannotSilentlyEnableLossless() {
        val preferences = FakePreferences(LOSSLESS_QUALITY_PREFERENCE to "FUTURE_QUALITY", "lossless_beta" to true)
        assertEquals(LosslessQuality.OFF, readAndMigrateLosslessQuality(preferences.prefs))
        assertEquals("OFF", preferences.values[LOSSLESS_QUALITY_PREFERENCE])
        assertEquals(false, preferences.values["lossless_beta"])
    }

    @Test
    fun currentPreferencesMakeNoRepeatedMigrationWrites() {
        val preferences = FakePreferences(
            LOSSLESS_QUALITY_PREFERENCE to "LOSSLESS",
            "lossless_beta" to true,
            AUDIO_CACHE_LIMIT_PREFERENCE to AppSettings.MIN_LOSSLESS_CACHE_LIMIT_BYTES,
        )
        repeat(2) {
            val quality = readAndMigrateLosslessQuality(preferences.prefs)
            readAndMigrateAudioCacheLimit(preferences.prefs, quality)
        }
        assertEquals(0, preferences.appliedEdits)
    }

    @Test
    fun bothLosslessTiersReserveAtLeastOneGibWithoutAllocatingUnlimitedStorage() {
        listOf(LosslessQuality.LOSSLESS, LosslessQuality.HI_RES).forEach { quality ->
            listOf(Long.MIN_VALUE, 0L, AppSettings.DEFAULT_CACHE_LIMIT_BYTES).forEach { oldLimit ->
                assertEquals(AppSettings.MIN_LOSSLESS_CACHE_LIMIT_BYTES, AudioCacheBudget.clamp(oldLimit, quality))
            }
            assertEquals(AppSettings.MAX_CACHE_LIMIT_BYTES,
                AudioCacheBudget.clamp(AppSettings.MAX_CACHE_LIMIT_BYTES, quality))
        }
    }

    @Test
    fun aLargerExistingBudgetAndExplicitUnlimitedChoiceSurviveTheUpgrade() {
        val larger = 2L * 1024 * 1024 * 1024
        listOf(larger, AppSettings.MAX_CACHE_LIMIT_BYTES, AppSettings.UNLIMITED_CACHE_LIMIT_BYTES).forEach { oldLimit ->
            val preferences = FakePreferences(AUDIO_CACHE_LIMIT_PREFERENCE to oldLimit)
            assertEquals(oldLimit, readAndMigrateAudioCacheLimit(preferences.prefs, LosslessQuality.HI_RES))
            assertEquals(0, preferences.appliedEdits)
        }
    }

    @Test
    fun disablingLosslessKeepsTheChosenBoundedBudgetButRestoresTheNormalMinimum() {
        assertEquals(AppSettings.DEFAULT_CACHE_LIMIT_BYTES, AudioCacheBudget.minimum(LosslessQuality.OFF))
        assertEquals(AppSettings.MIN_LOSSLESS_CACHE_LIMIT_BYTES,
            AudioCacheBudget.clamp(AppSettings.MIN_LOSSLESS_CACHE_LIMIT_BYTES, LosslessQuality.OFF))
        assertEquals(AppSettings.DEFAULT_CACHE_LIMIT_BYTES, AudioCacheBudget.clamp(0, LosslessQuality.OFF))
    }

    private class FakePreferences(vararg entries: Pair<String, Any>) {
        val values = entries.toMap().toMutableMap()
        var appliedEdits = 0
        private val updates = mutableMapOf<String, Any>()
        private val editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "putString", "putBoolean", "putLong" -> { updates[args!![0] as String] = args[1]; proxy }
                "apply" -> { values.putAll(updates); updates.clear(); appliedEdits++; null }
                else -> error("Unexpected editor method: ${method.name}")
            }
        } as SharedPreferences.Editor
        val prefs = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getString", "getBoolean", "getLong" -> values[args!![0] as String] ?: args[1]
                "edit" -> editor
                else -> error("Unexpected preferences method: ${method.name}")
            }
        } as SharedPreferences
    }
}
