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
import androidx.compose.ui.unit.dp
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

    @Test fun quickPicksStayAtTheTopAndSnapToCenteredCoverWithCaptions() {
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue(AuthStore(context).sessions.isEmpty())
        val oldTheme = AppSettings.themeMode.value
        val oldGlass = AppSettings.liquidGlass.value
        val oldReduced = AppSettings.reduceAnimation.value
        val theme = InstrumentationRegistry.getArguments().getString("theme")?.let(ThemeMode::valueOf) ?: ThemeMode.DARK
        AppSettings.setThemeMode(theme)
        AppSettings.setLiquidGlass(InstrumentationRegistry.getArguments().getString("glass")?.toBooleanStrictOrNull() ?: oldGlass)
        AppSettings.setReduceAnimation(false)
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
                    AppSettings.setReduceAnimation(false)
                    model = ViewModelProvider(activity)[MainViewModel::class.java]
                    seedHome(model, listOf(discovery), loadingRecents = true)
                }
                compose.onNodeWithText("Quick picks").assertIsDisplayed()
                val first = compose.onNodeWithTag("quick-cover:qa-pick-1").fetchSemanticsNode().boundsInRoot
                val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
                assertEquals("The focused cover is centered", root.center.x, first.center.x, 2f)
                val frame = compose.onNodeWithTag("home-quick-picks-frame").fetchSemanticsNode().boundsInRoot
                val gutter = with(compose.density) { 16.dp.toPx() }
                assertEquals("Carousel left gutter matches the rest of Home", root.left + gutter, frame.left, 2f)
                assertEquals("Carousel right gutter matches the rest of Home", root.right - gutter, frame.right, 2f)
                compose.onNodeWithTag("quick-pick-title").assertTextEquals("Pick 1")
                compose.onNodeWithTag("quick-pick-artist").assertTextEquals("Daylight QA")
                compose.onNodeWithTag("quick-neighbor:left").assertExists()
                compose.onNodeWithTag("quick-neighbor:right").assertExists()
                val left = compose.onNodeWithTag("quick-neighbor:left").fetchSemanticsNode().boundsInRoot
                val right = compose.onNodeWithTag("quick-neighbor:right").fetchSemanticsNode().boundsInRoot
                assertTrue("Both smaller neighbours stay inside the Home gutters", left.left >= frame.left && right.right <= frame.right)
                assertTrue("The focused cover is larger than both neighbours", first.height > left.height * 1.2f && first.height > right.height * 1.2f)
                screenshot("quick-picks-$theme", verifyAtmosphere = true)
                val pager = compose.onNodeWithTag("home-quick-picks-pages")
                pager.performTouchInput { swipe(Offset(width * .85f, height * .5f), Offset(width * .20f, height * .5f), 650) }
                compose.waitForIdle()
                compose.onNodeWithTag("quick-pick-title").assertTextEquals("Pick 2")
                val next = compose.onNodeWithTag("quick-cover:qa-pick-2").fetchSemanticsNode().boundsInRoot
                assertEquals("The next cover snaps to the same center", first.center.x, next.center.x, 2f)
                compose.onNodeWithTag("quick-neighbor:left").assertExists()
                compose.onNodeWithTag("quick-neighbor:right").assertExists()
                screenshot("quick-picks-middle-$theme")
                pager.performTouchInput { swipe(Offset(width * .15f, height * .5f), Offset(width * .80f, height * .5f), 650) }
                compose.waitForIdle()
                scenario.onActivity { seedHome(model, listOf(HomeShelf("Recents", listOf(picks.first())), discovery), false) }
                compose.waitForIdle()
                compose.onAllNodesWithText("Recents").assertCountEquals(0)
                compose.onAllNodesWithText("Pick 1").assertCountEquals(1)
                val after = compose.onNodeWithTag("quick-cover:qa-pick-1").fetchSemanticsNode().boundsInRoot
                assertTrue("Late recents do not shift the section", kotlin.math.abs(after.top - first.top) < 2f)
                pager.performScrollToIndex(50_000 - 50_000 % picks.size + 11)
                compose.waitForIdle()
                compose.onNodeWithTag("quick-pick-title").assertTextEquals("Pick 12")
                assertEquals("The final cover can also be centered", first.center.x,
                    compose.onNodeWithTag("quick-cover:qa-pick-12").fetchSemanticsNode().boundsInRoot.center.x, 2f)
                pager.performTouchInput { swipe(Offset(width * .85f, height * .5f), Offset(width * .20f, height * .5f), 650) }
                compose.waitForIdle()
                compose.onNodeWithTag("quick-pick-title").assertTextEquals("Pick 1")
                pager.performTouchInput { swipe(Offset(width * .15f, height * .5f), Offset(width * .80f, height * .5f), 650) }
                compose.waitForIdle()
                compose.onNodeWithTag("quick-pick-title").assertTextEquals("Pick 12")
                screenshot("quick-picks-scrolled-$theme")
                compose.runOnIdle { AppSettings.setReduceAnimation(true) }
                pager.performTouchInput { swipe(Offset(width * .85f, height * .5f), Offset(width * .20f, height * .5f), 650) }
                compose.waitForIdle()
                compose.onNodeWithTag("quick-pick-title").assertTextEquals("Pick 1")
                assertTrue("Reduced-motion neighbours retain their smaller, padded fit",
                    compose.onNodeWithTag("quick-neighbor:left").fetchSemanticsNode().boundsInRoot.left >= frame.left &&
                        compose.onNodeWithTag("quick-neighbor:right").fetchSemanticsNode().boundsInRoot.right <= frame.right)
                screenshot("quick-picks-reduced-$theme")
            }
        } finally { cover.delete(); AppSettings.setThemeMode(oldTheme); AppSettings.setLiquidGlass(oldGlass); AppSettings.setReduceAnimation(oldReduced) }
    }

    @Test fun lateHomeRefreshKeepsTheWholePreparedFeedUntilReturnToTop() {
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue(AuthStore(context).sessions.isEmpty())
        val oldTheme = AppSettings.themeMode.value
        val oldGlass = AppSettings.liquidGlass.value
        val theme = InstrumentationRegistry.getArguments().getString("theme")?.let(ThemeMode::valueOf) ?: ThemeMode.DARK
        AppSettings.setThemeMode(theme)
        AppSettings.setLiquidGlass(InstrumentationRegistry.getArguments().getString("glass")?.toBooleanStrictOrNull() ?: oldGlass)
        val picks = (1..9).map { ShelfItem("Track $it", "QA", null, "pick-$it", null) }
        fun shelf(title: String) = HomeShelf(title, (1..4).map { ShelfItem("$title $it", "QA", null, null, "MPRE-$title-$it") })
        val initial = listOf(HomeShelf("Quick picks", picks), shelf("First collection"), shelf("Middle one"), shelf("Middle two"), shelf("Middle three"), shelf("Last collection"))
        try { ActivityScenario.launch(MainActivity::class.java).use { scenario ->
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
            compose.waitForIdle()
            compose.onAllNodesWithText("Next page").assertCountEquals(0)
            assertEquals("Even new tail shelves wait while the feed is being read", oldTop,
                compose.onNodeWithText("Last collection").fetchSemanticsNode().boundsInRoot.top, 2f)
            repeat(2) { vertical.performTouchInput { swipeUp() }; compose.waitForIdle() }
            val lastCardBottom = compose.onNodeWithText("Last collection 1").fetchSemanticsNode().boundsInRoot.bottom
            val controlsTop = compose.onNodeWithTag("floating-player-navigation").fetchSemanticsNode().boundsInRoot.top
            assertTrue("The feed ends near the folded controls without reserving an expanded bar",
                controlsTop - lastCardBottom < with(compose.density) { 72.dp.toPx() })
            assertTrue("The last collection remains clear of the controls", lastCardBottom < controlsTop)
            val retainedTop = compose.onNodeWithText("Last collection").fetchSemanticsNode().boundsInRoot.top
            scenario.onActivity {
                @Suppress("UNCHECKED_CAST")
                val library = field(model, "_library") as MutableStateFlow<UiState<com.music.bitchord.data.model.LibraryPage>>
                library.value = UiState.Success(com.music.bitchord.data.model.LibraryPage(
                    listOf(com.music.bitchord.data.model.Song("qa-liked", "Liked QA", "QA", null)), emptyList(), emptyList()))
                model.openLibraryArtist(ShelfItem("QA", "", "file:///qa-placeholder.png", null, "UCqa"))
            }
            compose.onNodeWithTag("library-artist-liked-list").assertIsDisplayed()
            androidx.test.espresso.Espresso.pressBack()
            compose.waitForIdle()
            compose.onAllNodesWithText("Next page").assertCountEquals(0)
            assertEquals("Returning from a pushed page retains the entire Home snapshot", retainedTop,
                compose.onNodeWithText("Last collection").fetchSemanticsNode().boundsInRoot.top, 2f)
            scenario.onActivity { seedHome(model, initial.take(2) + shelf("Inserted during refresh") + initial.drop(2), false) }
            vertical.performScrollToIndex(0)
            compose.waitForIdle()
            vertical.performScrollToNode(hasText("Inserted during refresh"))
            compose.onNodeWithText("Inserted during refresh").assertIsDisplayed()
        } } finally { AppSettings.setThemeMode(oldTheme); AppSettings.setLiquidGlass(oldGlass) }
    }

    @Test fun releaseSpeedControlsCoastingBeforeOneFinalSnap() {
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue(AuthStore(context).sessions.isEmpty())
        val oldReduced = AppSettings.reduceAnimation.value
        val picks = (1..12).map { ShelfItem("Fling pick $it", "QA", null, "qa-fling-$it", null) }
        val tracks = (1..12).map { ShelfItem("Fling row $it", "QA", null, "qa-row-$it", null) }
        try { ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                AppSettings.setReduceAnimation(false)
                seedHome(ViewModelProvider(it)[MainViewModel::class.java], listOf(
                    HomeShelf("Quick picks", picks), HomeShelf("Fling recommendations", tracks)), false)
            }
            val pager = compose.onNodeWithTag("home-quick-picks-pages")
            val firstPage = 50_000 - 50_000 % picks.size
            val first = compose.onNodeWithTag("quick-cover:qa-fling-1").fetchSemanticsNode().boundsInRoot
            fun flick(speed: Float, duration: Long): Int {
                pager.performScrollToIndex(firstPage)
                pager.performTouchInput { swipeWithVelocity(Offset(width * .72f, height * .5f),
                    Offset(width * .30f, height * .5f), first.width * speed, duration) }
                compose.waitForIdle()
                val title = compose.onNodeWithTag("quick-pick-title").fetchSemanticsNode()
                    .config[SemanticsProperties.Text].single().text
                val moved = title.substringAfterLast(' ').toInt() - 1
                val centered = compose.onNodeWithTag("quick-cover:qa-fling-${moved + 1}")
                    .fetchSemanticsNode().boundsInRoot
                assertEquals("Every release speed settles on the same center", first.center.x, centered.center.x, 2f)
                return moved
            }
            val gentle = flick(.45f, 650)
            val medium = flick(6f, 180)
            val fast = flick(10f, 120)
            val extreme = flick(20f, 60)
            assertEquals("A gentle swipe advances one recommendation", 1, gentle)
            assertTrue("A faster release coasts instead of stopping at every cover: $gentle / $medium / $fast / $extreme",
                medium >= gentle && fast > gentle && fast >= medium && extreme >= fast && extreme <= 3)

            val vertical = compose.onNodeWithTag("home-feed")
            vertical.performScrollToNode(hasText("Fling recommendations"))
            val row = compose.onNodeWithTag("home-track-pages")
            fun rowFlick(speed: Float, duration: Long): Int {
                row.performScrollToIndex(0)
                row.performTouchInput { swipeWithVelocity(Offset(width * .85f, height * .5f),
                    Offset(width * .25f, height * .5f), width * speed, duration) }
                compose.waitForIdle()
                val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
                val gutter = with(compose.density) { 16.dp.toPx() }
                // The merged song-row semantics starts at the column's outer gutter.
                val columns = listOf(1, 4, 7, 10)
                val snapped = columns.firstOrNull { index ->
                    compose.onAllNodesWithText("Fling row $index").fetchSemanticsNodes().any { node ->
                        kotlin.math.abs(node.boundsInRoot.left - root.left - gutter) < 3f
                    }
                }
                assertTrue("Row must settle on a complete column", snapped != null)
                return columns.indexOf(snapped)
            }
            val gentleRow = rowFlick(.35f, 650)
            val fastRow = rowFlick(8f, 120)
            assertEquals("A gentle row swipe advances one column", 1, gentleRow)
            assertTrue("A fast row swipe coasts through several columns", fastRow > gentleRow && fastRow <= 3)
            screenshot("home-fling-row-settled")
            println("Home fling distances: covers=$gentle/$medium/$fast/$extreme, columns=$gentleRow/$fastRow")
        } } finally { AppSettings.setReduceAnimation(oldReduced) }
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
    private fun screenshot(name: String, verifyAtmosphere: Boolean = false) {
        compose.waitForIdle(); SystemClock.sleep(200)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        if (verifyAtmosphere) {
            // Sample the empty page gutter, outside covers and floating controls.
            // This catches an opaque Home floor hiding the shared theme wash,
            // including when the page is captured by the real Glass renderer.
            val x = (bitmap.width * .015f).toInt()
            val upper = bitmap.getPixel(x, (bitmap.height * .35f).toInt())
            val lower = bitmap.getPixel(x, (bitmap.height * .65f).toInt())
            val difference = kotlin.math.abs(Color.red(upper) - Color.red(lower)) +
                kotlin.math.abs(Color.green(upper) - Color.green(lower)) +
                kotlin.math.abs(Color.blue(upper) - Color.blue(lower))
            assertTrue("Home's gradient must remain visible behind the feed ($upper/$lower)", difference > 15)
        }
        val out = File(context.getExternalFilesDir(null), "canary-6-qa/$name.png")
        out.parentFile?.mkdirs()
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
