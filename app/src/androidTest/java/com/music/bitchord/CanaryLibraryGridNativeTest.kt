package com.music.bitchord

import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.data.spotify.LocalPlaylistStore
import com.music.bitchord.ui.MainViewModel
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Offline state in the real app: library density, duplicate likes, actions and Show all. */
@RunWith(AndroidJUnit4::class)
class CanaryLibraryGridNativeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val horizontal = SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)

    @Test fun twoByTwoPreviewKeepsShowAllAndOnlyOneExtraColumn() {
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue(AuthStore(context).sessions.isEmpty())
        assumeTrue(LocalPlaylistStore.playlists.value.isEmpty())
        val oldTheme = AppSettings.themeMode.value
        val theme = InstrumentationRegistry.getArguments().getString("theme")?.let(ThemeMode::valueOf) ?: ThemeMode.DARK
        AppSettings.setThemeMode(theme)
        val local = LocalPlaylistStore.savePlaylist("Local canary mix", emptyList())
        val remote = (1..24).map { ShelfItem("Mix $it", "Daylight QA", null, null, "VLPLqa-grid-$it") }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.onNodeWithContentDescription(context.getString(R.string.library)).performClick()
                compose.waitForIdle()
                scenario.onActivity { activity ->
                    val model = ViewModelProvider(activity)[MainViewModel::class.java]
                    field<MutableStateFlow<Boolean>>(model, "_signedIn").value = true
                    field<MutableStateFlow<UiState<LibraryPage>>>(model, "_library").value = UiState.Success(
                        LibraryPage(emptyList(), emptyList(), listOf(HomeShelf(YtMusicRepository.PLAYLISTS_SHELF,
                            listOf(ShelfItem("System likes duplicate", "", null, null, YtMusicRepository.LIKED_MUSIC)) + remote))),
                    )
                }
                compose.waitForIdle()
                compose.onAllNodesWithText("System likes duplicate").assertCountEquals(0)
                compose.onAllNodesWithText(context.getString(R.string.your_replay)).assertCountEquals(1)
                compose.onAllNodesWithText(context.getString(R.string.auto_liked)).assertCountEquals(1)
                compose.onNodeWithText("Local canary mix").assertIsDisplayed()
                compose.onAllNodesWithText(context.getString(R.string.import_spotify)).assertCountEquals(0)
                compose.onNodeWithContentDescription(context.getString(R.string.more)).performClick()
                compose.onNodeWithText(context.getString(R.string.import_spotify)).assertIsDisplayed()
                back()
                screenshot("library-$theme")

                val a = compose.onNodeWithText("Local canary mix").fetchSemanticsNode().boundsInRoot
                val b = compose.onNodeWithText("Mix 1").fetchSemanticsNode().boundsInRoot
                val c = compose.onNodeWithText("Mix 2").fetchSemanticsNode().boundsInRoot
                val d = compose.onNodeWithText("Mix 3").fetchSemanticsNode().boundsInRoot
                assertTrue("Two rows in the first column", kotlin.math.abs(a.left - b.left) < 4 && b.top > a.bottom)
                assertTrue("Two columns fit", c.left > a.right && d.right <= context.resources.displayMetrics.widthPixels)
                compose.onNode(horizontal).performScrollToNode(hasText("Mix 5"))
                compose.waitForIdle()
                compose.onAllNodesWithText("Mix 24").assertCountEquals(0)
                val scrolledLeft = compose.onNodeWithText("Mix 5").fetchSemanticsNode().boundsInRoot.left
                screenshot("library-scrolled-$theme")
                compose.onNodeWithText(context.getString(R.string.show_all)).performClick()
                compose.waitForIdle()
                compose.onAllNodesWithText("System likes duplicate").assertCountEquals(0)
                compose.onNodeWithText("Local canary mix").assertIsDisplayed()
                compose.onNodeWithText("Mix 1").assertIsDisplayed()
                screenshot("library-show-all-$theme")
                back()
                compose.onNodeWithText("Mix 5").assertIsDisplayed()
                assertTrue("Back preserves horizontal paging", kotlin.math.abs(scrolledLeft -
                    compose.onNodeWithText("Mix 5").fetchSemanticsNode().boundsInRoot.left) < 4)
                compose.onNodeWithContentDescription(context.getString(R.string.new_playlist)).performClick()
                compose.waitForIdle()
                compose.onNodeWithText(context.getString(R.string.playlist_name)).assertIsDisplayed()
                screenshot("library-new-playlist-$theme")
                back()
            }
        } finally {
            LocalPlaylistStore.deletePlaylist(local.id)
            val prefs = context.getSharedPreferences("bitchord_local_playlists", android.content.Context.MODE_PRIVATE)
            val deadline = SystemClock.uptimeMillis() + 3_000
            while (prefs.getString("playlists_json", "[]") != "[]" && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
            assertEquals("[]", prefs.getString("playlists_json", "[]"))
            AppSettings.setThemeMode(oldTheme)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(model: MainViewModel, name: String): T = MainViewModel::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(model) as T
    private fun back() {
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        SystemClock.sleep(200)
    }
    private fun screenshot(name: String) {
        compose.waitForIdle(); SystemClock.sleep(200)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val out = File(context.getExternalFilesDir(null), "canary-6-qa/$name.png")
        out.parentFile?.mkdirs()
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
