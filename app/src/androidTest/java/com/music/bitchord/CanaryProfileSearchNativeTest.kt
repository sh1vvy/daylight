package com.music.bitchord

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.auth.GoogleAccountSession
import com.music.bitchord.auth.YouTubeProfile
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.SearchSource
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise the actual Compose screen through Android accessibility, without any real login. */
@RunWith(AndroidJUnit4::class)
class CanaryProfileSearchNativeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val automation get() = instrumentation.uiAutomation

    @Test
    fun profileMenuRoutesAndSearchEditingRemainUsableOnTheNativeScreen() {
        // This fixture is deliberately restricted to the disposable development emulator.
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.VERSION.SDK_INT >= 28)
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val store = AuthStore(context)
        assumeTrue("This smoke test never replaces a real account", store.sessions.all { it.accountId == FAKE_ACCOUNT_ID })
        val previousTheme = AppSettings.themeMode.value
        InstrumentationRegistry.getArguments().getString("theme")?.let { requested ->
            AppSettings.setThemeMode(ThemeMode.valueOf(requested))
        }
        val previousAccount = store.activeAccountId
        val previousProfile = store.activeProfileId
        val previousCookie = store.cookie
        val profile = YouTubeProfile(FAKE_PROFILE_ID, "Daylight QA", handle = "qa@daylight.invalid")
        store.upsertSession(GoogleAccountSession(
            accountId = FAKE_ACCOUNT_ID,
            cookie = "SAPISID=daylight-ui-test-placeholder",
            name = "Daylight QA",
            email = "qa@daylight.invalid",
            profiles = listOf(profile),
            activeProfileId = profile.profileId,
        ))
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        try {
            automation.grantRuntimePermission(context.packageName, permission)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                clickNode("home profile button") { it.contentDescription?.toString() == text(R.string.switch_account) }
                waitText(text(R.string.switch_account))
                waitText(text(R.string.manage_accounts))
                waitText(text(R.string.settings))
                waitText("Daylight QA")
                screenshot("profile-menu-native")

                // Both Close and Android Back must dismiss the animated selector.
                clickNode("menu close button") { it.contentDescription?.toString() == text(R.string.close) }
                waitAbsent { hasText(it, text(R.string.switch_account)) }
                openProfileMenu()
                back()
                waitAbsent { hasText(it, text(R.string.switch_account)) }

                openProfileMenu()
                clickText(text(R.string.manage_accounts))
                waitText(text(R.string.account_integrations))
                waitAbsent { hasText(it, text(R.string.manage_accounts)) }
                screenshot("accounts-integrations-native")
                back()
                waitText(text(R.string.appearance))
                back()
                waitAbsent { hasText(it, text(R.string.appearance)) }

                openProfileMenu()
                clickText(text(R.string.settings))
                waitText(text(R.string.appearance))
                waitAbsent { hasText(it, text(R.string.manage_accounts)) }
                screenshot("settings-native")
                clickText(text(R.string.appearance))
                waitText(text(R.string.theme))
                waitText(text(R.string.liquid_glass))
                screenshot("settings-expanded-native")
                clickText(text(R.string.appearance))
                waitAbsent { hasText(it, text(R.string.theme)) }
                back()
                waitAbsent { hasText(it, text(R.string.appearance)) }

                val height = context.resources.displayMetrics.heightPixels
                clickNode("bottom Search tab") {
                    it.contentDescription?.toString() == text(R.string.search) && bounds(it).centerY() > height / 2
                }
                waitNode("search editor") { it.isEditable }
                waitText(text(R.string.search_source_youtube))
                screenshot("search-native")

                clickNode("Library search source") {
                    hasText(it, text(R.string.library)) && bounds(it).centerY() < height / 2
                }
                scenario.onActivity { activity ->
                    assertEquals(SearchSource.LIBRARY, ViewModelProvider(activity)[MainViewModel::class.java].searchSource.value)
                }
                screenshot("search-library-native")
                clickNode("YouTube search source") {
                    hasText(it, text(R.string.search_source_youtube)) && bounds(it).centerY() < height / 2
                }
                scenario.onActivity { activity ->
                    assertEquals(SearchSource.YOUTUBE, ViewModelProvider(activity)[MainViewModel::class.java].searchSource.value)
                }

                val query = "daylight canary preview check"
                setSearchText(query)
                clickNode("search submit button") {
                    it.contentDescription?.toString() == text(R.string.search) && bounds(it).centerY() < height / 2 && it.isEnabled
                }
                clickNode("clear search") { it.contentDescription?.toString() == text(R.string.clear_search) }
                waitNode("empty search editor") { it.isEditable && it.text.isNullOrEmpty() }
                instrumentation.waitForIdleSync()
                // Assert the immediate state contract, without depending on a provider response time.
                scenario.onActivity { activity ->
                    val model = ViewModelProvider(activity)[MainViewModel::class.java]
                    assertEquals("", model.query.value)
                    assertTrue(model.suggestions.value.isEmpty())
                    assertTrue(model.typeaheadResults.value.isEmpty())
                    assertNull(model.results.value)
                    assertFalse(model.searchLoadingMore.value)
                }
                screenshot("search-cleared-native")
            }
        } finally {
            // Do not sign out, clear encrypted storage or touch any unrelated integration.
            val remaining = store.sessions.filterNot { it.accountId == FAKE_ACCOUNT_ID }
            // The activity also mirrors the selected session into the legacy
            // cookie. Restore it so the fixture cannot recreate a session later.
            store.cookie = previousCookie
            // Remove the legacy fixture cookie first: reading an empty registry
            // while it remains would migrate it straight back into the registry.
            store.replaceSessions(remaining)
            store.activeAccountId = previousAccount
            store.activeProfileId = previousProfile
            AppSettings.setThemeMode(previousTheme)
        }
    }

    private fun text(id: Int) = context.getString(id)

    private fun openProfileMenu() {
        clickNode("profile button") { it.contentDescription?.toString() == text(R.string.switch_account) }
        waitText(text(R.string.manage_accounts))
    }

    private fun back() {
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }

    private fun setSearchText(value: String) {
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }
        assertTrue("Native editor must accept text", waitNode("search editor") { it.isEditable }.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args))
        waitNode("updated search editor") { it.isEditable && it.text?.toString() == value }
    }

    private fun hasText(node: AccessibilityNodeInfo, label: String): Boolean =
        node.text?.toString()?.lineSequence()?.any { it == label } == true

    private fun waitText(label: String) = waitNode(label) { hasText(it, label) }

    private fun waitNode(label: String, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val until = SystemClock.uptimeMillis() + 8_000L
        do {
            find(automation.rootInActiveWindow, predicate)?.let { return it }
            SystemClock.sleep(80)
        } while (SystemClock.uptimeMillis() < until)
        throw AssertionError("Native screen did not expose $label")
    }

    private fun waitAbsent(predicate: (AccessibilityNodeInfo) -> Boolean) {
        val until = SystemClock.uptimeMillis() + 4_000L
        do {
            if (find(automation.rootInActiveWindow, predicate) == null) return
            SystemClock.sleep(80)
        } while (SystemClock.uptimeMillis() < until)
        throw AssertionError("Dismissed native content remained visible")
    }

    private fun find(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        node ?: return null
        if (node.isVisibleToUser && predicate(node)) return node
        repeat(node.childCount) { index -> find(node.getChild(index), predicate)?.let { return it } }
        return null
    }

    private fun clickText(label: String) = clickNode(label) { hasText(it, label) }

    private fun clickNode(label: String, predicate: (AccessibilityNodeInfo) -> Boolean) {
        val until = SystemClock.uptimeMillis() + 4_000L
        do {
            // Compose can replace a merged virtual node between lookup and
            // dispatch when the Library result changes from loading to empty.
            // Reacquire the selector each attempt instead of keeping that node.
            var target = find(automation.rootInActiveWindow, predicate)
            while (target != null) {
                val supportsClick = target.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }
                // Role.Tab may expose ACTION_CLICK without isClickable=true.
                if (target.isEnabled && supportsClick && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    instrumentation.waitForIdleSync()
                    return
                }
                target = target.parent
            }
            SystemClock.sleep(80)
        } while (SystemClock.uptimeMillis() < until)
        throw AssertionError("Native control did not accept a click: $label")
    }

    private fun bounds(node: AccessibilityNodeInfo) = Rect().also(node::getBoundsInScreen)

    private fun screenshot(name: String) {
        // The selector/page transitions are 180 ms; this settles their final frame only.
        SystemClock.sleep(250)
        val bitmap = requireNotNull(automation.takeScreenshot()) { "Native screenshot unavailable" }
        val output = File(context.getExternalFilesDir(null), "canary-2-qa/$name.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    companion object {
        private const val FAKE_ACCOUNT_ID = "daylight-canary-native-qa"
        private const val FAKE_PROFILE_ID = "daylight-canary-native-qa-profile"
    }
}
