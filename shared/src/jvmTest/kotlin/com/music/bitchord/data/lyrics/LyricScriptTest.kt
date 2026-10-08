package com.music.bitchord.data.lyrics

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LyricScriptTest {
    private fun lyrics(text: String) = listOf(LyricLine(0L, text))

    @Test
    fun latinLyricsIncludingAccentsAndRomanizedHindiNeedNoRomanization() {
        for (text in listOf("Just keep watching", "Déjà vu, coração, mañana", "Tera hone laga hoon", "Cafe\u0301")) {
            assertFalse(lyrics(text).needsRomanization(), text)
        }
    }

    @Test
    fun emptyLyricsNumbersAndSymbolsDoNotOfferRomanization() {
        assertFalse(emptyList<LyricLine>().needsRomanization())
        assertFalse(lyrics("[Chorus] ♪ 123 … 🎵").needsRomanization())
    }

    @Test
    fun nativeAndMixedScriptLyricsKeepRomanizationAvailable() {
        for (text in listOf("तेरा होने लगा हूँ", "こんにちは", "Привет", "Just keep watching 你好", "\uD801\uDC00")) {
            assertTrue(lyrics(text).needsRomanization(), text)
        }
    }

    @Test
    fun backingVocalsAreCheckedAlongWithTheLead() {
        val line = LyricLine(0L, "Hello", background = LyricLine(0L, "こんにちは"))
        assertTrue(listOf(line).needsRomanization())
        assertFalse(listOf(line.copy(background = LyricLine(0L, "Bonjour"))).needsRomanization())
    }

    @Test
    fun alreadyLatinLyricsReturnWithoutAProviderRequest() = runBlocking {
        assertEquals(
            LyricsTranslation.RomanizationResult.AlreadyRomanized,
            LyricsTranslation.romanize("latin-script-regression", lyrics("Just keep watching"), "en"),
        )
    }
}
