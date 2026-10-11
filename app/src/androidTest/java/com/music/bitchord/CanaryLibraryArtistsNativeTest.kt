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
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.model.*
import com.music.bitchord.ui.MainViewModel
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Library artist taps stay in the collection and never need an artist catalogue request. */
@RunWith(AndroidJUnit4::class)
class CanaryLibraryArtistsNativeTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun artistOpensOnlyLikedSongsIncludingFeaturedCreditsAndUpdatesWithTheLikedSnapshot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue(AuthStore(context).sessions.isEmpty())
        val picture = File(context.cacheDir, "canary16-artist.png")
        val bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(232, 155, 173)) }
        picture.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val artist = ShelfItem("Lead", "", picture.toURI().toString(), null, "UClead")
        val songs = listOf(
            Song("one", "Liked lead song", "Lead", null, artistId = "UClead"),
            Song("two", "Liked collaboration", "Lead & Guest", null, artists = listOf(ArtistRef("Lead", "UCtopic"), ArtistRef("Guest", "UCguest"))),
            Song("three", "Unrelated liked song", "Other", null, artistId = "UCother"),
        )
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var model: MainViewModel
                compose.onNodeWithContentDescription(context.getString(R.string.library)).performClick()
                scenario.onActivity {
                    model = ViewModelProvider(it)[MainViewModel::class.java]
                    field<MutableStateFlow<Boolean>>(model, "_signedIn").value = true
                    field<MutableStateFlow<UiState<LibraryPage>>>(model, "_library").value = UiState.Success(
                        LibraryPage(songs, emptyList(), listOf(HomeShelf("Artists", listOf(artist)))))
                }
                compose.onNodeWithTag("library-category:library:artists").performClick()
                compose.onNodeWithText("Lead").performClick()
                compose.onNodeWithTag("library-artist-liked-list").assertIsDisplayed()
                compose.onNodeWithText("Liked lead song").assertIsDisplayed()
                compose.onNodeWithText("Liked collaboration").assertIsDisplayed()
                compose.onAllNodesWithText("Unrelated liked song").assertCountEquals(0)
                assertTrue(model.detailStack.value.last().browseId.startsWith(LIBRARY_ARTIST_SONGS_PREFIX))
                assertEquals(artist.thumbnailUrl, model.detailStack.value.last().thumbnailUrl)
                assertTrue(model.detailStack.value.last().sections.isEmpty())
                scenario.onActivity {
                    MainViewModel::class.java.getDeclaredMethod("dropFromLikedLists", String::class.java)
                        .apply { isAccessible = true }.invoke(model, "one")
                }
                compose.onAllNodesWithText("Liked lead song").assertCountEquals(0)
                compose.onNodeWithText("Liked collaboration").assertIsDisplayed()
                androidx.test.espresso.Espresso.pressBack(); compose.waitForIdle()
                compose.onNodeWithTag("library-collection-list").assertIsDisplayed()
            }
        } finally { picture.delete() }
    }

    @Suppress("UNCHECKED_CAST") private fun <T> field(model: MainViewModel, name: String): T =
        MainViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }.get(model) as T
}
