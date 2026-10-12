/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-12 through 2026-10-12.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.ui.MainViewModel
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Actual phone-sized app/IME flow. Never runs against the user's account or phone. */
@RunWith(AndroidJUnit4::class)
class CanarySearchFlowNativeTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context get() = inst.targetContext
    private val ui get() = inst.uiAutomation
    private fun text(id: Int) = context.getString(id)
    private fun requireEmulator() {
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue(AuthStore(context).sessions.isEmpty())
        ui.grantRuntimePermission(context.packageName, if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    @Test fun bottomEditorFollowsKeyboardRetainsQueryAndRestoresPreviousTabAcrossThemes() {
        requireEmulator()
        val originalTheme = AppSettings.themeMode.value
        val originalGlass = AppSettings.liquidGlass.value
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                for (theme in listOf(ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.PINK_CLOUD)) for (glass in listOf(false, true)) {
                    scenario.onActivity { AppSettings.setThemeMode(theme); AppSettings.setLiquidGlass(glass) }
                    click { it.contentDescription?.toString() == text(R.string.library) && rect(it).centerY() > screenHeight() / 2 }
                    click { it.contentDescription?.toString() == text(R.string.search) && rect(it).centerY() > screenHeight() / 2 }
                    val editor = waitNode { it.isEditable }
                    assertTrue("Editor must remain in lower half", rect(editor).centerY() > screenHeight() / 3)
                    assertNull(find(ui.rootInActiveWindow) { it.contentDescription?.toString() == text(R.string.home) })
                    setText("daylight native search")
                    screenshot("${theme.name.lowercase()}-$glass-keyboard")
                    waitUntil { imeVisible(scenario) }
                    nativeBack()
                    waitUntil { !imeVisible(scenario) }
                    assertEquals("daylight native search", waitNode { it.isEditable }.text?.toString())
                    screenshot("${theme.name.lowercase()}-$glass-settled")
                    click { it.contentDescription?.toString() == text(R.string.close) && rect(it).centerY() > screenHeight() / 2 }
                    waitNode { it.text?.toString() == text(R.string.library) && rect(it).centerY() < screenHeight() / 2 }
                    waitUntil { find(ui.rootInActiveWindow) { it.isEditable } == null }
                    scenario.onActivity { assertEquals("daylight native search", ViewModelProvider(it)[MainViewModel::class.java].query.value) }
                    click { it.contentDescription?.toString() == text(R.string.search) && rect(it).centerY() > screenHeight() / 2 }
                    assertEquals("daylight native search", waitNode { it.isEditable }.text?.toString())
                    click { it.contentDescription?.toString() == text(R.string.clear_search) }
                    waitNode { it.isEditable && it.text.isNullOrEmpty() }
                    scenario.onActivity {
                        val state = ViewModelProvider(it)[MainViewModel::class.java].searchState.value
                        assertTrue(state.suggestions.isEmpty()); assertTrue(state.previews.isEmpty())
                        assertNull(state.rows); assertFalse(state.loadingMore)
                    }
                    // Native Back dismisses IME first; Close exits Search explicitly.
                    nativeBack()
                    click { it.contentDescription?.toString() == text(R.string.close) && rect(it).centerY() > screenHeight() / 2 }
                }
            }
        } finally { AppSettings.setThemeMode(originalTheme); AppSettings.setLiquidGlass(originalGlass) }
    }

    @Test fun localSourceAndSubmitClearRemainAccessibleWithoutProviderTiming() {
        requireEmulator()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            click { it.contentDescription?.toString() == text(R.string.search) && rect(it).centerY() > screenHeight() / 2 }
            setText("daylight")
            click { it.contentDescription?.toString() == text(R.string.search) && it.isEnabled }
            waitNode { it.isEditable && it.text?.toString() == "daylight" }
            screenshot("submitted")
            click { it.contentDescription?.toString() == text(R.string.clear_search) }
            click { it.text?.toString() == text(R.string.library) && rect(it).centerY() > screenHeight() / 2 }
            setText("a local track")
            scenario.onActivity {
                val model = ViewModelProvider(it)[MainViewModel::class.java]
                assertTrue(model.searchState.value.suggestions.isEmpty())
                assertTrue(model.searchState.value.previews.isEmpty())
            }
            screenshot("local-search")
            click { it.contentDescription?.toString() == text(R.string.clear_search) }
            click { it.text?.toString() == text(R.string.daylight_search_youtube) }
            scenario.onActivity { assertEquals("", ViewModelProvider(it)[MainViewModel::class.java].query.value) }
        }
    }

    private fun screenHeight() = context.resources.displayMetrics.heightPixels
    private fun rect(node: AccessibilityNodeInfo) = Rect().also(node::getBoundsInScreen)
    private fun find(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        node ?: return null
        if (node.isVisibleToUser && predicate(node)) return node
        repeat(node.childCount) { find(node.getChild(it), predicate)?.let { found -> return found } }
        return null
    }
    private fun waitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val until = SystemClock.uptimeMillis() + 8_000
        do {
            // Compose reuses the editor node while its parent changes material/placement.
            // Query the live tree rather than a cached pre-morph virtual node.
            if (Build.VERSION.SDK_INT >= 33) ui.clearCache()
            find(ui.rootInActiveWindow, predicate)?.let { return it }
            SystemClock.sleep(80)
        } while (SystemClock.uptimeMillis() < until)
        val nodes = mutableListOf<String>()
        fun describe(node: AccessibilityNodeInfo?) {
            node ?: return
            if (node.isEditable || !node.text.isNullOrEmpty()) nodes += "${node.className}:${node.text}:${rect(node)}"
            repeat(node.childCount) { describe(node.getChild(it)) }
        }
        describe(ui.rootInActiveWindow)
        screenshot("failed-control")
        throw AssertionError("Search control missing: $nodes")
    }
    private fun waitUntil(predicate: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 5_000
        do { if (predicate()) return; SystemClock.sleep(80) } while (SystemClock.uptimeMillis() < until)
        screenshot("failed-transition")
        throw AssertionError("Search transition did not settle")
    }
    private fun nativeBack() {
        ui.executeShellCommand("input keyevent 4").use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }
    private fun imeVisible(scenario: ActivityScenario<MainActivity>): Boolean {
        var visible = false
        scenario.onActivity { visible = it.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true }
        return visible
    }
    private fun click(predicate: (AccessibilityNodeInfo) -> Boolean) {
        var node: AccessibilityNodeInfo? = waitNode(predicate)
        while (node != null) {
            if (node.isEnabled && node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK } && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                inst.waitForIdleSync(); SystemClock.sleep(400); return
            }
            node = node.parent
        }
        throw AssertionError("Search control did not accept click")
    }
    private fun setText(value: String) {
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }
        assertTrue(waitNode { it.isEditable }.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args))
        waitNode { it.isEditable && it.text?.toString() == value }
    }
    private fun screenshot(name: String) {
        SystemClock.sleep(300)
        val image = requireNotNull(ui.takeScreenshot())
        val file = File(context.getExternalFilesDir(null), "canary-20-search/$name.png")
        file.parentFile?.mkdirs(); file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
    }
}
