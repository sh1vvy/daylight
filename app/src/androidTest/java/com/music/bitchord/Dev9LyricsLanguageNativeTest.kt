@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.music.bitchord

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsLanguage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import com.music.bitchord.ui.haptics.rememberHaptics
import com.music.bitchord.ui.player.PlayerHost
import com.music.bitchord.ui.player.PlayerPlatform
import com.music.bitchord.ui.player.PlayerSettingsSource
import com.music.bitchord.ui.player.rememberLyricsTranslation
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the bundled model on-device, without any translation or account request. */
@RunWith(AndroidJUnit4::class)
class Dev9LyricsLanguageNativeTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun playerHidesEnglishBeforeTappingAndAfterReopening() {
        val original = PlayerPlatform.host
        val checks = AtomicInteger()
        val english = listOf(LyricLine(0, "The sun is rising slowly over the city this morning. I hear your voice and remember every word you said. We walk together down the road and watch the world go by."))
        val french = listOf(LyricLine(0, "Je marche dans les rues de la ville avec toi ce soir. Le soleil se couche et je me souviens de tous les moments que nous avons partagés ensemble."))
        val lines = mutableStateOf(english)
        val opening = mutableStateOf(0)
        PlayerPlatform.install(object : PlayerHost by original {
            override val settings = object : PlayerSettingsSource by original.settings {
                override val translationLanguage = MutableStateFlow("en")
            }
            override suspend fun identifyLyricsLanguage(lines: List<LyricLine>): String? {
                val language = original.identifyLyricsLanguage(lines)
                checks.incrementAndGet()
                return language
            }
        })
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    MaterialTheme {
                        val translation = rememberLyricsTranslation(
                            "qa-dev9-${opening.value}", lines.value, null, false, "Loading", rememberHaptics(),
                        )
                        if (translation.canTranslate) Text("Translate available")
                    }
                } }
                compose.waitUntil(5_000) { checks.get() >= 1 }
                compose.waitForIdle()
                compose.onNodeWithText("Translate available").assertDoesNotExist()
                compose.runOnIdle { lines.value = french; opening.value++ }
                compose.waitUntil(5_000) { checks.get() >= 2 }
                compose.waitForIdle()
                compose.onNodeWithText("Translate available").assertIsDisplayed()
                compose.runOnIdle { lines.value = english; opening.value++ }
                compose.waitUntil(5_000) { checks.get() >= 3 }
                compose.waitForIdle()
                compose.onNodeWithText("Translate available").assertDoesNotExist()
            }
        } finally { PlayerPlatform.install(original) }
    }

    @Test fun englishAndCachedEnglishAreIdentifiedOffline() = runBlocking {
        val lines = listOf(
            "The sun is rising slowly over the city this morning",
            "I hear your voice and remember every word you said",
            "We walk together down the road and watch the world go by",
            "There is a light inside my heart that never fades away",
        ).mapIndexed { index, text -> LyricLine(index * 1_000L, text) }
        assertEquals("en", LyricsLanguage.identify(lines))
        assertEquals("en", LyricsLanguage.identify(lines))
    }

    @Test fun latinAlphabetDoesNotMeanEnglish() = runBlocking {
        val french = listOf(LyricLine(0, "Je marche dans les rues de la ville avec toi ce soir. Le soleil se couche et je me souviens de tous les moments que nous avons partagés ensemble."))
        assertEquals("fr", LyricsLanguage.identify(french))
        val spanish = listOf(LyricLine(0, "Quiero estar contigo esta noche y caminar por las calles de la ciudad. Recuerdo todos los momentos que hemos compartido y las palabras que me dijiste."))
        assertEquals("es", LyricsLanguage.identify(spanish))
    }

    @Test fun mixedAndEmptyLyricsDoNotSuppressTranslation() = runBlocking {
        val mixed = listOf(
            LyricLine(0, "The sun is rising slowly over the city and I remember every moment we spent together"),
            LyricLine(1_000, "मैं तुम्हारे साथ हूँ"),
        )
        assertNull(LyricsLanguage.identify(mixed))
        assertNull(LyricsLanguage.identify(emptyList()))
    }
}
