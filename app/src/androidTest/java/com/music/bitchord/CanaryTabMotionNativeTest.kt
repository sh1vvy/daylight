package com.music.bitchord

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.LocalAppBackdrop
import com.music.bitchord.ui.components.LocalLiquidGlassEnabled
import com.music.bitchord.ui.components.backdrop.Backdrop
import com.music.bitchord.ui.components.floatingtabbar.FloatingTabBar
import com.music.bitchord.ui.components.floatingtabbar.FloatingTabBarDefaults
import com.music.bitchord.ui.components.floatingtabbar.FloatingTabBarScrollConnection
import com.music.bitchord.ui.theme.BitChordTheme
import java.io.File
import java.util.Collections
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise the production bar's intermediate geometry, gestures and material switch. */
@RunWith(AndroidJUnit4::class)
class CanaryTabMotionNativeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test
    fun regularHighlightSlidesWithoutInflatingAndBothMaterialsStillFold() {
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue("Do not touch a real account", AuthStore(context).sessions.isEmpty())
        val oldReduced = AppSettings.reduceAnimation.value
        val oldBlur = AppSettings.reduceDynamicBlur.value
        AppSettings.setReduceAnimation(false)
        AppSettings.setReduceDynamicBlur(false)
        val selected = mutableIntStateOf(0)
        val glass = mutableStateOf(false)
        val connection = FloatingTabBarScrollConnection(scrollThresholdPx = 30f)
        val lensFrames = Collections.synchronizedList(mutableListOf<IntSize>())
        val backdrop = object : Backdrop {
            override val isCoordinatesDependent = true
            override fun DrawScope.drawBackdrop(
                density: Density, coordinates: LayoutCoordinates?,
                layerBlock: (GraphicsLayerScope.() -> Unit)?,
            ) {
                coordinates?.size?.let(lensFrames::add)
                drawRect(Color.DarkGray)
            }
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    BitChordTheme(darkTheme = true) {
                        CompositionLocalProvider(
                            LocalLiquidGlassEnabled provides glass.value,
                            LocalAppBackdrop provides backdrop,
                        ) {
                            Box(Modifier.fillMaxSize().background(Color.DarkGray)) {
                                FloatingTabBar(
                                    selectedTabKey = selected.intValue,
                                    scrollConnection = connection,
                                    modifier = Modifier.align(Alignment.Center)
                                        .fillMaxWidth().padding(16.dp),
                                    colors = FloatingTabBarDefaults.colors(
                                        backgroundColor = Color.Black,
                                        accessoryBackgroundColor = Color.Black,
                                        indicatorColor = Color(0xffb86889),
                                    ),
                                    elevations = FloatingTabBarDefaults.elevations(0.dp, 0.dp),
                                    sizes = FloatingTabBarDefaults.sizes(
                                        tabBarContentPadding = PaddingValues(6.dp),
                                        tabExpandedContentPadding = PaddingValues(vertical = 9.dp),
                                    ),
                                    inlineAccessory = { modifier, _ -> Text("Now playing", modifier.padding(12.dp), color = Color.White) },
                                    expandedAccessory = { modifier, _ -> Text("Now playing", modifier.fillMaxWidth().padding(12.dp), color = Color.White) },
                                ) {
                                    listOf("Home", "Explore", "Library").forEachIndexed { index, label ->
                                        tab(key = index,
                                            icon = { Text(label.take(1), Modifier.size(25.dp).semantics { contentDescription = label }, color = Color.White) },
                                            title = { Text(label, color = Color.White) },
                                            onClick = { selected.intValue = index }, indication = null)
                                    }
                                    standaloneTab(key = 3,
                                        icon = { Text("S", Modifier.size(25.dp).semantics { contentDescription = "Search" }, color = Color.White) },
                                        onClick = { selected.intValue = 3 }, indication = null)
                                }
                            }
                        }
                    }
                } }
                compose.waitForIdle()
                val rest = screenshot("tabs-regular-rest")
                assertTrue("A regular selection capsule is visible", rest.width() > 100)
                compose.mainClock.autoAdvance = false
                compose.onNodeWithContentDescription("Explore").performClick()
                advance(64)
                val early = screenshot("tabs-regular-moving-early")
                advance(96)
                val middle = screenshot("tabs-regular-moving-middle")
                listOf(early, middle).forEach { frame ->
                    assertTrue("Regular mode keeps its width ($rest → $frame)", abs(frame.width() - rest.width()) <= 4)
                    assertTrue("Regular mode keeps its height ($rest → $frame)", abs(frame.height() - rest.height()) <= 4)
                }
                assertTrue("The regular capsule moves between frames", early.centerX() > rest.centerX() + 8 && middle.centerX() > early.centerX() + 8)
                assertTrue("Regular mode does not sample the glass lens", lensFrames.isEmpty())
                compose.mainClock.autoAdvance = true
                compose.waitForIdle()
                // A two-cell return to the first tab previously let the spring
                // overshoot the bar's clip and cut the capsule's rounded edge.
                compose.onNodeWithContentDescription("Library").performClick()
                compose.waitForIdle()
                compose.mainClock.autoAdvance = false
                compose.onNodeWithContentDescription("Home").performClick()
                repeat(12) { frame ->
                    advance(32)
                    val edge = screenshot("tabs-regular-edge-$frame")
                    assertTrue("The full capsule remains visible at the edge ($rest → $edge)",
                        abs(edge.width() - rest.width()) <= 4 && abs(edge.height() - rest.height()) <= 4)
                    assertTrue("The capsule cannot overshoot the first cell", edge.left >= rest.left - 2)
                }
                compose.mainClock.autoAdvance = true
                compose.waitForIdle()
                compose.onNodeWithContentDescription("Explore").performClick()
                compose.waitForIdle()
                compose.onNodeWithContentDescription("Explore").performTouchInput {
                    swipe(start = Offset(width * 0.1f, height / 2f), end = Offset(width * 0.9f, height / 2f), durationMillis = 200)
                }
                compose.runOnIdle { assertEquals(2, selected.intValue) }
                compose.onNodeWithContentDescription("Search").performClick()
                compose.runOnIdle { assertEquals(3, selected.intValue) }
                compose.onNodeWithContentDescription("Home").performClick()
                compose.waitForIdle()

                // The actual glass renderer reports its lifted coordinates to
                // this offline backdrop, proving its inflated motion remains.
                compose.runOnIdle { glass.value = true; lensFrames.clear() }
                compose.waitForIdle()
                compose.mainClock.autoAdvance = false
                compose.onNodeWithContentDescription("Explore").performClick()
                advance(96)
                screenshot("tabs-glass-moving")
                assertTrue("Glass keeps its raised lens", lensFrames.any { it.height > rest.height() + 8 })
                // Changing material mid-flight must drop the glass geometry.
                compose.runOnIdle { glass.value = false }
                advance(32)
                val changed = screenshot("tabs-glass-to-regular")
                assertTrue(abs(changed.width() - rest.width()) <= 4 && abs(changed.height() - rest.height()) <= 4)
                compose.mainClock.autoAdvance = true
                compose.waitForIdle()

                for (material in listOf(false, true)) {
                    compose.runOnIdle { glass.value = material; connection.inline() }
                    compose.waitForIdle()
                    compose.onNodeWithText("Home").assertDoesNotExist()
                    compose.onNodeWithContentDescription("Explore").assertExists()
                    compose.onNodeWithContentDescription("Search").assertExists()
                    compose.onNodeWithText("Now playing").assertExists()
                    compose.runOnIdle { connection.expand() }
                    compose.waitForIdle()
                    compose.onNodeWithText("Home").assertExists()
                }
                compose.runOnIdle { glass.value = false; AppSettings.setReduceAnimation(true) }
                compose.waitForIdle()
                compose.mainClock.autoAdvance = false
                compose.onNodeWithContentDescription("Library").performClick()
                advance(32)
                val snapped = screenshot("tabs-regular-reduced")
                val targetX = compose.onNodeWithContentDescription("Library").fetchSemanticsNode().boundsInRoot.center.x
                assertTrue("Reduced motion lands immediately", abs(snapped.centerX() - targetX) < 8)
            }
        } finally {
            compose.mainClock.autoAdvance = true
            AppSettings.setReduceAnimation(oldReduced)
            AppSettings.setReduceDynamicBlur(oldBlur)
        }
    }

    private fun advance(ms: Long) {
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
        SystemClock.sleep(64) // Present the frozen Compose frame on Android's renderer.
    }

    private fun screenshot(name: String): Rect {
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val output = File(context.getExternalFilesDir(null), "canary-4-qa/$name.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // This scene contains one pink capsule. Include all its pixels because
        // labels can split that fill into disconnected parts during travel.
        var left = bitmap.width
        var top = bitmap.height
        var right = -1
        var bottom = -1
        for (y in 0 until bitmap.height step 2) for (x in 0 until bitmap.width step 2) {
            val pixel = bitmap.getPixel(x, y)
            if (abs(android.graphics.Color.red(pixel) - 184) < 12 &&
                abs(android.graphics.Color.green(pixel) - 104) < 12 &&
                abs(android.graphics.Color.blue(pixel) - 137) < 12) {
                left = minOf(left, x); top = minOf(top, y)
                right = maxOf(right, x); bottom = maxOf(bottom, y)
            }
        }
        bitmap.recycle()
        return if (right >= left) Rect(left, top, right + 2, bottom + 2) else Rect()
    }
}
