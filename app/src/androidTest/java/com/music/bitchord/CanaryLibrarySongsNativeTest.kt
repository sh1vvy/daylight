package com.music.bitchord

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.BoundedRequestCache
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.MainViewModel
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Songs shortcut and liked-list pagination, using only scoped offline responses. */
@RunWith(AndroidJUnit4::class)
class CanaryLibrarySongsNativeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val automation get() = instrumentation.uiAutomation

    @Test
    fun songsOpensLikedMusicFromTheWarmLibraryPageAndKeepsUnlikesConsistent() {
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue("Never replace a real account", AuthStore(context).sessions.isEmpty())
        val oldCacheFolder = AppSettings.showCacheFolder.value
        AppSettings.setShowCacheFolder(true)
        val songs = (1..3).map { Song("canary4-liked-$it", "Liked test song $it", "Daylight QA", null, "3:00") }
        val token = "canary4-liked-next"
        val first = YtMusicRepository.SongPage(songs.take(2), token)
        putPage("browsePages", YtMusicRepository.LIKED_MUSIC, false, first)
        putPage("continuationPages", token, true, YtMusicRepository.SongPage(songs.drop(2), null))
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var model: MainViewModel
                scenario.onActivity { activity ->
                    model = ViewModelProvider(activity)[MainViewModel::class.java]
                    field<MutableStateFlow<Boolean>>(model, "_signedIn").value = true
                    field<MutableStateFlow<UiState<LibraryPage>>>(model, "_library").value = UiState.Success(
                        LibraryPage(songs.take(2), emptyList(), emptyList()),
                    )
                    // No coroutine or request is needed to populate the initial page.
                    model.openDetail(YtMusicRepository.LIKED_MUSIC, context.getString(R.string.auto_liked))
                    assertEquals(songs.take(2), (model.detailStack.value.single().songs as UiState.Success).data)
                    model.clearDetail()
                }
                click { it.contentDescription?.toString() == context.getString(R.string.library) && bounds(it).centerY() > 1200 }
                waitNode { hasText(it, context.getString(R.string.auto_liked)) }
                waitNode { hasText(it, context.getString(R.string.downloads)) }
                waitNode { hasText(it, context.getString(R.string.cached_songs)) }
                screenshot("library-songs-shortcut")
                click { hasText(it, context.getString(R.string.auto_liked)) }
                waitNode { hasText(it, songs.first().title) }
                val deadline = SystemClock.uptimeMillis() + 4_000
                while ((model.detailStack.value.single().songs as? UiState.Success)?.data?.size != 3 &&
                    SystemClock.uptimeMillis() < deadline) {
                    compose.waitForIdle(); SystemClock.sleep(50)
                }
                assertEquals(YtMusicRepository.LIKED_MUSIC, model.detailStack.value.single().browseId)
                assertEquals(context.getString(R.string.auto_liked), model.detailStack.value.single().title)
                assertEquals(songs, (model.detailStack.value.single().songs as UiState.Success).data)
                screenshot("library-liked-songs")
                scenario.onActivity {
                    MainViewModel::class.java.getDeclaredMethod("dropFromLikedLists", String::class.java)
                        .apply { isAccessible = true }.invoke(model, songs.first().videoId)
                }
                compose.waitForIdle()
                assertEquals(songs.drop(1), (model.detailStack.value.single().songs as UiState.Success).data)
                assertEquals(songs.drop(1), (model.library.value as UiState.Success).data.likedSongs)
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                compose.waitForIdle()
                waitNode { hasText(it, context.getString(R.string.auto_liked)) }
                assertTrue(model.detailStack.value.isEmpty())
            }
        } finally {
            YtMusicRepository.clearBrowseCache()
            AppSettings.setShowCacheFolder(oldCacheFolder)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(model: MainViewModel, name: String): T = MainViewModel::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(model) as T

    @Suppress("UNCHECKED_CAST")
    private fun putPage(cacheName: String, id: String, continuation: Boolean, page: YtMusicRepository.SongPage) {
        val keyClass = YtMusicRepository::class.java.declaredClasses.first { it.simpleName == "BrowseKey" }
        val key = keyClass.declaredConstructors.single().apply { isAccessible = true }.newInstance(
            Innertube.responseCacheScope, Innertube.currentLanguage, id, continuation,
        )
        val cache = YtMusicRepository::class.java.getDeclaredField(cacheName).apply { isAccessible = true }
            .get(YtMusicRepository) as BoundedRequestCache<Any, YtMusicRepository.SongPage>
        cache.put(key, page)
    }
    private fun bounds(node: AccessibilityNodeInfo) = Rect().also(node::getBoundsInScreen)
    private fun hasText(node: AccessibilityNodeInfo, text: String) = node.text?.toString()?.lineSequence()?.any { it == text } == true
    private fun find(node: AccessibilityNodeInfo?, test: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        node ?: return null
        if (node.isVisibleToUser && test(node)) return node
        repeat(node.childCount) { find(node.getChild(it), test)?.let { match -> return match } }
        return null
    }
    private fun waitNode(test: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 6_000
        do {
            compose.waitForIdle()
            find(automation.rootInActiveWindow, test)?.let { return it }
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        error("Missing Library Songs control")
    }
    private fun click(test: (AccessibilityNodeInfo) -> Boolean) {
        var node: AccessibilityNodeInfo? = waitNode(test)
        while (node?.isClickable != true) node = node?.parent ?: error("Missing clickable Library control")
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        compose.waitForIdle(); SystemClock.sleep(150)
    }
    private fun screenshot(name: String) {
        SystemClock.sleep(200)
        val bitmap = requireNotNull(automation.takeScreenshot())
        val out = File(context.getExternalFilesDir(null), "canary-4-qa/$name.png")
        out.parentFile?.mkdirs()
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
