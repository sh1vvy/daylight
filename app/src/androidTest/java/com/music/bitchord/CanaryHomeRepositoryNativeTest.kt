/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord

import android.os.Build
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.library.*
import com.music.bitchord.data.model.*
import com.music.bitchord.ui.MainViewModel
import com.sh1vvy.daylight.home.*
import com.sh1vvy.daylight.library.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** No authentication or real playlist writes: retained metadata exercised through the actual screen model. */
@RunWith(AndroidJUnit4::class)
class CanaryHomeRepositoryNativeTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun cachedHomeRemotePlaylistAndEditablePickerRestoreWithAllTheirMetadata() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue(AuthStore(context).sessions.isEmpty())
        val identity = "canary14:profile"
        val homeKey = HomeContext(identity, Innertube.currentLanguage, Innertube.responseCacheScope)
        val key = CollectionContext(identity, homeKey.language, homeKey.sourceRevision, "VLqa-remote14")
        val songs = (1..500).map { Song("qa-song-$it", "Retained track $it", "Daylight QA", null, setVideoId = "occurrence-$it") }
        val artistShelf = HomeShelf("Artists in this playlist", listOf(ShelfItem("Daylight QA", "", null, null, "UCqa")))
        val home = HomeSnapshot(listOf(HomeShelf("Listen again", listOf(ShelfItem("Remote playlist", "Daylight QA", null, null, key.id)))),
            HomeShelf("Recents", listOf(ShelfItem("Retained track 1", "Daylight QA", null, songs.first().videoId, null))), emptyList(), System.currentTimeMillis())
        val directory = (1..80).map { UserPlaylist("qa-own-$it", "Owned playlist $it", "Daylight QA", null) }
        DaylightHomeStorage.init(context); DaylightPlaylistDirectoryStorage.init(context)
        runBlocking {
            DaylightHomeStorage.save(homeKey, home) { true }
            DaylightPlaylistDirectoryStorage.save(key, CollectionListing(Unit, directory, complete = true, checkedAt = System.currentTimeMillis())) { true }
            CollectionMetadataStore.save(key.storageScope, CollectionSnapshot(key.id, "Remote playlist", "Daylight QA", creator = PlaylistCreator("Daylight QA", "UCqa"),
                songs = songs, complete = true, updatedAt = System.currentTimeMillis(), description = "Retained description", owned = true,
                sections = listOf(artistShelf), language = key.language, library = LibraryState("qa-remote14", true)))
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var model: MainViewModel
                scenario.onActivity { activity ->
                    model = ViewModelProvider(activity)[MainViewModel::class.java]
                    field<MutableStateFlow<String?>>(model, "_activeAccountId").value = "canary14"
                    field<MutableStateFlow<String?>>(model, "_activeProfileId").value = "profile"
                    field<MutableStateFlow<Boolean>>(model, "_signedIn").value = true
                    clear(model); model.loadHome(); model.loadPlaylists(); model.openDetail(key.id, "Remote playlist")
                }
                compose.waitUntil(10_000) {
                    model.playlists.value.size == 80 && (model.detailStack.value.firstOrNull()?.songs as? UiState.Success)?.data?.size == 500 &&
                        (model.home.value as? UiState.Success)?.data?.firstOrNull()?.title == "Recents"
                }
                scenario.onActivity {
                    val page = model.detailStack.value.single()
                    assertEquals(songs, (page.songs as UiState.Success).data)
                    assertEquals("Retained description", page.description); assertEquals(listOf(artistShelf), page.sections)
                    assertEquals(LibraryState("qa-remote14", true), page.library)
                    assertNotNull(model.editablePlaylist(directory.first().browseId))
                    model.clearDetail(); model.openDetail(key.id, "Remote playlist")
                }
                compose.waitUntil(5_000) { (model.detailStack.value.firstOrNull()?.songs as? UiState.Success)?.data == songs }
                scenario.onActivity {
                    model.clearDetail()
                    field<MutableStateFlow<String?>>(model, "_activeProfileId").value = "other"
                    clear(model)
                    model.openDetail(key.id, "Remote playlist")
                    assertTrue(model.detailStack.value.single().songs is UiState.Loading)
                    assertTrue(model.playlists.value.isEmpty()); model.clearDetail()
                }
                assertNull(runBlocking { DaylightHomeStorage.read(homeKey.copy(identity = "canary14:other")) })
                assertNull(runBlocking { DaylightPlaylistDirectoryStorage.read(key.copy(identity = "canary14:other")) })
            }
        } finally { runBlocking {
            CollectionMetadataStore.removeScope(key.storageScope); DaylightHomeStorage.remove(identity); DaylightPlaylistDirectoryStorage.remove(identity)
        } }
    }
    private fun clear(model: MainViewModel) = MainViewModel::class.java.getDeclaredMethod("clearListenerState", Boolean::class.javaPrimitiveType)
        .apply { isAccessible = true }.invoke(model, false)
    @Suppress("UNCHECKED_CAST") private fun <T> field(model: MainViewModel, name: String): T = MainViewModel::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.get(model) as T
}
