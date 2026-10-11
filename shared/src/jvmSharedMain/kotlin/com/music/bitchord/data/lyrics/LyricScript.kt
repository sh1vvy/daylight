/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-08 through 2026-10-08.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.data.lyrics

/** Whether the original lyric or its backing vocal contains letters needing Latin transliteration. */
fun List<LyricLine>.needsRomanization(): Boolean = any { line ->
    line.text.hasNonLatinLetters() || line.background?.text?.hasNonLatinLetters() == true
}

private fun String.hasNonLatinLetters(): Boolean = codePoints().anyMatch { codePoint ->
    Character.isLetter(codePoint) && Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.LATIN
}
