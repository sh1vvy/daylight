/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord

import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.BottomTab
import com.music.bitchord.ui.components.LocalAppBackdrop
import com.music.bitchord.ui.components.LocalLiquidGlassEnabled
import com.music.bitchord.ui.components.PlayerNavigationBar
import com.music.bitchord.ui.components.backdrop.Backdrop
import com.music.bitchord.ui.components.floatingtabbar.FloatingTabBarScrollConnection
import com.music.bitchord.ui.theme.BitChordTheme
import dev.chrisbanes.haze.HazeState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Real folding/transport controls, with no network, audio or account mutations. */
class CanaryPlayerChromeNativeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun playerAndTabsKeepTheirActionsAcrossEveryThemeAndMaterial() {
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue(AuthStore(context).sessions.isEmpty())
        val oldBlur = AppSettings.reduceDynamicBlur.value
        val backdrop = object : Backdrop {
            override val isCoordinatesDependent = false
            override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?,
                layerBlock: (GraphicsLayerScope.() -> Unit)?) { drawRect(Color(0xFF9B7B85)) }
        }
        val tabs = listOf(BottomTab("Home", Icons.Rounded.Home), BottomTab("Discover", Icons.Rounded.GridView),
            BottomTab("Library", Icons.Rounded.LibraryMusic), BottomTab("Search", Icons.Rounded.Search))
        try {
            AppSettings.setReduceDynamicBlur(false)
            for (theme in listOf("light", "dark", "pink")) for (glass in listOf(false, true)) {
                val selected = mutableIntStateOf(0)
                val connection = FloatingTabBarScrollConnection(scrollThresholdPx = 30f)
                var transportTaps = 0
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    scenario.onActivity { activity -> activity.setContent {
                        BitChordTheme(darkTheme = theme == "dark", pinkCloud = theme == "pink") {
                            CompositionLocalProvider(LocalLiquidGlassEnabled provides glass, LocalAppBackdrop provides backdrop) {
                                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                                    PlayerNavigationBar(HazeState(), tabs, selected.intValue, { selected.intValue = it }, connection,
                                        Song("local:chrome-qa", "Chrome check", "Daylight QA", null), false, false,
                                        onPlayPause = { transportTaps++ }, onNext = {}, onPrevious = {}, onExpand = {},
                                        modifier = Modifier.align(Alignment.BottomCenter))
                                }
                            }
                        }
                    } }
                    compose.waitForIdle()
                    compose.onNodeWithText("Chrome check").assertIsDisplayed()
                    compose.onNodeWithText("Daylight QA").assertIsDisplayed()
                    compose.onNodeWithContentDescription(context.getString(R.string.play)).performClick()
                    compose.runOnIdle { assertEquals(1, transportTaps) }
                    compose.onNodeWithContentDescription("Library").performClick()
                    compose.runOnIdle { assertEquals(2, selected.intValue) }
                    screenshot("$theme-glass-$glass-expanded")
                    compose.runOnIdle { connection.inline() }
                    compose.waitForIdle()
                    compose.onNodeWithText("Chrome check").assertIsDisplayed()
                    compose.onNodeWithText("Daylight QA").assertDoesNotExist()
                    compose.onNodeWithContentDescription("Search").assertIsDisplayed()
                    screenshot("$theme-glass-$glass-collapsed")
                    compose.onNodeWithContentDescription(context.getString(R.string.play)).performClick()
                    compose.runOnIdle { assertEquals(2, transportTaps); connection.expand() }
                    compose.waitForIdle()
                    compose.onNodeWithText("Daylight QA").assertIsDisplayed()
                    if (!glass) {
                        compose.runOnIdle { AppSettings.setReduceDynamicBlur(true) }
                        compose.waitForIdle()
                        compose.onNodeWithText("Chrome check").assertIsDisplayed()
                        screenshot("$theme-solid")
                        compose.runOnIdle { AppSettings.setReduceDynamicBlur(false) }
                    }
                }
            }
        } finally { AppSettings.setReduceDynamicBlur(oldBlur) }
    }

    private fun screenshot(name: String) {
        // Capture the settled surface after Android's tap ripple has faded.
        compose.waitForIdle(); SystemClock.sleep(800)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file = File(context.getExternalFilesDir(null), "canary-15-chrome/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
