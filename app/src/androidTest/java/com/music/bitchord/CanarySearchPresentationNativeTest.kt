/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-12 through 2026-10-12.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord

import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.ui.theme.BitChordTheme
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.components.*
import com.music.bitchord.ui.components.floatingtabbar.FloatingTabBarScrollConnection
import dev.chrisbanes.haze.HazeState
import com.sh1vvy.daylight.search.*
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Deterministic presentation actions, without changing playback, history or an account. */
class CanarySearchPresentationNativeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val labels = SearchLabels("Search", "Recent", "Clear", "Search music", "No results", "Retry", "More", "Remove", "Matches")

    @Test fun playingDockBecomesSearchAndReturnsWithTransportIntact() {
        assumeTrue(inst.targetContext.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue(AuthStore(inst.targetContext).sessions.isEmpty())
        val active = mutableStateOf(false)
        val query = mutableStateOf("retained query")
        val connection = FloatingTabBarScrollConnection(scrollThresholdPx = 30f)
        val tabs = listOf(BottomTab("Home", Icons.Rounded.Home), BottomTab("Discover", Icons.Rounded.GridView),
            BottomTab("Library", Icons.Rounded.LibraryMusic), BottomTab("Search", Icons.Rounded.Search))
        var playTaps = 0
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                BitChordTheme(darkTheme = true) {
                    CompositionLocalProvider(LocalLiquidGlassEnabled provides false) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            SearchDock(active.value, query.value, "Search music", "Search", "Close",
                                listOf("YouTube Music", "Library"), 0, {}, "Clear", false, {},
                                { query.value = it }, {}, { active.value = false }, false,
                                modifier = Modifier.align(Alignment.BottomCenter)) {
                                PlayerNavigationBar(HazeState(), tabs, 0, { if (it == 3) active.value = true }, connection,
                                    Song("local:search-morph", "Transport retained", "Daylight QA", null), false, false,
                                    onPlayPause = { playTaps++ }, onNext = {}, onPrevious = {}, onExpand = {})
                            }
                        }
                    }
                }
            } }
            compose.waitForIdle()
            compose.onNodeWithText("Transport retained").assertIsDisplayed()
            screenshot("morph-normal")
            compose.mainClock.autoAdvance = false
            compose.onNodeWithContentDescription("Search").performClick()
            compose.mainClock.advanceTimeBy(96)
            screenshot("morph-opening-96ms")
            compose.mainClock.advanceTimeBy(112)
            screenshot("morph-opening-208ms")
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            compose.onNodeWithText("YouTube Music").assertIsDisplayed()
            compose.onNodeWithTag("daylight-search-editor").assertTextEquals("retained query")
            screenshot("morph-search")
            compose.mainClock.autoAdvance = false
            compose.onNodeWithContentDescription("Close").performClick()
            compose.mainClock.advanceTimeBy(96)
            screenshot("morph-closing-96ms")
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            compose.onNodeWithText("Transport retained").assertIsDisplayed()
            compose.onNodeWithContentDescription(inst.targetContext.getString(R.string.play)).performClick()
            compose.runOnIdle { assertEquals(1, playTaps) }
            screenshot("morph-restored")
            compose.runOnIdle { connection.inline() }
            compose.waitForIdle()
            screenshot("morph-normal-inline")
            compose.mainClock.autoAdvance = false
            compose.onNodeWithContentDescription("Search").performClick()
            compose.mainClock.advanceTimeBy(96)
            screenshot("morph-inline-opening-96ms")
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            compose.onNodeWithText("YouTube Music").assertIsDisplayed()
            compose.onNodeWithContentDescription("Close").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Transport retained").assertIsDisplayed()
            compose.onNodeWithText("Daylight QA").assertDoesNotExist()
            compose.onNodeWithContentDescription(inst.targetContext.getString(R.string.play)).performClick()
            compose.runOnIdle { assertEquals(2, playTaps) }
            screenshot("morph-inline-restored")
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = requireNotNull(inst.uiAutomation.takeScreenshot())
        val file = File(inst.targetContext.getExternalFilesDir(null), "canary-20-search/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun themedResultsRetainMenusTrackPlaybackBrowseAndSwipeQueueActions() {
        assumeTrue(inst.targetContext.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue(AuthStore(inst.targetContext).sessions.isEmpty())
        for (theme in listOf("dark", "light", "pink")) {
            var played = ""; var menu = ""; var queued = 0; var retried = 0
            val track = SearchHit("track", "Fixture track", "Daylight QA", null, SearchHitKind.TRACK, true)
            val album = SearchHit("album", "Fixture album", "Daylight QA", null, SearchHitKind.ALBUM)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    BitChordTheme(darkTheme = theme == "dark", pinkCloud = theme == "pink") {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            DaylightSearchResults("fixture", false, emptyList(), emptyList(), listOf(track, album), emptyList(),
                                false, false, null, "Offline page", labels, listOf("All", "Songs"), 0, {},
                                onHit = { played = it.key }, onMenu = { menu = it.key }, onQueue = { queued++ },
                                onHistory = {}, onRemoveHistory = {}, onClearHistory = {}, onSuggestion = {},
                                onLoadMore = {}, onRetry = {}, onRetryPage = { retried++ },
                                listState = rememberLazyListState(), scrollEpoch = 0,
                                padding = PaddingValues(top = 32.dp, bottom = 160.dp))
                        }
                    }
                } }
                compose.onNodeWithText("Fixture track").performClick()
                compose.runOnIdle { assertEquals("track", played) }
                compose.onAllNodesWithContentDescription("More")[0].performClick()
                compose.runOnIdle { assertEquals("track", menu) }
                compose.onNodeWithText("Fixture album").performClick()
                compose.runOnIdle { assertEquals("album", played) }
                compose.onNodeWithText("Fixture track").performTouchInput { swipeRight() }
                compose.waitForIdle()
                compose.runOnIdle { assertEquals("One queue action per swipe", 1, queued) }
                compose.onNodeWithText("Fixture track").assertIsDisplayed()
                compose.onNodeWithText("Retry").performClick()
                compose.runOnIdle { assertEquals(1, retried) }
                val bitmap = requireNotNull(inst.uiAutomation.takeScreenshot())
                val file = File(inst.targetContext.getExternalFilesDir(null), "canary-20-search/$theme-results.png")
                file.parentFile?.mkdirs(); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            }
        }
    }
}
