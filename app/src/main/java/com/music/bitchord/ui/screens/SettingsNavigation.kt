/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-08 through 2026-10-13.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.ui.screens

/** Each composition collects matches before deciding whether to show the empty state. */
internal class SettingsSearch(query: String) {
    private val words = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    val active: Boolean get() = words.isNotEmpty()

    var anyMatch = false
        private set

    fun matches(vararg keywords: String?): Boolean {
        val hit = !active || words.all { word -> keywords.any { it?.contains(word, ignoreCase = true) == true } }
        if (hit) anyMatch = true
        return hit
    }
}
