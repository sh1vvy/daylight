@file:Suppress("UnstableApiUsage")

package com.music.bitchord

import android.content.ComponentName
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.model.*
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.playback.*
import com.music.bitchord.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Home button and service refill; account UI is simulated without credentials. */
@RunWith(AndroidJUnit4::class)
class CanaryHomeMixNativeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun compactHomeMixesQuickPicksAndOneTapStartsAndRefillsFavorites() {
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue(AuthStore(context).sessions.isEmpty())
        val oldTheme = AppSettings.themeMode.value
        val oldAutoplay = AppSettings.autoplay.value
        val oldRepeat = AppSettings.repeatMode.value
        val theme = InstrumentationRegistry.getArguments().getString("theme")?.let(ThemeMode::valueOf) ?: ThemeMode.DARK
        AppSettings.setThemeMode(theme)
        AppSettings.setAutoplay(false)
        val file = File(context.cacheDir, "canary7-silent.wav")
        val bytes = 30 * 8_000 * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + bytes).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(8_000).putInt(16_000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(bytes).array()
        file.outputStream().use { it.write(header); it.write(ByteArray(bytes)) }
        val saved = (1..20).map { Song("local:canary7-$it", "Favorite $it", "Daylight QA", null, localUri = file.toURI().toString()) }
        val recent = (1..3).map { ShelfItem("Recent $it", "Daylight QA", null, "recent-$it", null) }
        val discovery = (1..8).map { ShelfItem("Discovery $it", "Daylight QA", null, "new-$it", null) }
        var controller: MediaController? = null
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var model: MainViewModel
                scenario.onActivity {
                    model = ViewModelProvider(it)[MainViewModel::class.java]
                    seed(model, listOf(HomeShelf("Recents", recent), HomeShelf("For you", emptyList(), "Unneeded description")), discovery, saved)
                }
                compose.onAllNodesWithText("Play my mix").assertCountEquals(0)
                compose.onAllNodesWithText("Sign in to YouTube Music").assertCountEquals(0)
                val signIn = compose.onNodeWithTag("home-sign-in-pill", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val avatar = compose.onNodeWithTag("default-profile-avatar", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertEquals("Sign in matches the profile circle's height", avatar.height, signIn.height, 2f)
                assertTrue("Sign in is next to the profile circle", signIn.right < avatar.left)
                assertEquals(avatar.center.y, signIn.center.y, 2f)
                compose.onAllNodesWithText("Listen Now").assertCountEquals(0)
                compose.onAllNodesWithText("Your music. A little brighter.").assertCountEquals(0)
                compose.onAllNodesWithText("A little familiar. A little discovery.").assertCountEquals(0)
                compose.onAllNodesWithText("Unneeded description").assertCountEquals(0)
                val butterfly = compose.onNodeWithTag("home-butterfly", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val wordmark = compose.onNodeWithContentDescription("Daylight", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertTrue("Marks have matching visual heights", kotlin.math.abs(butterfly.height - wordmark.height) < 8f)
                assertTrue("The brand marks have generous space between them", wordmark.left > butterfly.right + wordmark.width)
                val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
                assertEquals("The two marks use matching page gutters", butterfly.left - root.left, root.right - wordmark.right, 2f)
                val first = compose.onNodeWithText("Recent 1").fetchSemanticsNode().boundsInRoot
                val second = compose.onNodeWithText("Discovery 1").fetchSemanticsNode().boundsInRoot
                val third = compose.onNodeWithText("Discovery 2").fetchSemanticsNode().boundsInRoot
                assertTrue(first.bottom < second.top && second.bottom < third.top)
                screenshot("home-signed-out-$theme")
                scenario.onActivity { signedIn(model, true) }
                compose.onAllNodesWithText("Sign in").assertCountEquals(0)
                compose.onNodeWithTag("home-play-my-mix").performScrollTo().assertIsDisplayed()
                val mix = compose.onNodeWithTag("home-play-my-mix").fetchSemanticsNode().boundsInRoot
                val lastPick = compose.onNodeWithText("Discovery 2").fetchSemanticsNode().boundsInRoot
                assertTrue("Play my mix belongs below the quick listening rows", mix.top > lastPick.bottom)
                screenshot("home-$theme")
                scenario.onActivity { signedIn(model, false) }
                compose.onAllNodesWithText("Play my mix").assertCountEquals(0)
                scenario.onActivity { signedIn(model, true) }

                // No discoveries initially: the real service must start known files and enrich its tail.
                scenario.onActivity { seed(model, emptyList(), emptyList(), saved) }
                DaylightMixRepository.clear(); DaylightMixRepository.prime(saved)
                val latch = CountDownLatch(1)
                instrumentation.runOnMainSync {
                    val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
                    future.addListener({ controller = future.get(); latch.countDown() }, { it.run() })
                }
                assertTrue(latch.await(10, TimeUnit.SECONDS))
                onMain { controller!!.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL }
                compose.onNodeWithText("Play my mix").performClick()
                compose.waitUntil(10_000) { onMain { controller!!.currentMediaItem?.toSong()?.isDaylightMix() == true } }
                onMain { controller!!.pause() }
                assertTrue(AppSettings.autoplay.value)
                assertEquals(androidx.media3.common.Player.REPEAT_MODE_OFF, onMain { controller!!.repeatMode })
                compose.waitUntil(20_000) { onMain { controller!!.mediaItemCount >= 8 } }
                val queue = onMain { (0 until controller!!.mediaItemCount).map { controller!!.getMediaItemAt(it).toSong() } }
                assertTrue(queue.size in 8..20)
                assertEquals(queue.size, queue.map { it.videoId }.distinct().size)
                assertTrue(queue.all { it.localUri == file.toURI().toString() && it.isDaylightMix() })
                // Turning AutoPlay off must stop this endless mix and remove its pending tail.
                onMain { controller!!.toggleAutoplay() }
                compose.waitUntil(5_000) { !AppSettings.autoplay.value && onMain { controller!!.mediaItemCount == 1 } }
                runBlocking { withContext(Dispatchers.Main) { controller!!.playSongs(listOf(saved.first()), 0); controller!!.pause() } }
                compose.waitUntil(5_000) { onMain { controller!!.currentMediaItem?.toSong()?.isDaylightMix() == false } }
                assertEquals(1, onMain { controller!!.mediaItemCount })
            }
        } finally {
            onMain { controller?.stop(); controller?.clearMediaItems(); controller?.release() }
            DaylightMixRepository.clear(); file.delete()
            AppSettings.setAutoplay(oldAutoplay); AppSettings.setRepeatMode(oldRepeat); AppSettings.setThemeMode(oldTheme)
        }
    }

    private fun <T> onMain(action: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync { result = action() }
        @Suppress("UNCHECKED_CAST") return result as T
    }

    @Suppress("UNCHECKED_CAST")
    private fun signedIn(model: MainViewModel, value: Boolean) {
        (MainViewModel::class.java.getDeclaredField("_signedIn").apply { isAccessible = true }.get(model)
            as MutableStateFlow<Boolean>).value = value
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val image = File(context.getExternalFilesDir(null), "canary-7-qa/$name.png")
        image.parentFile?.mkdirs(); image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }

    @Suppress("UNCHECKED_CAST")
    private fun seed(model: MainViewModel, shelves: List<HomeShelf>, recommendations: List<ShelfItem>, songs: List<Song>) {
        fun field(name: String) = MainViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }.get(model)
        (field("homeLoadGeneration") as AtomicLong).incrementAndGet()
        (field("_homePendingShelves") as MutableStateFlow<Int>).value = 0
        (field("_homeRecentlyPlayedLoading") as MutableStateFlow<Boolean>).value = false
        (field("_home") as MutableStateFlow<UiState<List<HomeShelf>>>).value = UiState.Success(shelves)
        (field("_homeQuickRecommendations") as MutableStateFlow<List<ShelfItem>>).value = recommendations
        (field("_library") as MutableStateFlow<UiState<LibraryPage>>).value = UiState.Success(LibraryPage(songs, emptyList(), emptyList()))
    }
}
