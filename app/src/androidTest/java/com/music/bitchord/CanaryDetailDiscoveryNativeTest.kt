package com.music.bitchord

import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.BoundedRequestCache
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.model.*
import com.music.bitchord.ui.screens.DetailScreen
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real scrollable detail rows, with warmed catalogue headers and no live source requests. */
@RunWith(AndroidJUnit4::class)
class CanaryDetailDiscoveryNativeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val vertical = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
    private val horizontal = SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)

    @Test fun albumCardsAndPlaylistPortraitsOpenTheirOwnDestinations() {
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue(AuthStore(context).sessions.isEmpty())
        val artists = (1..8).map { ArtistRef("Featured artist $it", "UCqa-discovery-$it") }
        val discography = (1..8).map { ShelfItem("Other album $it", "Featured artist 1", null, null, "MPREbqa-more-$it") }
        val related = (1..8).map { ShelfItem("Similar album $it", "Other artist", null, null, "MPREbqa-similar-$it") }
        val songs = (1..5).map { Song("qa-discovery-$it", "Fixture track $it", artists.first().name, null,
            artistId = artists.first().browseId, artists = artists) }
        artists.forEach { warmArtist(it, if (it == artists.first()) discography else emptyList()) }
        var openedAlbum: String? = null
        var openedArtist: String? = null
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                fun show(page: DetailPage) { scenario.onActivity { activity -> activity.setContent {
                    BitChordTheme(darkTheme = true) {
                        DetailScreen(page, null, false, { _, _ -> }, {}, {}, {}, {},
                            onSectionItemClick = { openedAlbum = it.browseId },
                            onArtistClick = { id, _ -> openedArtist = id }, onAddSuggested = {},
                            contentPadding = PaddingValues(bottom = 16.dp), modifier = Modifier)
                    }
                } } }
                show(DetailPage("MPREbqa-current", "Fixture album", "Featured artist 1", null,
                    UiState.Success(songs), BrowseType.ALBUM, sections = listOf(HomeShelf("Related albums", related))))
                compose.onNode(vertical).performScrollToNode(hasText("More by Featured artist 1"))
                compose.waitUntil(5_000) { compose.onAllNodesWithText("Other album 1").fetchSemanticsNodes().isNotEmpty() }
                reveal("Other album 1")
                compose.onNodeWithText("Other album 1").assertIsDisplayed().performClick()
                assertEquals("MPREbqa-more-1", openedAlbum)
                compose.onNode(vertical).performScrollToNode(hasText("Similar album 1"))
                reveal("Similar album 1")
                compose.onAllNodes(horizontal).onLast().performScrollToNode(hasText("Similar album 8"))
                compose.waitForIdle()
                screenshot("album-discovery-rows")
                compose.onNodeWithText("Similar album 8").assertIsDisplayed().performClick()
                assertEquals("MPREbqa-similar-8", openedAlbum)

                show(DetailPage("VLqa-playlist", "Fixture playlist", "Creator", null,
                    UiState.Success(songs), BrowseType.PLAYLIST))
                compose.onNode(vertical).performScrollToNode(hasText("Featured artists"))
                compose.onAllNodes(hasText("Featured artist 1") and hasAnyAncestor(horizontal)).assertCountEquals(1)
                reveal("Featured artist 2")
                compose.onNode(horizontal).performScrollToNode(hasText("Featured artist 8"))
                compose.onNodeWithText("Featured artist 8").assertIsDisplayed().performClick()
                assertEquals("UCqa-discovery-8", openedArtist)
                screenshot("playlist-artist-portraits")
            }
        } finally { YtMusicRepository.clearBrowseCache() }
    }

    private fun reveal(text: String) {
        // A partially visible lazy item can contain two full carousels; exercise
        // the user's vertical swipe rather than just jumping to its item index.
        repeat(8) {
            if (compose.onNodeWithText(text).isDisplayed()) return
            compose.onNode(vertical).performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val output = java.io.File(context.getExternalFilesDir(null), "canary-8-qa/$name.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Suppress("UNCHECKED_CAST")
    private fun warmArtist(artist: ArtistRef, albums: List<ShelfItem>) {
        val keyClass = Class.forName("com.music.bitchord.data.YtMusicRepository\$BrowseKey")
        val constructor = keyClass.declaredConstructors.first { it.parameterTypes.size == 4 }.apply { isAccessible = true }
        val key = constructor.newInstance(Innertube.responseCacheScope, Innertube.currentLanguage, artist.browseId, false)
        val cache = YtMusicRepository::class.java.getDeclaredField("artistPreviews").apply { isAccessible = true }
            .get(YtMusicRepository) as BoundedRequestCache<Any, ArtistPage>
        cache.put(key, ArtistPage(emptyList(), null, listOf(HomeShelf("Albums", albums)), name = artist.name))
    }
}
