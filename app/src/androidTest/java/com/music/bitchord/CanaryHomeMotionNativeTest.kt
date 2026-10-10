@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.music.bitchord

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.ViewModelProvider
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.canvas.CanvasCache
import com.music.bitchord.data.canvas.CanvasRepository
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.LibraryViewType
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.data.spotify.LocalPlaylistStore
import com.music.bitchord.ui.MainViewModel
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Home routes, with frozen opening frames and a cached silent cover clip. */
@RunWith(AndroidJUnit4::class)
class CanaryHomeMotionNativeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val automation get() = instrumentation.uiAutomation

    @Test
    fun homeAlbumsAndPlaylistsGrowFromTheirCardsAndVideoWaitsUntilOpeningEnds() {
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue(AuthStore(context).sessions.isEmpty())
        assumeTrue(LocalPlaylistStore.playlists.value.isEmpty())
        val oldTheme = AppSettings.themeMode.value
        val oldReduced = AppSettings.reduceAnimation.value
        val oldCanvas = AppSettings.animatedCanvas.value
        val oldRecents = AppSettings.homeRecentsViewType.value
        AppSettings.setThemeMode(ThemeMode.DARK)
        AppSettings.setReduceAnimation(false)
        AppSettings.setAnimatedCanvas(true)
        val cover = File(context.cacheDir, "canary-home-cover.png")
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(184, 104, 137))
            drawCircle(200f, 200f, 115f, Paint().apply { color = Color.rgb(250, 213, 168) })
            drawCircle(200f, 200f, 30f, Paint().apply { color = Color.rgb(101, 75, 111) })
        }
        cover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val otherCover = File(context.cacheDir, "canary-home-other-cover.png")
        Canvas(bitmap).drawColor(Color.rgb(80, 110, 160))
        otherCover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val art = cover.toURI().toString()
        val song = Song("qa-home-motion", "Home motion track", "Daylight QA", art, "3:00")
        val playlist = LocalPlaylistStore.savePlaylist("Home motion playlist", listOf(song))
        val album = ShelfItem("Home motion album", "Daylight QA", art, null, "MPREb_canary4_motion")
        val playlistCard = ShelfItem(playlist.title, "Daylight QA", art, null, playlist.browseId)

        // Seed the existing caches, scoped to a unique offline fixture. No real
        // login, provider lookup or stream is needed to reproduce a fast video hit.
        val clipUrl = "https://daylight.invalid/canary4/${System.nanoTime()}.mp4"
        val cache = CanvasCache::class.java.getDeclaredField("cache").apply { isAccessible = true }
            .get(CanvasCache) as SimpleCache
        val bytes = instrumentation.context.assets.open("canary-motion-cover.mp4").use { it.readBytes() }
        val hole = requireNotNull(cache.startReadWrite(clipUrl, 0, bytes.size.toLong()))
        try {
            cache.startFile(clipUrl, 0, bytes.size.toLong()).apply { writeBytes(bytes) }.let {
                cache.commitFile(it, bytes.size.toLong())
            }
            cache.applyContentMetadataMutations(clipUrl, ContentMetadataMutations().also {
                ContentMetadataMutations.setContentLength(it, bytes.size.toLong())
            })
        } finally { cache.releaseHoleSpan(hole) }
        val key = "album|${album.title}|${album.subtitle}"
        @Suppress("UNCHECKED_CAST")
        val canvases = CanvasRepository::class.java.getDeclaredField("cache").apply { isAccessible = true }
            .get(CanvasRepository) as MutableMap<String, Any>
        val entryClass = CanvasRepository::class.java.declaredClasses.first { it.simpleName == "Entry" }
        val entry = entryClass.declaredConstructors.single().apply { isAccessible = true }
            .newInstance(CanvasArtwork(clipUrl), true)
        val priorEntry = synchronized(canvases) { canvases.put(key, entry) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var model: MainViewModel
                scenario.onActivity { activity ->
                    model = ViewModelProvider(activity)[MainViewModel::class.java]
                    seedHome(model, listOf(HomeShelf("Motion albums", listOf(album))))
                }
                waitNode { text(it, album.title) }
                screenshot("home-hero-before")
                checkOpening(scenario, album.title, "home-hero-album") {
                    // A recommendation arriving mid-flight would otherwise turn
                    // the hero into a compact shelf and dispose its source key.
                    scenario.onActivity {
                        @Suppress("UNCHECKED_CAST")
                        val recommendations = field(model, "_homeQuickRecommendations") as MutableStateFlow<List<ShelfItem>>
                        recommendations.value = listOf(ShelfItem("Late discovery", "Daylight QA", null, "qa-late-discovery", null))
                    }
                }
                assertEquals(album.browseId, model.detailStack.value.single().browseId)
                assertEquals(BrowseType.ALBUM, model.detailStack.value.single().type)
                val deadline = SystemClock.uptimeMillis() + 6_000
                while (textureCount(scenario) == 0 && SystemClock.uptimeMillis() < deadline) {
                    compose.waitForIdle(); SystemClock.sleep(50)
                }
                assertEquals("The animated sleeve mounts after the opening", 1, textureCount(scenario))
                SystemClock.sleep(500)
                compose.waitForIdle()
                screenshot("home-album-video-settled")
                compose.mainClock.autoAdvance = false
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                instrumentation.waitForIdleSync()
                compose.mainClock.advanceTimeBy(128)
                compose.waitForIdle()
                SystemClock.sleep(64)
                screenshot("home-hero-return-middle")
                assertTrue("Late discoveries wait until the original card has returned",
                    find(automation.rootInActiveWindow) { text(it, "Late discovery") } == null)
                compose.mainClock.autoAdvance = true
                compose.waitForIdle()
                waitNode { text(it, "Late discovery") }
                waitNode { text(it, album.title) }
                assertTrue(model.detailStack.value.isEmpty())

                // Recents in rows and cards, then a regular square Home shelf.
                // The same collection may occur twice; only the tapped occurrence moves.
                for (view in listOf(LibraryViewType.LIST, LibraryViewType.GRID)) {
                    AppSettings.setHomeRecentsViewType(view)
                    scenario.onActivity { seedHome(model, listOf(HomeShelf("Recents", listOf(playlistCard)))) }
                    waitNode { text(it, playlist.title) }
                    screenshot("home-recents-${view.name}-before")
                    checkOpening(scenario, playlist.title, "home-recents-${view.name}")
                    assertEquals(playlist.browseId, model.detailStack.value.single().browseId)
                    waitNode { text(it, song.title) }
                    back()
                    waitNode { text(it, playlist.title) }
                    assertTrue(model.detailStack.value.isEmpty())
                }
                scenario.onActivity { seedHome(model, listOf(
                    HomeShelf("Recents", emptyList()), HomeShelf("Keep listening", listOf(playlistCard,
                        playlistCard.copy(thumbnailUrl = otherCover.toURI().toString()))),
                )) }
                waitNode { text(it, playlist.title) }
                screenshot("home-square-before")
                checkOpening(scenario, playlist.title, "home-square-playlist")
                assertEquals(playlist.browseId, model.detailStack.value.single().browseId)
                back()
                waitNode { text(it, playlist.title) }
                assertTrue(model.detailStack.value.isEmpty())
                screenshot("home-square-returned")
            }
        } finally {
            compose.mainClock.autoAdvance = true
            synchronized(canvases) { if (priorEntry == null) canvases.remove(key) else canvases[key] = priorEntry }
            cache.removeResource(clipUrl)
            LocalPlaylistStore.deletePlaylist(playlist.id)
            val prefs = context.getSharedPreferences("bitchord_local_playlists", android.content.Context.MODE_PRIVATE)
            val deadline = SystemClock.uptimeMillis() + 3_000
            while (prefs.getString("playlists_json", "[]") != "[]" && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
            cover.delete()
            otherCover.delete()
            AppSettings.setThemeMode(oldTheme)
            AppSettings.setReduceAnimation(oldReduced)
            AppSettings.setAnimatedCanvas(oldCanvas)
            AppSettings.setHomeRecentsViewType(oldRecents)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun seedHome(model: MainViewModel, shelves: List<HomeShelf>) {
        // Stop any late provider result replacing the offline screen while measuring it.
        (field(model, "homeLoadGeneration") as AtomicLong).incrementAndGet()
        (field(model, "homeRecommendationsJob") as? kotlinx.coroutines.Job)?.cancel()
        MainViewModel::class.java.getDeclaredField("homeContinuation").apply { isAccessible = true }.set(model, null)
        (field(model, "_homeQuickRecommendations") as MutableStateFlow<List<ShelfItem>>).value = emptyList()
        (field(model, "_homePendingShelves") as MutableStateFlow<Int>).value = 0
        (field(model, "_homeRecentlyPlayedLoading") as MutableStateFlow<Boolean>).value = false
        (field(model, "_home") as MutableStateFlow<UiState<List<HomeShelf>>>).value = UiState.Success(shelves)
    }
    private fun field(model: MainViewModel, name: String) = MainViewModel::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.get(model)

    private fun checkOpening(scenario: ActivityScenario<MainActivity>, title: String, name: String, afterEarlyFrame: () -> Unit = {}) {
        compose.mainClock.autoAdvance = false
        var node: AccessibilityNodeInfo? = waitNode { text(it, title) }
        while (node?.isClickable != true) node = node?.parent ?: error("Missing collection click")
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        SystemClock.sleep(64)
        val early = screenshot("$name-early")
        assertEquals("Video cannot cover the expanding still", 0, textureCount(scenario))
        afterEarlyFrame()
        compose.mainClock.advanceTimeBy(128)
        compose.waitForIdle()
        SystemClock.sleep(64)
        val middle = screenshot("$name-middle")
        assertEquals("Video must wait for navigation to finish", 0, textureCount(scenario))
        // The sharp overlay now includes the whole tall Home sleeve (rather than
        // only the old scrim's unmasked colour). Check its path and a later size
        // while the shared transition is still running.
        assertTrue("The Home cover travels toward its header ($early → $middle)", middle.top < early.top - 40)
        compose.mainClock.advanceTimeBy(160)
        compose.waitForIdle()
        SystemClock.sleep(64)
        val later = screenshot("$name-later")
        assertEquals("Video must stay off the moving cover", 0, textureCount(scenario))
        // Near the header the status-bar fade hides the fixture's exact pink
        // top edge; its detected top can stop moving before the artwork does.
        assertTrue("The Home cover grows across intermediate frames ($early → $middle → $later)", middle.width() > early.width() + 24 && later.top <= middle.top)
        val titleBounds = compose.onNodeWithTag("detail-release-title").fetchSemanticsNode().boundsInRoot
        val frame = requireNotNull(automation.takeScreenshot())
        assertTrue("Home release title stays above the expanding cover before it settles",
            NativeCoverFrames.titleIsVisible(frame, Rect(titleBounds.left.toInt(), titleBounds.top.toInt(), titleBounds.right.toInt(), titleBounds.bottom.toInt()), AppSettings.themeMode.value == ThemeMode.DARK))
        frame.recycle()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }
    private fun textureCount(scenario: ActivityScenario<MainActivity>): Int {
        var count = 0
        fun visit(view: View) {
            if (view is TextureView) count++
            if (view is ViewGroup) repeat(view.childCount) { visit(view.getChildAt(it)) }
        }
        scenario.onActivity { visit(it.window.decorView) }
        return count
    }
    private fun text(node: AccessibilityNodeInfo, label: String) = node.text?.toString()?.lineSequence()?.any { it == label } == true
    private fun find(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        node ?: return null
        if (node.isVisibleToUser && predicate(node)) return node
        repeat(node.childCount) { find(node.getChild(it), predicate)?.let { match -> return match } }
        return null
    }
    private fun waitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 8_000
        do {
            if (compose.mainClock.autoAdvance) compose.waitForIdle()
            find(automation.rootInActiveWindow, predicate)?.let { return it }
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Missing Home collection")
    }
    private fun back() {
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        SystemClock.sleep(300)
    }
    private fun screenshot(name: String): Rect {
        val bitmap = requireNotNull(automation.takeScreenshot())
        val out = File(context.getExternalFilesDir(null), "canary-4-qa/$name.png")
        out.parentFile?.mkdirs()
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val sleeve = NativeCoverFrames.widestFixtureSleeve(bitmap)
        if (name.endsWith("-early")) assertTrue("Home opening keeps the card's rounded corners ($sleeve)",
            NativeCoverFrames.hasRoundedTopCorners(bitmap, sleeve))
        if (name.endsWith("-middle") || name.endsWith("-later")) {
            assertTrue("The Home sleeve stays centered while growing ($sleeve)",
                if (name.endsWith("-later") || name.contains("return")) NativeCoverFrames.hasFixtureHubAtHeaderCenter(bitmap) else NativeCoverFrames.hasCenteredFixtureHub(bitmap, sleeve))
        }
        bitmap.recycle()
        return sleeve
    }
}
