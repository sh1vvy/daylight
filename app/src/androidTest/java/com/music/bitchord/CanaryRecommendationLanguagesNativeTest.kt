package com.music.bitchord

import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.screens.RecommendationLanguageSheet
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanaryRecommendationLanguagesNativeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun searchableLanguageChoicesSaveTogetherAndCancelWithoutChangingPreferences() {
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue(AuthStore(context).sessions.isEmpty())
        val old = AppSettings.excludedRecommendationLanguages.value
        AppSettings.setExcludedRecommendationLanguages(emptySet())
        val visible = mutableStateOf(true)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    BitChordTheme(darkTheme = true) {
                        if (visible.value) RecommendationLanguageSheet(AppSettings.excludedRecommendationLanguages.value,
                            onDismiss = { visible.value = false },
                            onSave = { AppSettings.setExcludedRecommendationLanguages(it); visible.value = false })
                    }
                } }
                compose.onNode(hasSetTextAction()).performTextInput("Hindi")
                compose.onNode(hasText("Hindi") and hasClickAction() and !hasSetTextAction()).assertIsDisplayed().performClick()
                compose.onNode(hasSetTextAction()).performTextReplacement("Spanish")
                compose.onNode(hasText("Spanish") and hasClickAction() and !hasSetTextAction()).assertIsDisplayed().performClick()
                compose.onNodeWithText(context.getString(R.string.save)).performClick()
                assertEquals(setOf("hi", "es"), AppSettings.excludedRecommendationLanguages.value)
                compose.runOnIdle { visible.value = true }
                compose.onNodeWithText(context.getString(R.string.recommendation_languages_reset)).performClick()
                compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
                assertEquals(setOf("hi", "es"), AppSettings.excludedRecommendationLanguages.value)
            }
        } finally { AppSettings.setExcludedRecommendationLanguages(old) }
    }
}
