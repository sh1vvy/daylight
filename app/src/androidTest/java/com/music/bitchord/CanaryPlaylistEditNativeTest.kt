package com.music.bitchord

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
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
import com.music.bitchord.data.library.PlaylistCoverStore
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.data.spotify.LocalPlaylistStore
import com.music.bitchord.ui.MainViewModel
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real app editing without an account; bounded photo persistence and artwork navigation. */
@RunWith(AndroidJUnit4::class)
class CanaryPlaylistEditNativeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun editsPersistWithoutSignInAndArtworkDoesNotChangeTheQueue() {
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue(AuthStore(context).sessions.isEmpty())
        assumeTrue(LocalPlaylistStore.playlists.value.isEmpty())
        val oldTheme = AppSettings.themeMode.value
        val theme = InstrumentationRegistry.getArguments().getString("theme")?.let(ThemeMode::valueOf) ?: ThemeMode.DARK
        AppSettings.setThemeMode(theme)
        val photo = File(context.cacheDir, "canary6-edit-photo.png")
        val bitmap = Bitmap.createBitmap(2400, 1800, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(171, 116, 145))
            drawCircle(1200f, 900f, 700f, Paint().apply { color = Color.rgb(250, 214, 172) })
            drawCircle(1200f, 900f, 210f, Paint().apply { color = Color.rgb(102, 73, 118) })
        }
        photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val art = Uri.fromFile(photo).toString()
        val songs = (1..3).map { Song("qa-edit-$it", "Edit track $it", "Daylight QA", art, "3:00") }
        val playlist = LocalPlaylistStore.savePlaylist("Edit canary mix", songs)
        var draft: String? = null
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var model: MainViewModel
                scenario.onActivity { model = ViewModelProvider(it)[MainViewModel::class.java] }
                compose.onNodeWithContentDescription(context.getString(R.string.library)).performClick()
                compose.onNodeWithText(playlist.title).performTouchInput { longClick() }
                compose.onNodeWithText(context.getString(R.string.edit_playlist)).performClick()
                compose.onNode(hasSetTextAction()).performTextReplacement("Cancelled name")
                compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
                compose.waitForIdle()
                assertEquals(playlist.title, LocalPlaylistStore.getPlaylist(playlist.id)!!.title)
                compose.onNodeWithText(playlist.title).performTouchInput { longClick() }
                compose.onNodeWithText(context.getString(R.string.edit_playlist)).performClick()
                compose.onNode(hasSetTextAction()).performTextReplacement("A little sunset")
                screenshot("edit-playlist-$theme")
                compose.onNodeWithText(context.getString(R.string.save)).performClick()
                compose.waitUntil(4_000) { LocalPlaylistStore.getPlaylist(playlist.id)?.title == "A little sunset" }
                compose.waitForIdle()
                compose.onNodeWithText("A little sunset").assertIsDisplayed()
                assertEquals(songs, LocalPlaylistStore.getPlaylist(playlist.id)!!.songs)
                assertTrue(model.detailStack.value.isEmpty())

                // Picker work uses the same native decoder/store as the editor, with a large image.
                draft = runBlocking { PlaylistCoverStore.prepare(Uri.fromFile(photo)) }
                val decoded = BitmapFactory.decodeFile(Uri.parse(draft).path)
                assertTrue(maxOf(decoded.width, decoded.height) <= 1024)
                decoded.recycle()
                runBlocking { PlaylistCoverStore.save(null, playlist.browseId, draft) }
                val saved = requireNotNull(PlaylistCoverStore.cover(null, playlist.browseId))
                assertNotEquals(draft, saved)
                PlaylistCoverStore.discard(draft); draft = null
                photo.delete() // The external source and temporary draft are no longer needed.
                PlaylistCoverStore.init(context)
                assertEquals(saved, PlaylistCoverStore.cover("another-account", playlist.browseId))
                assertTrue(File(Uri.parse(saved).path!!).isFile)
                compose.waitForIdle()
                screenshot("edited-library-$theme")
                compose.onNodeWithText("A little sunset").performClick()
                compose.waitUntil(5_000) { model.detailStack.value.singleOrNull()?.browseId == playlist.browseId }
                compose.waitForIdle()
                compose.onNodeWithText("Edit track 1").assertIsDisplayed()
                screenshot("edited-playlist-$theme")
                compose.onNodeWithContentDescription("View album artwork").performClick()
                compose.onNodeWithContentDescription("Close artwork").assertIsDisplayed()
                screenshot("full-screen-artwork-$theme")
                back()
                compose.onAllNodesWithContentDescription("Close artwork").assertCountEquals(0)
                compose.onNodeWithText("Edit track 1").assertIsDisplayed()
                assertEquals(songs, LocalPlaylistStore.getPlaylist(playlist.id)!!.songs)
                assertEquals(playlist.browseId, model.detailStack.value.single().browseId)

                // Replacing/removing covers deletes old private copies and restores provider art.
                draft = runBlocking { PlaylistCoverStore.prepare(Uri.parse(saved)) }
                runBlocking { PlaylistCoverStore.save(null, playlist.browseId, draft) }
                assertFalse(File(Uri.parse(saved).path!!).exists())
                PlaylistCoverStore.discard(draft); draft = null
                val replacement = requireNotNull(PlaylistCoverStore.cover(null, playlist.browseId))
                runBlocking { PlaylistCoverStore.save(null, playlist.browseId, null) }
                assertNull(PlaylistCoverStore.cover(null, playlist.browseId))
                assertFalse(File(Uri.parse(replacement).path!!).exists())
            }
        } finally {
            PlaylistCoverStore.discard(draft)
            runBlocking { PlaylistCoverStore.save(null, playlist.browseId, null) }
            LocalPlaylistStore.deletePlaylist(playlist.id)
            val prefs = context.getSharedPreferences("bitchord_local_playlists", android.content.Context.MODE_PRIVATE)
            val deadline = SystemClock.uptimeMillis() + 3_000
            while (prefs.getString("playlists_json", "[]") != "[]" && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
            assertEquals("[]", prefs.getString("playlists_json", "[]"))
            photo.delete()
            AppSettings.setThemeMode(oldTheme)
        }
    }

    private fun back() { instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); compose.waitForIdle() }
    private fun screenshot(name: String) {
        compose.waitForIdle(); SystemClock.sleep(200)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val out = File(context.getExternalFilesDir(null), "canary-6-qa/$name.png")
        out.parentFile?.mkdirs()
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
