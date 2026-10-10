package com.music.bitchord

import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.data.AppUpdateChecker.DownloadState
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.ui.components.UpdateAvailableDialogContent
import com.music.bitchord.ui.theme.BitChordTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Check the real update card without requesting a release, downloading or installing an APK. */
@RunWith(AndroidJUnit4::class)
class CanaryUpdateNoticeNativeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun updateStatesKeepTheirActionsAndFitTheScreen() {
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val oldTheme = AppSettings.themeMode.value
        val oldReduced = AppSettings.reduceAnimation.value
        val theme = InstrumentationRegistry.getArguments().getString("theme")?.let(ThemeMode::valueOf) ?: ThemeMode.DARK
        AppSettings.setThemeMode(theme)
        AppSettings.setReduceAnimation(true)
        val state = mutableStateOf<DownloadState>(DownloadState.Idle)
        var downloads = 0
        var installs = 0
        var cancels = 0
        var releases = 0
        var dismisses = 0
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    BitChordTheme(darkTheme = theme == ThemeMode.DARK, pinkCloud = theme == ThemeMode.PINK_CLOUD) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            UpdateAvailableDialogContent(
                                version = "0.2.2-dev.8",
                                notes = "**A little smoother.**\n\n- Faster, smoother scrolling.\n- Softer update popup.\n- All your music, just as you left it.",
                                downloadState = state.value,
                                onDismiss = { dismisses++ },
                                onDownload = { downloads++; state.value = DownloadState.Downloading(0.42f) },
                                onCancelDownload = { cancels++; state.value = DownloadState.Idle },
                                onInstall = { installs++ },
                                onOpenReleasePage = { releases++ },
                            )
                        }
                    }
                } }
                compose.onNodeWithText(text(R.string.download_now)).assertIsDisplayed()
                val card = compose.onNodeWithTag("update-notice").fetchSemanticsNode().boundsInRoot
                val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
                assertTrue("The card leaves space around every screen edge", card.left > root.left && card.right < root.right && card.top > root.top && card.bottom < root.bottom)
                compose.onNodeWithText(text(R.string.whats_new)).assertIsDisplayed()
                screenshot("idle-$theme")
                compose.onNodeWithText(text(R.string.download_now)).performClick()
                assertEquals(1, downloads)
                compose.onNodeWithText("42%").assertIsDisplayed()
                compose.onNodeWithText(text(R.string.cancel)).assertIsDisplayed()
                screenshot("downloading-$theme")
                compose.onNodeWithContentDescription(text(R.string.close)).performClick()
                assertEquals(1, dismisses)
                assertEquals("Dismiss does not cancel the active download", 0, cancels)
                compose.onNodeWithText(text(R.string.cancel)).performClick()
                assertEquals(1, cancels)
                compose.onNodeWithText(text(R.string.download_now)).assertIsDisplayed()
                compose.runOnIdle { state.value = DownloadState.Ready(File(context.cacheDir, "not-installed-qa.apk")) }
                compose.onNodeWithText(text(R.string.install_now)).assertIsDisplayed().performClick()
                assertEquals(1, installs)
                screenshot("ready-$theme")
                compose.runOnIdle { state.value = DownloadState.Failed("Couldn’t finish the download. Please try again.") }
                compose.onNodeWithText(text(R.string.try_again)).assertIsDisplayed()
                compose.onNodeWithText(text(R.string.open_releases_page)).assertIsDisplayed().performClick()
                assertEquals(1, releases)
                screenshot("failed-$theme")
                compose.onNodeWithText(text(R.string.try_again)).performClick()
                assertEquals(2, downloads)
            }
        } finally {
            AppSettings.setThemeMode(oldTheme)
            AppSettings.setReduceAnimation(oldReduced)
        }
    }

    private fun text(id: Int) = context.getString(id)
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file = File(context.getExternalFilesDir(null), "canary-9-qa/update-$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
