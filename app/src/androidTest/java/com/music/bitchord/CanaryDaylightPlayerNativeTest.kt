/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.SystemClock
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.canvas.CanvasCache
import com.music.bitchord.data.canvas.CanvasRepository
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricWord
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.MixBlend
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.ui.player.PlayerExperienceScreen
import com.music.bitchord.ui.theme.BitChordTheme
import com.sh1vvy.daylight.player.PlayerExperience
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanaryDaylightPlayerNativeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val position = PlaybackPosition().apply { report(1_200) }
    private val taps = AtomicInteger()
    private var seek = -1L
    private var jumped = -1
    private var removed = -1
    private var mode by mutableStateOf(PlayerExperience.DAYLIGHT)
    private var playing by mutableStateOf(false)
    private var missing by mutableStateOf(false)
    private var landscape by mutableStateOf(false)
    private var dark by mutableStateOf(true)
    private var pink by mutableStateOf(false)
    private var fontScale by mutableFloatStateOf(1f)
    private val lines = (0..16).map { LyricLine(it * 2_000L, "Daylight line ${it + 1}",
        listOf(LyricWord(it * 2_000L, it * 2_000L + 1_000L, "Daylight"), LyricWord(it * 2_000L + 1_000L, it * 2_000L + 1_800L, "line ${it + 1}"))) }

    private fun requireDisposableEmulator() {
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue(AuthStore(context).sessions.isEmpty())
    }

    private fun art(name: String, a: Int, b: Int): String {
        val file = File(context.cacheDir, "$name.png")
        val bitmap = Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { shader = LinearGradient(0f, 0f, 800f, 1000f, a, b, Shader.TileMode.CLAMP) }
        canvas.drawRect(0f, 0f, 800f, 1000f, paint)
        paint.shader = null; paint.color = Color.WHITE; paint.alpha = 55
        canvas.drawCircle(450f, 350f, 240f, paint)
        paint.alpha = 35; canvas.drawCircle(160f, 680f, 220f, paint)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        return file.toURI().toString()
    }

    @Composable private fun fixture(song: Song) {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
        BitChordTheme(darkTheme = dark, pinkCloud = pink) {
            PlayerExperienceScreen(experience = mode, onDismiss = { taps.incrementAndGet() }, song = song,
                isPlaying = playing, isLoading = false, position = position, durationMs = 120_000,
                audioVersionSwitching = false, qualityUpgraded = false,
                queue = listOf(song, song.copy(title = "Second track", queueEntryId = "second"), song.copy(title = "Second track", queueEntryId = "third")),
                queueIndex = 0, hasPrevious = true, hasNext = true, repeatMode = 0, shuffleEnabled = false, autoplayEnabled = true,
                signedIn = true, accountName = "Daylight QA", likeStatus = LikeStatus.INDIFFERENT,
                onToggleLike = { taps.incrementAndGet() }, onPlayPause = { taps.incrementAndGet(); playing = !playing }, onNext = { taps.incrementAndGet() }, onPrevious = { taps.incrementAndGet() },
                onBlockedControl = {}, onSeek = { seek = it }, onSeekFraction = { seek = (it * 120_000).toLong() },
                onToggleShuffle = { taps.incrementAndGet() }, onCycleRepeat = { taps.incrementAndGet() }, onToggleAutoplay = { taps.incrementAndGet() },
                onJumpTo = { jumped = it }, onRemoveFromQueue = { removed = it }, onMoveInQueue = { _, _ -> }, onClearQueue = { taps.incrementAndGet() },
                onOpenMenu = { taps.incrementAndGet() }, onOpenAlbum = {}, onOpenArtist = { _, _ -> }, onOpenPlaybackSource = {}, onListenTogether = {},
                lyrics = if (missing) emptyList() else lines, lyricsSource = null, lyricsProviderStates = emptyMap(), onSelectLyricsProvider = {},
                lyricsUnavailable = missing, lyricsMissing = missing, lyricsOffsetOpen = false, onDismissLyricsOffset = {},
                windowWidth = if (landscape) 800.dp else 360.dp, windowHeight = if (landscape) 360.dp else 800.dp,
                modifier = Modifier)
        }
        }
    }

    @Test fun newAndCurrentModesRetainTheirControlsAndThemeMatrix() {
        requireDisposableEmulator()
        val originalCanvas = AppSettings.animatedCanvas.value
        val originalMode = AppSettings.playerExperience.value
        AppSettings.setAnimatedCanvas(false)
        compose.mainClock.autoAdvance = false
        val song = Song("new-player-qa", "An ordinary track", "Daylight QA", art("new-player-warm", Color.rgb(166, 45, 87), Color.rgb(26, 23, 52)))
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.setContent { fixture(song) } }
                compose.mainClock.advanceTimeBy(600); compose.waitForIdle()
                for ((name, isDark, isPink) in listOf(Triple("dark", true, false), Triple("light", false, false), Triple("pink", false, true))) {
                    for (glass in listOf(false, true)) {
                        compose.runOnIdle { dark = isDark; pink = isPink; AppSettings.setLiquidGlass(glass) }
                        compose.mainClock.advanceTimeBy(600); compose.waitForIdle()
                        compose.onNodeWithTag("daylight-player").assertIsDisplayed()
                        compose.onNodeWithText("Music").assertDoesNotExist()
                        val heart = compose.onNodeWithTag("daylight-player-heart").fetchSemanticsNode().boundsInRoot
                        val menu = compose.onNodeWithTag("daylight-player-menu").fetchSemanticsNode().boundsInRoot
                        val title = compose.onNodeWithText("An ordinary track").fetchSemanticsNode().boundsInRoot
                        assertTrue("Heart is left of the title", heart.right <= title.left)
                        assertTrue("Menu is right of the title", menu.left >= title.right)
                        compose.onNodeWithTag("daylight-player-play").performClick()
                        compose.mainClock.advanceTimeBy(600)
                        compose.onNodeWithText("An ordinary track").assertIsDisplayed()
                        screenshot("$name-glass-$glass-player")
                    }
                }
                assertEquals(6, taps.get())
                compose.runOnIdle { mode = PlayerExperience.CURRENT }
                compose.mainClock.advanceTimeBy(600); compose.waitForIdle()
                compose.onNodeWithTag("daylight-player").assertDoesNotExist()
                compose.onNodeWithText("An ordinary track").assertIsDisplayed()
                screenshot("current-player-retained")
                compose.runOnIdle { mode = PlayerExperience.DAYLIGHT; AppSettings.setPlayerExperience(PlayerExperience.DAYLIGHT) }
                compose.mainClock.advanceTimeBy(600); compose.waitForIdle()
                assertEquals("DAYLIGHT", context.getSharedPreferences("bitchord_settings", 0).getString("player_experience", "CURRENT"))
            }
        } finally { compose.mainClock.autoAdvance = true; AppSettings.setAnimatedCanvas(originalCanvas); AppSettings.setPlayerExperience(originalMode); AppSettings.setLiquidGlass(false) }
    }

    @Test fun lyricsSeekQueueActionsAndAutomixUseTheExistingSession() {
        requireDisposableEmulator()
        val originalCanvas = AppSettings.animatedCanvas.value
        val originalOffset = AppSettings.lyricsOffsetMs.value
        AppSettings.setAnimatedCanvas(false)
        AppSettings.setLyricsOffsetMs(-1_000)
        val coverA = art("new-player-red", Color.rgb(165, 43, 73), Color.rgb(33, 25, 41))
        val coverB = art("new-player-blue", Color.rgb(28, 124, 195), Color.rgb(10, 36, 57))
        val song = Song("new-player-qa", "An ordinary track", "Daylight QA", coverA)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.setContent { fixture(song) } }
                compose.waitForIdle()
                compose.onNodeWithTag("daylight-player-lyrics").performClick()
                compose.waitForIdle()
                compose.onNodeWithTag("daylight-player-lyric-list").assertIsDisplayed()
                compose.onNodeWithText("Daylight line 1").performClick()
                assertEquals("Negative display offsets must not shift a line's audio seek target", 0L, seek)
                compose.onNodeWithText("Daylight line 2").performClick()
                assertEquals(2_000L, seek)
                screenshot("daylight-lyrics")
                compose.onNodeWithTag("daylight-player-queue").performClick()
                compose.waitForIdle()
                compose.onAllNodesWithText("Second track").assertCountEquals(2)
                compose.onAllNodesWithText("Second track")[0].performClick()
                assertEquals(1, jumped)
                compose.onAllNodesWithContentDescription("Remove from queue")[1].performClick()
                assertEquals(1, removed)
                screenshot("daylight-queue")
                compose.onNodeWithTag("daylight-player-queue").performClick()
                compose.waitForIdle()
                compose.onNodeWithTag("daylight-player").performTouchInput { swipeUp(startY = height * .65f, endY = height * .28f, durationMillis = 300) }
                compose.waitForIdle()
                compose.onNodeWithTag("daylight-player-queue-list").assertIsDisplayed()
                compose.onNodeWithTag("daylight-player-queue-handle").performTouchInput { swipeDown(startY = 1f, endY = height - 1f, durationMillis = 300) }
                compose.waitForIdle()
                compose.onNodeWithTag("daylight-player-queue-list").assertDoesNotExist()
                compose.runOnIdle { AppSettings.smartMixInProgress.value = true; AppSettings.smartMixBlend.value = MixBlend(500f, System.nanoTime(), true,
                    progress = .48f, outgoingArtwork = coverA, incomingArtwork = coverB) }
                compose.mainClock.autoAdvance = false
                compose.mainClock.advanceTimeBy(600)
                compose.waitForIdle()
                screenshot("daylight-automix-handoff")
                compose.onNodeWithTag("automix-cover", useUnmergedTree = true).assertIsDisplayed()
                compose.runOnIdle { AppSettings.smartMixBlend.value = null; AppSettings.smartMixInProgress.value = false; missing = true }
                compose.mainClock.autoAdvance = true
                compose.waitForIdle()
                compose.onNodeWithTag("daylight-player-lyrics").assertIsNotEnabled()
                compose.onNodeWithTag("daylight-player-close").performClick()
            }
        } finally { AppSettings.setAnimatedCanvas(originalCanvas); AppSettings.setLyricsOffsetMs(originalOffset); AppSettings.smartMixBlend.value = null; AppSettings.smartMixInProgress.value = false }
    }

    @Test fun animatedCoverDecodesAndPausesWithoutReplacingThePlayer() {
        requireDisposableEmulator()
        val originalCanvas = AppSettings.animatedCanvas.value
        AppSettings.setAnimatedCanvas(true)
        compose.mainClock.autoAdvance = false
        val song = Song("new-player-motion-qa", "Animated track", "Daylight QA", art("new-player-motion", Color.rgb(160, 46, 87), Color.rgb(28, 20, 48)))
        val cleanup = motionCover(song)
        try {
            playing = true
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.setContent { fixture(song) } }
                compose.mainClock.advanceTimeBy(600); compose.waitForIdle()
                var first: Bitmap? = null
                val deadline = SystemClock.uptimeMillis() + 6_000
                while (first == null && SystemClock.uptimeMillis() < deadline) { SystemClock.sleep(100); first = videoFrame(scenario) }
                assertNotNull("Animated artwork decodes in the new player", first)
                SystemClock.sleep(450)
                val next = requireNotNull(videoFrame(scenario))
                assertFalse("Animated artwork advances while playing", first!!.sameAs(next))
                first.recycle(); next.recycle()
                screenshot("animated-playing")
                compose.onNodeWithTag("daylight-player-play").performClick()
                compose.mainClock.advanceTimeBy(600); compose.waitForIdle()
                SystemClock.sleep(500)
                val paused = requireNotNull(videoFrame(scenario))
                SystemClock.sleep(450)
                val still = requireNotNull(videoFrame(scenario))
                assertTrue("Pausing stops the artwork decoder", paused.sameAs(still))
                paused.recycle(); still.recycle()
                compose.onNodeWithTag("daylight-player-play").assertContentDescriptionEquals("Play")
                screenshot("animated-paused")
            }
        } finally { compose.mainClock.autoAdvance = true; cleanup(); AppSettings.setAnimatedCanvas(originalCanvas) }
    }

    @Test fun largeTextAndLandscapeKeepThePlaybackDeckAccessible() {
        requireDisposableEmulator()
        val originalCanvas = AppSettings.animatedCanvas.value
        AppSettings.setAnimatedCanvas(false)
        val song = Song("new-player-layout-qa", "A longer song title for checking the player", "A longer artist name", art("new-player-layout", Color.rgb(160, 46, 87), Color.rgb(28, 20, 48)))
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { fontScale = 2f; it.setContent { fixture(song) } }
                compose.waitForIdle()
                for (tag in listOf("daylight-player-play", "daylight-player-menu", "daylight-player-heart", "daylight-player-lyrics", "daylight-player-queue")) compose.onNodeWithTag(tag).assertIsDisplayed()
                screenshot("large-text-player")
                compose.runOnIdle { fontScale = 1f; landscape = true }
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                compose.waitUntil(5_000) {
                    compose.onNodeWithTag("daylight-player").fetchSemanticsNode().boundsInRoot.let { it.width > it.height }
                }
                compose.waitForIdle()
                for (tag in listOf("daylight-player-play", "daylight-player-menu", "daylight-player-heart", "daylight-player-lyrics", "daylight-player-queue")) compose.onNodeWithTag(tag).assertIsDisplayed()
                screenshot("landscape-player")
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            }
        } finally { AppSettings.setAnimatedCanvas(originalCanvas) }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun motionCover(song: Song): () -> Unit {
        val url = "https://daylight.invalid/new-player/${System.nanoTime()}.mp4"
        val cache = CanvasCache::class.java.getDeclaredField("cache").apply { isAccessible = true }.get(CanvasCache) as SimpleCache
        val bytes = instrumentation.context.assets.open("canary-automix-cover.mp4").use { it.readBytes() }
        val hole = requireNotNull(cache.startReadWrite(url, 0, bytes.size.toLong()))
        try {
            cache.startFile(url, 0, bytes.size.toLong()).apply { writeBytes(bytes) }.let { cache.commitFile(it, bytes.size.toLong()) }
            cache.applyContentMetadataMutations(url, ContentMetadataMutations().also { ContentMetadataMutations.setContentLength(it, bytes.size.toLong()) })
        } finally { cache.releaseHoleSpan(hole) }
        @Suppress("UNCHECKED_CAST")
        val entries = CanvasRepository::class.java.getDeclaredField("cache").apply { isAccessible = true }.get(CanvasRepository) as MutableMap<String, Any>
        val constructor = CanvasRepository::class.java.declaredClasses.first { it.simpleName == "Entry" }.declaredConstructors.single().apply { isAccessible = true }
        val key = "song|${song.videoId}"
        val prior = synchronized(entries) { entries.put(key, constructor.newInstance(CanvasArtwork(url), true)) }
        return { synchronized(entries) { if (prior == null) entries.remove(key) else entries[key] = prior }; cache.removeResource(url) }
    }

    private fun videoFrame(scenario: ActivityScenario<MainActivity>): Bitmap? {
        var bitmap: Bitmap? = null
        fun visit(view: View) {
            if (view is TextureView && view.isAvailable) bitmap = view.getBitmap(32, 32)
            if (view is ViewGroup) repeat(view.childCount) { visit(view.getChildAt(it)) }
        }
        scenario.onActivity { visit(it.window.decorView) }
        return bitmap
    }

    private fun screenshot(name: String) {
        SystemClock.sleep(400)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file = File(context.getExternalFilesDir(null), "canary-18-player/$name.png")
        file.parentFile?.mkdirs(); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
