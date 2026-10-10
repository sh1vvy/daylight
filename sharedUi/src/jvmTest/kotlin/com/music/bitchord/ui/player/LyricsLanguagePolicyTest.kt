package com.music.bitchord.ui.player

import com.music.bitchord.data.lyrics.LyricLine
import org.junit.Assert.*
import org.junit.Test

class LyricsLanguagePolicyTest {
    private val lyrics = listOf(LyricLine(0, "I can see the morning light"))

    @Test fun `English lyrics hide translation in English app locales`() {
        assertFalse(translationAvailable(lyrics, true, "en", "en"))
        assertFalse(translationAvailable(lyrics, true, "en-US", "en-GB"))
    }

    @Test fun `other languages and English in a different app language retain translation`() {
        assertTrue(translationAvailable(lyrics, true, "fr", "en"))
        assertTrue(translationAvailable(lyrics, true, "en", "es"))
        assertFalse(translationAvailable(lyrics, true, "fr", "fr-CA"))
    }

    @Test fun `uncertain or mixed lyrics stay translatable after checking`() {
        assertTrue(translationAvailable(lyrics, true, null, "en"))
        assertTrue(translationAvailable(lyrics, true, "und", "en"))
        assertFalse(translationAvailable(lyrics, false, null, "en"))
    }

    @Test fun `empty and instrumental lyrics never offer translation`() {
        assertFalse(translationAvailable(null, true, null, "en"))
        assertFalse(translationAvailable(emptyList(), true, null, "en"))
        assertFalse(translationAvailable(listOf(LyricLine(0, "♪ ♫")), true, null, "en"))
    }
}
