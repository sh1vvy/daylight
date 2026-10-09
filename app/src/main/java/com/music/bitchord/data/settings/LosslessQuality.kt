package com.music.bitchord.data.settings

import android.content.SharedPreferences

/** Separate from YouTube's bitrate preference so both keep their own ceiling. */
enum class LosslessQuality {
    OFF,
    LOSSLESS,
    HI_RES;

    companion object {
        /** Existing beta listeners keep Dev.5's full quality ceiling after upgrading. */
        fun fromPersistedName(name: String?, legacyEnabled: Boolean): LosslessQuality =
            if (name == null) {
                if (legacyEnabled) HI_RES else OFF
            } else {
                entries.firstOrNull { it.name == name } ?: OFF
            }
    }
}

internal const val LOSSLESS_QUALITY_PREFERENCE = "lossless_quality"
internal const val AUDIO_CACHE_LIMIT_PREFERENCE = "audio_cache_limit_bytes"
private const val LEGACY_LOSSLESS_PREFERENCE = "lossless_beta"

/** Also used after restoring a backup, so the old switch cannot revive a newer OFF choice. */
internal fun readAndMigrateLosslessQuality(prefs: SharedPreferences): LosslessQuality {
    val saved = prefs.getString(LOSSLESS_QUALITY_PREFERENCE, null)
    val legacyEnabled = prefs.getBoolean(LEGACY_LOSSLESS_PREFERENCE, false)
    val quality = LosslessQuality.fromPersistedName(saved, legacyEnabled)
    val enabled = quality != LosslessQuality.OFF
    if (saved != quality.name || legacyEnabled != enabled) {
        prefs.edit()
            .putString(LOSSLESS_QUALITY_PREFERENCE, quality.name)
            .putBoolean(LEGACY_LOSSLESS_PREFERENCE, enabled)
            .apply()
    }
    return quality
}

/** Disk limits are budgets, not eagerly allocated space. Both defaults stay bounded. */
internal object AudioCacheBudget {
    fun minimum(quality: LosslessQuality): Long =
        if (quality == LosslessQuality.OFF) AppSettings.DEFAULT_CACHE_LIMIT_BYTES
        else AppSettings.MIN_LOSSLESS_CACHE_LIMIT_BYTES

    fun clamp(bytes: Long, quality: LosslessQuality): Long =
        if (bytes > AppSettings.MAX_CACHE_LIMIT_BYTES) AppSettings.UNLIMITED_CACHE_LIMIT_BYTES
        else bytes.coerceAtLeast(minimum(quality))
}

/** Persist the new floor once on upgrades/restores instead of changing it on every launch. */
internal fun readAndMigrateAudioCacheLimit(prefs: SharedPreferences, quality: LosslessQuality): Long {
    val saved = prefs.getLong(AUDIO_CACHE_LIMIT_PREFERENCE, AppSettings.DEFAULT_CACHE_LIMIT_BYTES)
    val limit = AudioCacheBudget.clamp(saved, quality)
    if (saved != limit) prefs.edit().putLong(AUDIO_CACHE_LIMIT_PREFERENCE, limit).apply()
    return limit
}
