/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-10 through 2026-10-10.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.ui.player

import com.music.bitchord.data.lyrics.LyricLine
import java.util.Locale

/** Avoid an initial button flash; uncertain language identification never disables translation. */
internal fun translationAvailable(
    lyrics: List<LyricLine>?,
    languageChecked: Boolean,
    originalLanguage: String?,
    targetLanguage: String,
): Boolean {
    if (!languageChecked || lyrics.isNullOrEmpty() || lyrics.none { it.text.any(Char::isLetter) }) return false
    val source = originalLanguage?.let { Locale.forLanguageTag(it).language }.orEmpty()
    val target = Locale.forLanguageTag(targetLanguage).language
    return source.isBlank() || source == "und" || source != target
}
