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
import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.*
import com.music.bitchord.data.settings.*
import com.music.bitchord.ui.MainViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real app, offline fixtures: category routing, retained browsers, search, and theme fit. */
@RunWith(AndroidJUnit4::class)
class CanaryLibraryGridNativeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun collectionHubAndBrowsersWorkAcrossEveryThemeAndMaterial() {
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue(AuthStore(context).sessions.isEmpty())
        val oldTheme = AppSettings.themeMode.value
        val oldGlass = AppSettings.liquidGlass.value
        val oldSort = AppSettings.librarySort.value
        val oldViews = AppSettings.libraryCollectionViews.value
        val preferences = context.getSharedPreferences("bitchord_settings", android.content.Context.MODE_PRIVATE)
        val oldSavedViews = preferences.getString("library_collection_views", null)
        val files = (0..5).map { index ->
            File(context.cacheDir, "library-overhaul-cover-$index.png").apply {
                val bitmap = Bitmap.createBitmap(420, 420, Bitmap.Config.ARGB_8888)
                Canvas(bitmap).apply {
                    drawColor(Color.rgb(60 + index * 25, 60 + index * 13, 100 + index * 18))
                    drawCircle(210f, 210f, 135f, Paint().apply { color = Color.rgb(230, 170 + index * 8, 155 + index * 12) })
                    drawCircle(210f, 210f, 34f, Paint().apply { color = Color.rgb(65, 60, 80) })
                }
                outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            }
        }
        val playlists = (1..60).map { ShelfItem("Playlist $it", "Daylight QA", files[it % 6].toURI().toString(), null, "VLPLqa-$it") }
        val albums = (1..60).map { ShelfItem("Album $it", "Daylight QA", files[(it + 2) % 6].toURI().toString(), null, "MPREqa-$it") }
        val artists = ('A'..'Z').flatMap { letter -> (1..12).map { ShelfItem("$letter Artist $it", "", files[it % 6].toURI().toString(), null, "UCqa-$letter-$it") } }
        val fixture = LibraryPage(emptyList(), emptyList(), listOf(
            HomeShelf("Playlists", listOf(ShelfItem("System likes duplicate", "", null, null, YtMusicRepository.LIKED_MUSIC)) + playlists),
            HomeShelf("Albums", albums), HomeShelf("Artists", artists), HomeShelf("Subscriptions", artists.take(4))))
        try {
            for (theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.PINK_CLOUD)) for (glass in listOf(false, true)) {
                AppSettings.setThemeMode(theme); AppSettings.setLiquidGlass(glass); AppSettings.setLibrarySort(LibrarySort.DEFAULT)
                AppSettings.setLibraryCollectionView("Albums", LibraryViewType.GRID)
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    compose.onNodeWithContentDescription(context.getString(R.string.library)).performClick()
                    scenario.onActivity { activity ->
                        val model = ViewModelProvider(activity)[MainViewModel::class.java]
                        field<MutableStateFlow<Boolean>>(model, "_signedIn").value = true
                        field<MutableStateFlow<UiState<LibraryPage>>>(model, "_library").value = UiState.Success(fixture)
                    }
                    compose.waitForIdle()
                    compose.onNodeWithText(context.getString(R.string.replay)).assertIsDisplayed()
                    compose.onNodeWithTag("library-category:library:playlists").assertIsDisplayed()
                    compose.onNodeWithTag("library-category:library:artists").assertIsDisplayed()
                    compose.onNodeWithTag("library-category:library:albums").assertIsDisplayed()
                    compose.onNodeWithTag("library-category:VLLM").assertIsDisplayed()
                    compose.onNodeWithTag("library-category:local:downloads").assertIsDisplayed()
                    compose.onAllNodesWithText("System likes duplicate").assertCountEquals(0)
                    screenshot("overview-$theme-glass-$glass")
                    compose.onNodeWithText(context.getString(R.string.replay)).performClick()
                    compose.onNodeWithTag("library-overview").assertDoesNotExist()
                    back()
                    compose.onNodeWithTag("library-category:local:downloads").performClick()
                    compose.onNodeWithTag("library-downloads-hub").assertIsDisplayed()
                    screenshot("downloads-$theme-glass-$glass")
                    compose.onNodeWithTag("library-downloads-category:3").performClick()
                    compose.onNodeWithTag("library-downloads-hub").assertDoesNotExist()
                    back()
                    compose.onNodeWithTag("library-downloads-hub").assertIsDisplayed()
                    back()
                    compose.onNodeWithTag("library-overview").performScrollToNode(hasText("Album 1"))
                    val album = compose.onNodeWithText("Album 1").fetchSemanticsNode().boundsInRoot
                    val playlist = compose.onNodeWithText("Playlist 1").fetchSemanticsNode().boundsInRoot
                    assertTrue("Two equal-width columns", playlist.left > album.right && kotlin.math.abs(playlist.top - album.top) < 6)
                    assertTrue("Large covers instead of compact tiles", album.width > context.resources.displayMetrics.widthPixels * 0.30f)
                    screenshot("recent-$theme-glass-$glass")
                    compose.onNodeWithTag("library-overview").performScrollToNode(hasTestTag("library-category:library:albums"))
                    compose.onNodeWithTag("library-category:library:albums").performClick()
                    compose.onNodeWithTag("library-collection-grid").assertIsDisplayed()
                    compose.onNodeWithTag("library-collection-search").performTextInput("Album 57")
                    compose.onNode(hasText("Album 57") and !hasSetTextAction()).assertIsDisplayed()
                    compose.onAllNodesWithText("Album 1").assertCountEquals(0)
                    hideKeyboard()
                    screenshot("album-search-$theme-glass-$glass")
                    compose.onNodeWithContentDescription("Collection options").performClick()
                    compose.onNodeWithText("List", useUnmergedTree = true).performClick()
                    compose.onNodeWithTag("library-collection-list").assertIsDisplayed()
                    assertEquals(LibraryViewType.LIST, AppSettings.libraryCollectionViews.value["Albums"])
                    screenshot("album-list-$theme-glass-$glass")
                    back()
                    compose.onNodeWithTag("library-category:library:artists").performClick()
                    screenshot("artists-open-$theme-glass-$glass")
                    compose.onNodeWithText("A Artist 1").assertIsDisplayed()
                    compose.onNodeWithText("Z", useUnmergedTree = true).performClick()
                    compose.onNodeWithText("Z Artist 1").assertIsDisplayed()
                    compose.onAllNodesWithText("A Artist 1").assertCountEquals(0)
                    screenshot("artist-index-$theme-glass-$glass")
                    back()
                    compose.onNodeWithTag("library-category:library:playlists").performClick()
                    compose.onAllNodesWithText("System likes duplicate").assertCountEquals(0)
                    compose.onNodeWithText("Playlist 1").assertIsDisplayed()
                    screenshot("playlists-$theme-glass-$glass")
                    back()
                    compose.onNodeWithContentDescription(context.getString(R.string.more)).performClick()
                    compose.onNodeWithText(context.getString(R.string.listening_history)).assertIsDisplayed()
                    compose.onNodeWithText(context.getString(R.string.import_spotify)).assertIsDisplayed()
                    screenshot("library-tools-$theme-glass-$glass")
                    back()
                }
            }
        } finally {
            files.forEach(File::delete)
            AppSettings.setThemeMode(oldTheme); AppSettings.setLiquidGlass(oldGlass); AppSettings.setLibrarySort(oldSort)
            AppSettings.libraryCollectionViews.value = oldViews
            preferences.edit().apply {
                if (oldSavedViews == null) remove("library_collection_views") else putString("library_collection_views", oldSavedViews)
            }.apply()
        }
    }

    @Suppress("UNCHECKED_CAST") private fun <T> field(model: MainViewModel, name: String): T = MainViewModel::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(model) as T
    private fun back() { instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); compose.waitForIdle(); SystemClock.sleep(250) }
    private fun hideKeyboard() { instrumentation.uiAutomation.executeShellCommand("input keyevent 111").close(); compose.waitForIdle() }
    private fun screenshot(name: String) {
        compose.waitForIdle(); SystemClock.sleep(120)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val out = File(context.getExternalFilesDir(null), "canary-12-qa/$name.png")
        out.parentFile?.mkdirs(); out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
