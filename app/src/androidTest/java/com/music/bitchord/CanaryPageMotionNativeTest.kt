package com.music.bitchord

import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.ui.components.LibraryNavigation
import com.music.bitchord.ui.components.appPageTransition
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual navigation container at intermediate frames, including reversed and reduced motion. */
@RunWith(AndroidJUnit4::class)
class CanaryPageMotionNativeTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun tabsTravelContinuouslyInBothDirectionsAndReducedMotionSettlesImmediately() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue(AuthStore(context).sessions.isEmpty())
        val route = mutableStateOf("tab:0")
        val reduced = mutableStateOf(false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    LibraryNavigation(targetState = route.value, libraryRoutes = emptySet(), selection = null,
                        reduceMotion = reduced.value, onSelectCard = { _, _, _, _ -> },
                        transitionSpec = { appPageTransition() }, modifier = Modifier.fillMaxSize(), label = "native page") { key ->
                        Box(Modifier.fillMaxSize().testTag(key).background(if (key == "tab:0") Color.Red else Color.Blue))
                    }
                }
            } }
            compose.waitForIdle()
            val width = compose.onNodeWithTag("tab:0").fetchSemanticsNode().boundsInRoot.width
            compose.mainClock.autoAdvance = false
            compose.runOnIdle { route.value = "tab:2" }
            advance(48)
            val early = left("tab:2")
            val outgoing = left("tab:0")
            assertTrue("Incoming page travels from the right", early > 0 && early < width)
            assertTrue("Outgoing page remains present during travel", outgoing < 0 && outgoing > -width)
            advance(64)
            assertTrue("Incoming page advances each frame", left("tab:2") < early)
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            assertTrue(kotlin.math.abs(left("tab:2")) < 1)

            compose.mainClock.autoAdvance = false
            compose.runOnIdle { route.value = "tab:0" }
            advance(48)
            assertTrue("Return reverses direction", left("tab:0") < 0)
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            compose.runOnIdle { reduced.value = true }
            compose.mainClock.autoAdvance = false
            compose.runOnIdle { route.value = "tab:2" }
            advance(16)
            assertTrue("Reduced motion has no intermediate slide", kotlin.math.abs(left("tab:2")) < 1)
            compose.mainClock.autoAdvance = true
        }
    }
    private fun left(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.left
    private fun advance(ms: Long) {
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }
}
