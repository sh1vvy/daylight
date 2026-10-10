package com.music.bitchord

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.spotify.LocalPlaylistStore
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.ui.MainViewModel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith

/** Real Library rows/grid, detail loading and Back, using offline fixture playlists. */
@RunWith(AndroidJUnit4::class)
class CanaryLibraryMotionNativeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val automation get() = instrumentation.uiAutomation

    @Test
    fun coversOpenAndReturnFromBothLibraryLayoutsWithoutLosingTheViewport() {
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue("Do not touch a real account", AuthStore(context).sessions.isEmpty())
        val previousFixtures = LocalPlaylistStore.playlists.value
        assumeTrue("Do not touch a real library", previousFixtures.all { playlist ->
            playlist.title.matches(Regex("Canary [1-7]")) && playlist.songs.size == 30 &&
                playlist.songs.all { it.videoId.startsWith("qa-motion-") && it.artist == "Daylight QA" }
        })
        previousFixtures.forEach { LocalPlaylistStore.deletePlaylist(it.id) }
        awaitFixtureRemoval()
        val oldTheme = AppSettings.themeMode.value
        val oldGlass = AppSettings.liquidGlass.value
        val oldReduced = AppSettings.reduceAnimation.value
        val args = InstrumentationRegistry.getArguments()
        val glass = args.getString("glass") == "true"
        val reduced = args.getString("reduce") == "true"
        val theme = args.getString("theme")?.let(ThemeMode::valueOf) ?: ThemeMode.DARK
        AppSettings.setThemeMode(theme)
        AppSettings.setLiquidGlass(glass)
        AppSettings.setReduceAnimation(reduced)
        val cover = File(context.cacheDir, "canary-library-cover.png")
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(184, 104, 137))
            drawCircle(200f, 200f, 115f, Paint().apply { color = Color.rgb(250, 213, 168) })
            drawCircle(200f, 200f, 30f, Paint().apply { color = Color.rgb(101, 75, 111) })
        }
        cover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val otherCover = File(context.cacheDir, "canary-library-other-cover.png")
        Canvas(bitmap).drawColor(Color.rgb(80, 110, 160))
        otherCover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val songs = (1..30).map { Song("qa-motion-$it", "Canary track $it", "Daylight QA", cover.toURI().toString(), "3:00") }
        val playlists = (1..7).map { index -> LocalPlaylistStore.savePlaylist("Canary $index",
            if (index == 7) songs else songs.map { it.copy(thumbnailUrl = otherCover.toURI().toString()) }) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var model: MainViewModel? = null
                scenario.onActivity { model = ViewModelProvider(it)[MainViewModel::class.java] }
                clickNode("Library tab") {
                    it.contentDescription?.toString() == context.getString(R.string.library) &&
                        bounds(it).centerY() > context.resources.displayMetrics.heightPixels / 2
                }
                waitNode("newest playlist") { text(it, "Canary 7") }
                screenshot("library-$theme-glass-$glass-reduced-$reduced")
                val originalTop = cardBounds("Canary 7").top
                if (!reduced) compose.mainClock.autoAdvance = false
                clickText("Canary 7", settle = false)
                if (!reduced) {
                    instrumentation.waitForIdleSync()
                    compose.mainClock.advanceTimeByFrame()
                    compose.waitForIdle()
                    compose.mainClock.advanceTimeByFrame()
                    compose.mainClock.advanceTimeBy(64)
                    compose.waitForIdle()
                    SystemClock.sleep(64) // Let Android's renderer present the frozen Compose frame.
                    val early = screenshot("opening-early-$theme-glass-$glass", settle = false)
                    compose.mainClock.advanceTimeBy(128)
                    compose.waitForIdle()
                    SystemClock.sleep(64)
                    val middle = screenshot("opening-middle-$theme-glass-$glass", settle = false, requireCover = true)
                    if (theme == ThemeMode.DARK) {
                        assertTrue("The opening cover grows between frames ($early → $middle)", middle.width() > early.width() + 24)
                        assertTrue("The cover travels from its Library card ($early → $middle)", middle.centerY() < early.centerY() - 40)
                    }
                    compose.mainClock.advanceTimeBy(160)
                    compose.waitForIdle()
                    SystemClock.sleep(64)
                    screenshot("opening-later-$theme-glass-$glass", settle = false)
                    val titleBounds = compose.onNodeWithTag("detail-release-title").fetchSemanticsNode().boundsInRoot
                    val frame = requireNotNull(automation.takeScreenshot())
                    assertTrue("The release title is above the cover before the handoff",
                        NativeCoverFrames.titleIsVisible(frame, Rect(titleBounds.left.toInt(), titleBounds.top.toInt(), titleBounds.right.toInt(), titleBounds.bottom.toInt()), theme == ThemeMode.DARK))
                    frame.recycle()
                    compose.mainClock.autoAdvance = true
                    compose.waitForIdle()
                }
                waitNode("loaded playlist") { text(it, "Canary track 1") }
                assertEquals(1, model!!.detailStack.value.size)
                assertEquals(playlists.last().browseId, model!!.detailStack.value.single().browseId)
                screenshot("detail-$theme-glass-$glass-reduced-$reduced")
                scrollForward()
                back()
                waitNode("library returned") { text(it, context.getString(R.string.library)) }
                assertTrue(model!!.detailStack.value.isEmpty())
                screenshot("return-viewport-$theme-glass-$glass-reduced-$reduced")
                val returnedTop = cardBounds("Canary 7").top
                assertTrue("Returning keeps the Library viewport ($originalTop → $returnedTop)", kotlin.math.abs(originalTop - returnedTop) < 16)

                clickText(context.getString(R.string.show_all))
                waitNode("playlist grid") { text(it, "Canary 3") }
                screenshot("grid-$theme-glass-$glass-reduced-$reduced")
                clickText("Canary 7")
                waitNode("grid playlist loaded") { text(it, "Canary track 1") }
                assertEquals(1, model!!.detailStack.value.size)
                back()
                waitNode("grid restored") { text(it, "Canary 3") }
                assertTrue(model!!.detailStack.value.isEmpty())
                // A different card must get its own cover/visit and a fresh detail viewport.
                clickText("Canary 6")
                waitNode("second playlist loaded") { text(it, "Canary track 1") }
                assertEquals(playlists[5].browseId, model!!.detailStack.value.single().browseId)
                back()
                waitNode("grid remains active") { text(it, "Canary 3") }
                back()
                waitNode("Library feed restored") { text(it, context.getString(R.string.library)) }
                // The preview has two rows and a short extra column.
                // Show all remains the destination for the full collection.
                val sidewaysLeft = cardBounds("Canary 4").left
                clickText("Canary 4")
                waitNode("second shelf playlist loaded") { text(it, "Canary track 1") }
                back()
                val restoredLeft = cardBounds("Canary 4").left
                assertTrue("Returning keeps the shelf position ($sidewaysLeft → $restoredLeft)",
                    kotlin.math.abs(sidewaysLeft - restoredLeft) < 16)
                screenshot("returned-$theme-glass-$glass-reduced-$reduced")
            }
        } finally {
            compose.mainClock.autoAdvance = true
            playlists.forEach { LocalPlaylistStore.deletePlaylist(it.id) }
            // The production writer is asynchronous. Finish its cleanup before
            // instrumentation kills this process or the next theme run starts.
            awaitFixtureRemoval()
            cover.delete()
            otherCover.delete()
            AppSettings.setLiquidGlass(oldGlass)
            AppSettings.setReduceAnimation(oldReduced)
            AppSettings.setThemeMode(oldTheme)
        }
    }

    private fun text(node: AccessibilityNodeInfo, label: String) =
        node.text?.toString()?.lineSequence()?.any { it == label } == true
    private fun awaitFixtureRemoval() {
        val prefs = context.getSharedPreferences("bitchord_local_playlists", android.content.Context.MODE_PRIVATE)
        val deadline = SystemClock.uptimeMillis() + 3_000
        while (prefs.getString("playlists_json", "[]") != "[]" && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(50)
        }
        assertEquals("Native fixtures were persisted as removed", "[]", prefs.getString("playlists_json", "[]"))
    }
    private fun bounds(node: AccessibilityNodeInfo) = Rect().also(node::getBoundsInScreen)
    // The departing detail header has the same title. Measure the Library's
    // long-press card, rather than accidentally matching that outgoing header.
    private fun cardOwner(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var candidate: AccessibilityNodeInfo? = node
        while (candidate != null) {
            if (candidate.actionList.any { it.id == AccessibilityNodeInfo.ACTION_LONG_CLICK }) return candidate
            candidate = candidate.parent
        }
        return null
    }
    private fun cardBounds(label: String): Rect = bounds(requireNotNull(cardOwner(
        waitNode("Library card $label") { text(it, label) && cardOwner(it) != null },
    )))
    private fun find(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        node ?: return null
        if (node.isVisibleToUser && predicate(node)) return node
        repeat(node.childCount) { find(node.getChild(it), predicate)?.let { found -> return found } }
        return null
    }
    private fun waitNode(label: String, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 8_000
        do {
            if (compose.mainClock.autoAdvance) compose.waitForIdle()
            find(automation.rootInActiveWindow, predicate)?.let { return it }
            SystemClock.sleep(80)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Missing native Library control: $label")
    }
    private fun clickText(label: String, settle: Boolean = true) = clickNode(label, settle) { text(it, label) }
    private fun clickNode(label: String, settle: Boolean = true, predicate: (AccessibilityNodeInfo) -> Boolean) {
        var target: AccessibilityNodeInfo? = waitNode(label, predicate)
        while (target != null) {
            if (target.isEnabled && target.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK } &&
                target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                if (settle) { compose.waitForIdle(); SystemClock.sleep(500) }
                return
            }
            target = target.parent
        }
        throw AssertionError("Could not click $label")
    }
    private fun scrollForward() {
        assertTrue(waitNode("detail list") { it.isScrollable }.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
        compose.waitForIdle()
        SystemClock.sleep(350)
    }
    private fun back() {
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        SystemClock.sleep(500)
    }
    private fun screenshot(name: String, settle: Boolean = true, requireCover: Boolean = false): Rect {
        if (settle) { compose.waitForIdle(); SystemClock.sleep(500) }
        val bitmap = requireNotNull(automation.takeScreenshot())
        val output = File(context.getExternalFilesDir(null), "canary-4-qa/$name.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (requireCover) {
            var coverPixels = 0
            for (y in 0 until bitmap.height step 16) for (x in 0 until bitmap.width step 16) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.red(pixel) > 95 && Color.red(pixel) > Color.green(pixel) * 1.1 &&
                    Color.blue(pixel) > 60) coverPixels++
            }
            assertTrue("A transition must retain the sleeve instead of a blank frame ($coverPixels samples)", coverPixels > 100)
        }
        val coverBounds = NativeCoverFrames.widestFixtureSleeve(bitmap)
        if (name.startsWith("opening-early-") || name.startsWith("library-")) assertTrue("Library keeps the card's rounded corners ($coverBounds)",
            NativeCoverFrames.hasRoundedTopCorners(bitmap, coverBounds))
        if (requireCover) assertTrue("The whole sleeve scales around its center ($coverBounds)",
            NativeCoverFrames.hasCenteredFixtureHub(bitmap, coverBounds))
        bitmap.recycle()
        return coverBounds
    }
}
