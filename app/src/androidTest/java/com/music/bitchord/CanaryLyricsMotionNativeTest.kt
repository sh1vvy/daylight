@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.music.bitchord

import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.ui.player.LyricPlayhead
import com.music.bitchord.ui.player.LyricsPanel
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise the existing renderer with deterministic offline timing; no stream/provider needed. */
@RunWith(AndroidJUnit4::class)
class CanaryLyricsMotionNativeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun followingMovesContinuouslyAndKeepsTranslationsAndTapToSeek() {
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val reduced = AppSettings.reduceAnimation.value
        AppSettings.setReduceAnimation(false)
        val position = PlaybackPosition()
        val playhead = LyricPlayhead(position, mutableStateOf(0))
        val lines = (0..12).map { LyricLine(it * 2_000L, "A little daylight ${it + 1}", sungUntilMs = (it + 1) * 2_000L) }
        val translations = lines.mapIndexed { index, line -> line.copy(text = "Translated line ${index + 1}") }
        var seek = -1L
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    MaterialTheme(colorScheme = darkColorScheme()) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF3B2B36))) {
                            LyricsPanel(lines = lines, subLines = translations, trackKey = "qa-lyrics-six",
                                playhead = playhead, looking = false, isPlaying = false,
                                controlsOpen = false, onRevealControls = {}, onHideControls = {},
                                onSeekToLine = { seek = it }, modifier = Modifier.fillMaxSize())
                        }
                    }
                } }
                compose.waitForIdle()
                compose.onAllNodesWithText("Translated line 2").onFirst().assertIsDisplayed()
                val before = top(2)
                compose.mainClock.autoAdvance = false
                compose.runOnIdle { position.report(2_010L) }
                advance(64)
                val early = top(2)
                screenshot("lyrics-spring-early")
                advance(96)
                val middle = top(2)
                screenshot("lyrics-spring-middle")
                assertTrue("The anchor advances between frames ($before, $early, $middle)", middle < early && early < before)
                compose.mainClock.autoAdvance = true
                compose.waitForIdle()
                val settled = top(2)
                assertTrue("The early frame precedes the final position", middle > settled)
                screenshot("lyrics-spring-settled")
                compose.onAllNodesWithText("A little daylight 3").onFirst().performClick()
                assertEquals(4_000L, seek)
                compose.onAllNodesWithText("Translated line 3").onFirst().assertIsDisplayed()

                AppSettings.setReduceAnimation(true)
                compose.waitForIdle()
                compose.mainClock.autoAdvance = false
                compose.runOnIdle { position.report(4_010L) }
                advance(32)
                val immediate = top(3)
                advance(96)
                assertTrue("Reduced motion immediately places the row", kotlin.math.abs(top(3) - immediate) < 2f)
                screenshot("lyrics-reduced-motion")
                compose.mainClock.autoAdvance = true
            }
        } finally { compose.mainClock.autoAdvance = true; AppSettings.setReduceAnimation(reduced) }
    }
    private fun top(line: Int) = compose.onAllNodesWithText("A little daylight $line").onFirst().fetchSemanticsNode().boundsInRoot.top
    private fun advance(ms: Long) { compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }
    private fun screenshot(name: String) {
        compose.waitForIdle(); SystemClock.sleep(80)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file = File(context.getExternalFilesDir(null), "canary-6-qa/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
