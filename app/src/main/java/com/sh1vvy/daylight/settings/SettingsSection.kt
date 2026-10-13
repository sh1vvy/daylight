/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-13 through 2026-10-13.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.settings

/** Daylight's navigation vocabulary. IDs stay stable across translations and recreation. */
enum class SettingsSection(val id: String, val topics: List<String>) {
    APPEARANCE("appearance", listOf("theme", "dark", "light", "pink", "glass", "player", "artwork", "canvas", "volume", "status")),
    PLAYBACK("playback", listOf("audio", "quality", "wifi", "cellular", "lossless", "hi-res", "automix", "crossfade", "equalizer", "normalization", "dolby", "spatial", "silence", "pcm", "usb", "dac", "queue", "recommendations", "languages", "suggestions", "music")),
    LYRICS("lyrics_accessibility", listOf("lyrics", "translation", "romanization", "language", "font", "accessibility", "blur", "animation", "sources", "sync")),
    STORAGE("downloads_storage", listOf("download", "offline", "cache", "storage", "folder", "local", "files", "wifi", "export")),
    PERFORMANCE("performance", listOf("performance", "refresh", "battery", "rate", "stats", "background", "task")),
    DATA("your_data", listOf("data", "backup", "restore", "import", "export", "replay", "genre"));

    fun matches(query: String, title: String, summary: String): Boolean {
        val words = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        val searchable = listOf(title, summary) + topics
        return words.all { word -> searchable.any { it.contains(word, ignoreCase = true) } }
    }

    companion object {
        fun fromId(id: String?): SettingsSection? = entries.firstOrNull { it.id == id }
    }
}
