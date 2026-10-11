/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-10 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.ui.MainViewModel
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Loaded recommendations reuse real Home rendering without fetching or playing a stream. */
@RunWith(AndroidJUnit4::class)
class CanaryQuickPicksNativeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun quickPicksStayAtTheTopWhenRecentsFinishAndPageInThreeRows() {
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue(AuthStore(context).sessions.isEmpty())
        val oldTheme = AppSettings.themeMode.value
        val theme = InstrumentationRegistry.getArguments().getString("theme")?.let(ThemeMode::valueOf) ?: ThemeMode.DARK
        AppSettings.setThemeMode(theme)
        val cover = File(context.cacheDir, "canary6-picks.png")
        val bitmap = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(174, 118, 142))
            drawCircle(120f, 120f, 80f, Paint().apply { color = Color.rgb(246, 211, 177) })
        }
        cover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val picks = (1..12).map { ShelfItem("Pick $it", "Daylight QA", cover.toURI().toString(), "qa-pick-$it", null) }
        val discovery = HomeShelf("Quick picks", picks)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var model: MainViewModel
                scenario.onActivity { activity ->
                    model = ViewModelProvider(activity)[MainViewModel::class.java]
                    seedHome(model, listOf(discovery), loadingRecents = true)
                }
                compose.onNodeWithText("Quick picks").assertIsDisplayed()
                val first = compose.onNodeWithText("Pick 1").fetchSemanticsNode().boundsInRoot
                val second = compose.onNodeWithText("Pick 2").fetchSemanticsNode().boundsInRoot
                val third = compose.onNodeWithText("Pick 3").fetchSemanticsNode().boundsInRoot
                assertTrue(first.left == second.left && second.left == third.left)
                assertTrue(first.bottom < second.top && second.bottom < third.top)
                screenshot("quick-picks-$theme")
                val pager = compose.onNodeWithTag("home-quick-picks-pages")
                pager.performTouchInput { swipe(Offset(width * .85f, height * .5f), Offset(width * .20f, height * .5f), 650) }
                compose.waitForIdle()
                val next = compose.onNodeWithText("Pick 4").fetchSemanticsNode().boundsInRoot
                assertEquals("Three-song columns snap to the same leading gutter", first.left, next.left, 2f)
                compose.onNodeWithText("Pick 5").assertIsDisplayed(); compose.onNodeWithText("Pick 6").assertIsDisplayed()
                scenario.onActivity { seedHome(model, listOf(discovery.copy(items = picks.drop(3) + picks.take(3))), false) }
                compose.waitForIdle()
                assertEquals("Refreshing picks keeps the selected column aligned", first.left,
                    compose.onNodeWithText("Pick 7").fetchSemanticsNode().boundsInRoot.left, 2f)
                scenario.onActivity { seedHome(model, listOf(discovery), false) }
                compose.waitForIdle()
                pager.performTouchInput { swipe(Offset(width * .15f, height * .5f), Offset(width * .80f, height * .5f), 650) }
                compose.waitForIdle()
                scenario.onActivity { seedHome(model, listOf(HomeShelf("Recents", listOf(picks.first())), discovery), false) }
                compose.waitForIdle()
                compose.onAllNodesWithText("Recents").assertCountEquals(0)
                compose.onAllNodesWithText("Pick 1").assertCountEquals(1)
                val after = compose.onNodeWithText("Pick 1").fetchSemanticsNode().boundsInRoot
                assertTrue("Late recents do not shift the section", kotlin.math.abs(after.top - first.top) < 2f)
                repeat(3) {
                    pager.performTouchInput { swipe(Offset(width * .85f, height * .5f), Offset(width * .20f, height * .5f), 650) }
                    compose.waitForIdle()
                }
                assertEquals("The final column uses the same leading gutter", first.left,
                    compose.onNodeWithText("Pick 10").fetchSemanticsNode().boundsInRoot.left, 2f)
                compose.onNodeWithText("Pick 11").assertIsDisplayed()
                compose.onNodeWithText("Pick 12").assertIsDisplayed()
                screenshot("quick-picks-scrolled-$theme")
            }
        } finally { cover.delete(); AppSettings.setThemeMode(oldTheme) }
    }

    @Test fun lateHomeRefreshWaitsForTopWhilePaginationExtendsTheBottom() {
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue(AuthStore(context).sessions.isEmpty())
        val picks = (1..9).map { ShelfItem("Track $it", "QA", null, "pick-$it", null) }
        fun shelf(title: String) = HomeShelf(title, (1..4).map { ShelfItem("$title $it", "QA", null, null, "MPRE-$title-$it") })
        val initial = listOf(HomeShelf("Quick picks", picks), shelf("First collection"), shelf("Middle one"), shelf("Middle two"), shelf("Middle three"), shelf("Last collection"))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: MainViewModel
            scenario.onActivity { model = ViewModelProvider(it)[MainViewModel::class.java]; seedHome(model, initial, false) }
            val vertical = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).onFirst()
            vertical.performScrollToIndex(initial.size)
            compose.onNodeWithText("Last collection").assertIsDisplayed()
            assertTrue(vertical.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
            val oldTop = compose.onNodeWithText("Last collection").fetchSemanticsNode().boundsInRoot.top
            scenario.onActivity { seedHome(model, initial.take(2) + shelf("Inserted during refresh") + initial.drop(2), false) }
            compose.waitForIdle()
            assertEquals("A refresh cannot move the section being read", oldTop,
                compose.onNodeWithText("Last collection").fetchSemanticsNode().boundsInRoot.top, 2f)
            compose.onAllNodesWithText("Inserted during refresh").assertCountEquals(0)
            scenario.onActivity { seedHome(model, initial + shelf("Next page"), false) }
            vertical.performScrollToNode(hasText("Next page"))
            compose.onNodeWithText("Next page").assertIsDisplayed()
            scenario.onActivity { seedHome(model, initial.take(2) + shelf("Inserted during refresh") + initial.drop(2), false) }
            vertical.performScrollToIndex(0)
            compose.waitForIdle()
            vertical.performScrollToNode(hasText("Inserted during refresh"))
            compose.onNodeWithText("Inserted during refresh").assertIsDisplayed()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun seedHome(model: MainViewModel, shelves: List<HomeShelf>, loadingRecents: Boolean) {
        (field(model, "homeLoadGeneration") as AtomicLong).incrementAndGet()
        (field(model, "_homePendingShelves") as MutableStateFlow<Int>).value = 0
        (field(model, "_homeRecentlyPlayedLoading") as MutableStateFlow<Boolean>).value = loadingRecents
        (field(model, "_home") as MutableStateFlow<UiState<List<HomeShelf>>>).value = UiState.Success(shelves)
    }
    private fun field(model: MainViewModel, name: String) = MainViewModel::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.get(model)
    private fun screenshot(name: String) {
        compose.waitForIdle(); SystemClock.sleep(200)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val out = File(context.getExternalFilesDir(null), "canary-6-qa/$name.png")
        out.parentFile?.mkdirs()
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
