/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.AudioPipelineDialog
import com.music.bitchord.ui.components.SongRow
import com.music.bitchord.ui.theme.BitChordTheme
import com.music.bitchord.ui.theme.daylightBackground
import dev.chrisbanes.haze.HazeState
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Rendered pixels catch opaque row fills that palette-only contrast checks cannot. */
@RunWith(AndroidJUnit4::class)
class CanaryThemeSurfaceNativeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val themes = listOf(Triple("dark", true, false), Triple("light", false, false), Triple("pink", false, true))

    private fun requireDisposableEmulator() {
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue(AuthStore(context).sessions.isEmpty())
    }

    @Test fun ordinarySongRowsShareThePageWashAndSwipeStillQueuesOnlyPastTheThreshold() {
        requireDisposableEmulator()
        val priorSwipe = AppSettings.swipeToPlayNext.value
        AppSettings.setSwipeToPlayNext(false)
        try {
            for ((name, dark, pink) in themes) {
                val queued = AtomicInteger()
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    scenario.onActivity { activity -> activity.setContent {
                        BitChordTheme(darkTheme = dark, pinkCloud = pink) {
                            Box(Modifier.fillMaxSize().daylightBackground(), contentAlignment = Alignment.Center) {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    repeat(4) { index ->
                                        SongRow(Song("surface-$index", "Theme track ${index + 1}", "Daylight QA", null),
                                            onClick = {}, onSwipeToQueue = { queued.incrementAndGet() },
                                            modifier = Modifier.testTag("surface-row-$index"))
                                    }
                                    SongRow(Song("surface-current", "Playing track", "Daylight QA", null),
                                        onClick = {}, onSwipeToQueue = {}, isCurrent = true,
                                        modifier = Modifier.testTag("surface-current-row"))
                                }
                            }
                        }
                    } }
                    compose.waitForIdle()
                    assertRowsBlendWithPage(name)
                    val row = compose.onNodeWithTag("surface-row-1")
                    row.performTouchInput {
                        down(Offset(width * .15f, height * .5f))
                        moveTo(Offset(width * .43f, height * .5f), delayMillis = 450)
                    }
                    screenshot("rows-$name-dragging").recycle()
                    row.performTouchInput { up() }
                    compose.waitForIdle()
                    assertEquals("A short swipe does not queue a track", 0, queued.get())
                    row.performTouchInput { swipe(Offset(width * .10f, height * .5f), Offset(width * .85f, height * .5f), 450) }
                    compose.waitForIdle()
                    assertEquals("A deliberate swipe queues once", 1, queued.get())
                    // Synthetic touch events advance their timestamps ahead of
                    // wall time. Let both clocks settle the native release ripple
                    // before comparing the resting pixels, rather than its ink.
                    compose.mainClock.advanceTimeBy(1_500)
                    SystemClock.sleep(1_500)
                    assertRowsBlendWithPage("$name-reset")
                }
            }
        } finally { AppSettings.setSwipeToPlayNext(priorSwipe) }
    }

    @Test fun audioInspectionUsesReadableThemeSurfacesAndItsCloseActionWorks() {
        requireDisposableEmulator()
        val priorBlur = AppSettings.reduceDynamicBlur.value
        AppSettings.setReduceDynamicBlur(true)
        try {
            for ((name, dark, pink) in themes) {
                var dismissed = false
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    scenario.onActivity { activity -> activity.setContent {
                        BitChordTheme(darkTheme = dark, pinkCloud = pink) {
                            Box(Modifier.fillMaxSize().daylightBackground()) {
                                AudioPipelineDialog(hazeState = remember { HazeState() }, onDismiss = { dismissed = true })
                            }
                        }
                    } }
                    compose.onNodeWithText(context.getString(R.string.audio_pipeline)).assertIsDisplayed()
                    compose.onNodeWithText(context.getString(R.string.done)).assertIsDisplayed()
                    screenshot("audio-inspection-$name").recycle()
                    compose.onNodeWithText(context.getString(R.string.done)).performClick()
                    compose.runOnIdle { assertTrue("The dialog still closes", dismissed) }
                }
            }
        } finally { AppSettings.setReduceDynamicBlur(priorBlur) }
    }

    private fun assertRowsBlendWithPage(name: String) {
        val bitmap = screenshot("rows-$name")
        try {
            repeat(4) { index ->
                val bounds = compose.onNodeWithTag("surface-row-$index").fetchSemanticsNode().boundsInRoot
                // The row's empty left padding must match the adjacent page, not a solid base.
                val y = bounds.center.y.toInt()
                val inside = bitmap.getPixel(bounds.left.toInt() + 3, y)
                val outside = bitmap.getPixel(bounds.left.toInt() - 3, y)
                val delta = kotlin.math.abs(Color.red(inside) - Color.red(outside)) +
                    kotlin.math.abs(Color.green(inside) - Color.green(outside)) +
                    kotlin.math.abs(Color.blue(inside) - Color.blue(outside))
                assertTrue("$name row $index paints an opaque band (RGB delta $delta)", delta <= 10)
            }
            val playing = compose.onNodeWithTag("surface-current-row").fetchSemanticsNode().boundsInRoot
            val outside = bitmap.getPixel(playing.left.toInt() - 3, playing.top.toInt() + 1)
            val corner = bitmap.getPixel(playing.left.toInt() + 1, playing.top.toInt() + 1)
            val difference = kotlin.math.abs(Color.red(corner) - Color.red(outside)) +
                kotlin.math.abs(Color.green(corner) - Color.green(outside)) +
                kotlin.math.abs(Color.blue(corner) - Color.blue(outside))
            assertTrue("$name playing highlight has a square corner", difference <= 10)
            compose.onNodeWithText("Playing track").assertIsDisplayed()
        } finally { bitmap.recycle() }
    }

    private fun screenshot(name: String): Bitmap {
        compose.waitForIdle()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file = File(context.getExternalFilesDir(null), "canary-17-qa/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }
}
