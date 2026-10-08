package com.music.bitchord.data.lyrics

/** Whether the original lyric or its backing vocal contains letters needing Latin transliteration. */
fun List<LyricLine>.needsRomanization(): Boolean = any { line ->
    line.text.hasNonLatinLetters() || line.background?.text?.hasNonLatinLetters() == true
}

private fun String.hasNonLatinLetters(): Boolean = codePoints().anyMatch { codePoint ->
    Character.isLetter(codePoint) && Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.LATIN
}
