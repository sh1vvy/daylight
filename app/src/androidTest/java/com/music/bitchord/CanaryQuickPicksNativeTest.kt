package com.music.bitchord

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.SystemClock
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
                scenario.onActivity { seedHome(model, listOf(HomeShelf("Recents", listOf(picks.first())), discovery), false) }
                compose.waitForIdle()
                compose.onAllNodesWithText("Recents").assertCountEquals(0)
                compose.onAllNodesWithText("Pick 1").assertCountEquals(1)
                val after = compose.onNodeWithText("Pick 1").fetchSemanticsNode().boundsInRoot
                assertTrue("Late recents do not shift the section", kotlin.math.abs(after.top - first.top) < 2f)
                compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)).onFirst()
                    .performScrollToNode(hasText("Pick 12"))
                compose.onNodeWithText("Pick 12").assertIsDisplayed()
                screenshot("quick-picks-scrolled-$theme")
            }
        } finally { cover.delete(); AppSettings.setThemeMode(oldTheme) }
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
