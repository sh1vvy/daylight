/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord

import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.ui.screens.CreditsScreen
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Packaged attribution and GPL text remain readable offline in the real Android UI. */
@RunWith(AndroidJUnit4::class)
class CanaryAttributionNativeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun creditsOpenPackagedAttributionLicenseAndOriginalAuthorsOffline() {
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue(AuthStore(context).sessions.isEmpty())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                BitChordTheme(darkTheme = true) { CreditsScreen(PaddingValues()) }
            } }
            compose.onNodeWithText("Daylight modifications by sh1vvy.", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Attribution and origin").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Under GNU GPLv3 sections", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Under GNU GPLv3 sections", substring = true).assertTextContains("Earlier copies distributed without these terms", substring = true)
            compose.onNodeWithText("Close").performClick()
            compose.onNodeWithText("Read GPLv3 license").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("GNU GENERAL PUBLIC LICENSE", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("GNU GENERAL PUBLIC LICENSE", substring = true).assertTextContains("Version 3, 29 June 2007", substring = true)
            compose.onNodeWithText("Close").performClick()
            compose.onNodeWithText("Copyright and contributions", substring = false).performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("BitChord was created by Kushagra Singh", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("BitChord was created by Kushagra Singh", substring = true).assertTextContains("Other authors keep ownership", substring = true)
            compose.onNodeWithText("Close").performClick()
        }
    }
}
