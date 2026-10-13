/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-13 through 2026-10-13.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord

import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.FrostedTopBar
import com.music.bitchord.ui.theme.BitChordTheme
import com.music.bitchord.ui.theme.daylightBackground
import com.sh1vvy.daylight.settings.*
import com.sh1vvy.daylight.welcome.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android rendering and persisted entry flow; restricted to account-free emulators. */
@RunWith(AndroidJUnit4::class)
class CanaryWelcomeSettingsNativeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context get() = inst.targetContext
    private fun requireEmulator() {
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue(AuthStore(context).sessions.isEmpty())
        inst.uiAutomation.grantRuntimePermission(context.packageName, if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    @Test fun guestChoicePersistsAndSettingsIsReachableAfterRecreation() {
        requireEmulator()
        val prefs = context.getSharedPreferences("daylight_entry", 0)
        prefs.edit().clear().commit() // Disposable emulator only; never account/preferences storage.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithTag("daylight-welcome-youtube").assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
            compose.onNodeWithTag("daylight-welcome-guest").assertIsDisplayed()
            assertFalse("Cancelling sign-in does not finish onboarding", WelcomePreferences(context).completed)
            compose.onNodeWithTag("daylight-welcome-guest").performClick()
            compose.waitUntil(10_000) { WelcomePreferences(context).completed }
            scenario.recreate()
            compose.onNodeWithTag("daylight-welcome").assertDoesNotExist()
            compose.onNodeWithTag("daylight-open-settings").performClick()
            compose.onNodeWithTag("daylight-settings-hub").assertIsDisplayed()
            capture("settings-native")
            compose.onNodeWithTag("daylight-settings-category-appearance").performClick()
            compose.onNodeWithTag("daylight-settings-section-appearance").assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.liquid_glass)).assertIsDisplayed()
            capture("appearance-native")
            scenario.recreate()
            compose.onNodeWithTag("daylight-settings-section-appearance").assertIsDisplayed()
        }
    }

    @Test fun welcomeFitsAllThemesAndLargeTypeWithoutChangingActionBehavior() {
        requireEmulator()
        for ((name, dark, pink) in listOf(Triple("dark", true, false), Triple("light", false, false), Triple("pink", false, true))) {
            val youtube = AtomicInteger(); val guest = AtomicInteger()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    BitChordTheme(darkTheme = dark, pinkCloud = pink) {
                        val density = LocalDensity.current
                        CompositionLocalProvider(LocalDensity provides Density(density.density, if (pink) 1.5f else 1f)) {
                            WelcomeScreen(reduceMotion = pink, onYouTube = { youtube.incrementAndGet() }, onGuest = { guest.incrementAndGet() })
                        }
                    }
                } }
                compose.onNodeWithTag("daylight-welcome-youtube").performScrollTo().assertIsDisplayed()
                val b = compose.onNodeWithTag("daylight-welcome-youtube").fetchSemanticsNode().boundsInRoot
                assertTrue("Sign in retains a touch target", b.height >= 48 * context.resources.displayMetrics.density)
                compose.onNodeWithTag("daylight-welcome-youtube").performClick()
                compose.onNodeWithTag("daylight-welcome-guest").performScrollTo().performClick()
                assertEquals(1, youtube.get()); assertEquals(1, guest.get())
                capture("welcome-$name")
            }
        }
    }

    @Test fun categorySearchAndThemeControlsRemainAvailableAcrossThemes() {
        requireEmulator()
        for ((name, dark, pink) in listOf(Triple("dark", true, false), Triple("light", false, false), Triple("pink", false, true))) {
            val chosen = AtomicInteger(-1)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    BitChordTheme(darkTheme = dark, pinkCloud = pink) {
                        var section by remember { mutableStateOf<SettingsSection?>(null) }
                        Box(Modifier.fillMaxSize().daylightBackground()) {
                            SettingsExperience(section, { section = it }, null, null, false,
                                {}, {}, {}, if (pink) 2 else if (dark) 0 else 1, { chosen.set(it) }, {}, "QA", true,
                                PaddingValues(top = 32.dp, bottom = 80.dp)) { selected, query, _ -> Text("${selected.id}:$query") }
                        }
                    }
                } }
                capture("settings-$name")
                compose.onNodeWithTag("daylight-settings-theme-2").performClick()
                assertEquals(2, chosen.get())
                compose.onNodeWithTag("daylight-settings-search").performTextInput("automix")
                compose.onNodeWithTag("daylight-settings-category-playback").assertIsDisplayed().performClick()
                compose.onNodeWithTag("daylight-settings-section-playback").assertIsDisplayed()
                compose.onNodeWithText("playback:automix").assertIsDisplayed()
                capture("settings-$name-search")
            }
        }
    }

    @Test fun expressiveRefreshFollowsPullAndRespectsReducedMotion() {
        requireEmulator()
        val originalReduce = AppSettings.reduceAnimation.value
        val originalGlass = AppSettings.liquidGlass.value
        AppSettings.setLiquidGlass(false)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val fraction = mutableFloatStateOf(0f)
                val refreshing = mutableStateOf(false)
                scenario.onActivity { activity -> activity.setContent {
                    BitChordTheme(darkTheme = true) {
                        Column(Modifier.fillMaxSize().daylightBackground().statusBarsPadding()) {
                            FrostedTopBar("Home", false, refreshing = refreshing.value, pullFraction = { fraction.floatValue })
                            Text("Refresh fixture", Modifier.padding(top = 100.dp))
                        }
                    }
                } }
                compose.runOnIdle { fraction.floatValue = .7f }
                capture("refresh-pull")
                compose.runOnIdle { fraction.floatValue = 1f; refreshing.value = true }
                capture("refresh-morph")
                compose.runOnIdle { AppSettings.setReduceAnimation(true) }
                capture("refresh-reduced-motion")
                compose.runOnIdle { fraction.floatValue = 0f; refreshing.value = false }
                compose.onNodeWithText("Refresh fixture").assertIsDisplayed()
                capture("refresh-settled")
            }
        } finally { AppSettings.setReduceAnimation(originalReduce); AppSettings.setLiquidGlass(originalGlass) }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        SystemClock.sleep(350) // Let the native frame catch up with Compose’s virtual test clock.
        val bitmap = requireNotNull(inst.uiAutomation.takeScreenshot())
        val file = File(context.getExternalFilesDir(null), "canary-21-qa/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
