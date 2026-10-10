@file:Suppress("UnstableApiUsage")
package com.music.bitchord

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.library.*
import com.music.bitchord.data.model.*
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.*
import com.music.bitchord.ui.MainViewModel
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Offline catalogue fixtures only, never executed on a signed-in device or physical phone. */
@RunWith(AndroidJUnit4::class)
class CanaryCollectionMetadataNativeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun savedLikedListOpensImmediatelyAndSpotifyPlaybackAddsLibraryAndHomeCards() {
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue(AuthStore(context).sessions.isEmpty())
        val oldCookie = AppSettings.spotifySpdcToken.value
        val oldAutoplay = AppSettings.autoplay.value
        val ytScope = likedMetadataScope("canary10:profile")!!
        val spotifyScope = spotifyMetadataScope("canary10-not-a-real-cookie")!!
        val songs = (1..240).map { Song("local:qa-cached-$it", "Cached liked song $it", "Daylight QA", null, "0:30") }
        val liked = CollectionSnapshot(YtMusicRepository.LIKED_MUSIC, "Liked songs", songs = songs, complete = true, updatedAt = System.currentTimeMillis())
        val playlist = CollectionSnapshot("spotify:playlist:canary10", "Cached Spotify collection", "Daylight QA",
            songs = songs.take(3), complete = true, updatedAt = System.currentTimeMillis(), trackIds = listOf("one", "two", "three"))
        runBlocking { CollectionMetadataStore.save(ytScope, liked); CollectionMetadataStore.save(spotifyScope, playlist) }
        AppSettings.setSpotifySpdcToken("canary10-not-a-real-cookie")
        AppSettings.setAutoplay(false)
        val audio = File(context.cacheDir, "canary10-silent.wav")
        val bytes = 30 * 8_000 * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + bytes).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(8_000).putInt(16_000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(bytes).array()
        audio.outputStream().use { it.write(header); it.write(ByteArray(bytes)) }
        var controller: MediaController? = null
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var model: MainViewModel
                scenario.onActivity { activity ->
                    model = ViewModelProvider(activity)[MainViewModel::class.java]
                    field<MutableStateFlow<String?>>(model, "_activeAccountId").value = "canary10"
                    field<MutableStateFlow<String?>>(model, "_activeProfileId").value = "profile"
                    field<MutableStateFlow<Boolean>>(model, "_signedIn").value = true
                    field<AtomicLong>(model, "homeLoadGeneration").incrementAndGet()
                    field<MutableStateFlow<Int>>(model, "_homePendingShelves").value = 0
                    field<MutableStateFlow<Boolean>>(model, "_homeRecentlyPlayedLoading").value = false
                    field<MutableStateFlow<UiState<List<HomeShelf>>>>(model, "_home").value = UiState.Success(emptyList())
                    field<MutableStateFlow<UiState<LibraryPage>>>(model, "_library").value = UiState.Success(LibraryPage(songs, emptyList(), emptyList()))
                    YtMusicRepository.clearBrowseCache()
                    model.openDetail(YtMusicRepository.LIKED_MUSIC, "Liked songs")
                    assertEquals("All 240 rows are ready synchronously, without a browse-cache first page", songs,
                        (model.detailStack.value.single().songs as UiState.Success).data)
                }
                compose.waitForIdle()
                screenshot("liked-ready")
                scenario.onActivity {
                    model.clearDetail()
                    YtMusicRepository.clearBrowseCache()
                    model.openDetail(YtMusicRepository.LIKED_MUSIC, "Liked songs")
                    assertEquals(songs, (model.detailStack.value.single().songs as UiState.Success).data)
                    model.clearDetail()
                    model.openDetail(playlist.browseId, playlist.title, playlist.subtitle)
                    assertEquals(playlist.songs, (model.detailStack.value.single().songs as UiState.Success).data)
                    model.clearDetail()
                }
                compose.waitForIdle()
                assertEquals(0L, runBlocking { CollectionMetadataStore.read(spotifyScope).single().lastPlayedAt })
                clickNavigation(context.getString(R.string.library))
                compose.onAllNodesWithText(playlist.title).assertCountEquals(0)
                val latch = CountDownLatch(1)
                instrumentation.runOnMainSync {
                    val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
                    future.addListener({ controller = future.get(); latch.countDown() }, { it.run() })
                }
                assertTrue(latch.await(10, TimeUnit.SECONDS))
                runBlocking { withContext(Dispatchers.Main) {
                    controller!!.playSongs(listOf(songs.first().copy(localUri = audio.toURI().toString(), playbackSource = playlist.title,
                        playbackSourceType = PlaybackSourceType.BROWSE, playbackSourceId = playlist.browseId)), 0)
                } }
                compose.waitUntil(10_000) { model.playedSpotifyCollections.value.second.any { it.browseId == playlist.browseId } }
                instrumentation.runOnMainSync { controller!!.pause() }
                compose.onNodeWithText(playlist.title).assertExists()
                screenshot("spotify-in-library")
                clickNavigation(context.getString(R.string.home))
                compose.onNodeWithText(playlist.title).assertExists()
                screenshot("spotify-in-listen-again")
                scenario.onActivity {
                    model.openDetail(playlist.browseId, playlist.title)
                    assertEquals(playlist.songs, (model.detailStack.value.single().songs as UiState.Success).data)
                    model.clearDetail()
                    field<MutableStateFlow<String?>>(model, "_activeAccountId").value = "different"
                    MainViewModel::class.java.getDeclaredMethod("clearListenerState", Boolean::class.javaPrimitiveType)
                        .apply { isAccessible = true }.invoke(model, false)
                    model.openDetail(YtMusicRepository.LIKED_MUSIC, "Liked songs")
                    assertTrue("Another identity never sees the saved rows", model.detailStack.value.single().songs is UiState.Loading)
                    model.clearDetail()
                }
            }
        } finally {
            instrumentation.runOnMainSync { controller?.stop(); controller?.clearMediaItems(); controller?.release() }
            AppSettings.setSpotifySpdcToken(oldCookie); AppSettings.setAutoplay(oldAutoplay)
            runBlocking { CollectionMetadataStore.removeScope(ytScope); CollectionMetadataStore.removeScope(spotifyScope) }
            audio.delete()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(model: MainViewModel, name: String): T = MainViewModel::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.get(model) as T

    private fun clickNavigation(label: String) {
        fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            node ?: return null
            val rect = Rect().also(node::getBoundsInScreen)
            if (node.isVisibleToUser && node.contentDescription?.toString() == label && rect.centerY() > 1200) return node
            repeat(node.childCount) { find(node.getChild(it))?.let { found -> return found } }
            return null
        }
        compose.waitForIdle()
        var node = find(instrumentation.uiAutomation.rootInActiveWindow) ?: error("Missing $label navigation")
        while (!node.isClickable) node = node.parent ?: error("No clickable $label navigation")
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        compose.waitForIdle(); SystemClock.sleep(300)
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file = File(context.getExternalFilesDir(null), "canary-10-qa/$name.png")
        file.parentFile!!.mkdirs(); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
