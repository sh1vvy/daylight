/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord

import android.graphics.Bitmap
import android.os.Build
import android.view.View
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.ui.feedback.AppFeedback
import com.music.bitchord.ui.feedback.AppFeedbackHost
import com.music.bitchord.ui.theme.BitChordTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android windows verify that feedback stays above modal content and passes input through. */
@RunWith(AndroidJUnit4::class)
class CanaryAppFeedbackNativeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun themedFeedbackReplacesOldMessagesWithoutTakingFocusAndClearsInBackground() {
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue(AuthStore(context).sessions.isEmpty())
        for ((name, dark, pink) in listOf(Triple("light", false, false), Triple("dark", true, false), Triple("pink", false, true))) {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var modalView: View? = null
                var fieldFocused = false
                var clicked = false
                scenario.onActivity { activity -> activity.setContent {
                    BitChordTheme(darkTheme = dark, pinkCloud = pink) {
                        Dialog(onDismissRequest = {}) {
                            modalView = LocalView.current
                            Surface {
                                Column(Modifier.padding(24.dp)) {
                                    var text by remember { mutableStateOf("") }
                                    Text("Player window")
                                    TextField(text, { text = it }, modifier = Modifier.testTag("feedback-input")
                                        .onFocusChanged { fieldFocused = it.isFocused })
                                    Button(onClick = { clicked = true; AppFeedback.makeText(context, "Song added to queue").show() },
                                        modifier = Modifier.testTag("feedback-underlying-button")) { Text("Add next") }
                                }
                            }
                        }
                        AppFeedbackHost()
                    }
                } }
                compose.onNodeWithTag("feedback-input").performTextInput("Still typing")
                compose.runOnIdle { AppFeedback.makeText(context, "Saved to your library").show() }
                compose.onNodeWithTag("app-feedback").assertIsDisplayed()
                compose.onNodeWithText("Saved to your library").assertIsDisplayed()
                compose.runOnIdle {
                    assertTrue("The player window keeps focus", modalView!!.hasWindowFocus())
                    assertTrue("Typing focus is unchanged", fieldFocused)
                }
                compose.onNodeWithTag("feedback-underlying-button").performTouchInput { click() }
                compose.runOnIdle { assertTrue("The feedback window does not intercept taps", clicked) }
                compose.onNodeWithText("Song added to queue").assertIsDisplayed()
                compose.onAllNodesWithText("Saved to your library").assertCountEquals(0)
                val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                val file = File(context.getExternalFilesDir(null), "canary-12-qa/feedback-$name.png")
                file.parentFile?.mkdirs(); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
                compose.mainClock.advanceTimeBy(6_000)
                compose.waitForIdle()
                compose.onNodeWithTag("app-feedback").assertDoesNotExist()
                compose.runOnIdle { AppFeedback.makeText(context, "Temporary result", AppFeedback.LENGTH_LONG).show() }
                compose.onNodeWithText("Temporary result").assertIsDisplayed()
                scenario.moveToState(Lifecycle.State.CREATED)
                assertNull("No old toast replays when returning to the app", AppFeedback.messages.value)
            }
        }
    }
}
