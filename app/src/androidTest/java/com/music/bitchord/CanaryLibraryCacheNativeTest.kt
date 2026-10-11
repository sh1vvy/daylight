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
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.library.*
import com.music.bitchord.data.model.*
import com.sh1vvy.daylight.library.LibraryOverviewStore
import com.music.bitchord.ui.MainViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A real ViewModel reopen, entirely from account-partitioned metadata on the emulator. */
@RunWith(AndroidJUnit4::class)
class CanaryLibraryCacheNativeTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun libraryAndLikedListsRestoreTogetherAndNeverLeakAcrossProfiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName.endsWith(".dev"))
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assumeTrue(AuthStore(context).sessions.isEmpty())
        val identity = "canary13:profile"
        val language = com.music.bitchord.data.innertube.Innertube.currentLanguage
        val songs = (1..1278).map { Song("qa-liked-$it", "Liked $it", "Daylight QA", null) }
        val page = LibraryPage(songs, emptyList(), listOf(HomeShelf("Playlists", (1..100).map {
            ShelfItem("Saved playlist $it", "Daylight QA", null, null, "VLqa-$it")
        }), HomeShelf("Albums", listOf(ShelfItem("Saved album", "Daylight QA", null, null, "MPREqa")))))
        runBlocking {
            CollectionMetadataStore.save(likedMetadataScope(identity), CollectionSnapshot(YtMusicRepository.LIKED_MUSIC,
                "Liked songs", songs = songs, complete = true, updatedAt = System.currentTimeMillis()))
            LibraryOverviewStore.save(identity, language, page, System.currentTimeMillis())
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var model: MainViewModel
                scenario.onActivity { activity ->
                    model = ViewModelProvider(activity)[MainViewModel::class.java]
                    field<MutableStateFlow<String?>>(model, "_activeAccountId").value = "canary13"
                    field<MutableStateFlow<String?>>(model, "_activeProfileId").value = "profile"
                    field<MutableStateFlow<Boolean>>(model, "_signedIn").value = true
                    clear(model)
                    model.loadLibrary()
                }
                compose.waitUntil(10_000) { (model.library.value as? UiState.Success)?.data?.likedSongs?.size == 1278 }
                scenario.onActivity {
                    val restored = (model.library.value as UiState.Success).data
                    assertEquals(page.shelves, restored.shelves)
                    assertTrue("Cached likes also seed Play my mix", com.music.bitchord.playback.DaylightMixRepository.snapshot().isNotEmpty())
                    repeat(5) { model.loadLibrary() }
                    model.openDetail(YtMusicRepository.LIKED_MUSIC, "Liked songs")
                    assertEquals(songs, (model.detailStack.value.single().songs as UiState.Success).data)
                    model.clearDetail()
                    field<MutableStateFlow<String?>>(model, "_activeProfileId").value = "other"
                    clear(model)
                    assertTrue(model.library.value is UiState.Loading)
                    model.openDetail(YtMusicRepository.LIKED_MUSIC, "Liked songs")
                    assertTrue(model.detailStack.value.single().songs is UiState.Loading)
                    model.clearDetail()
                }
                assertNull(runBlocking { LibraryOverviewStore.read("canary13:other", language) })
            }
        } finally {
            runBlocking { CollectionMetadataStore.removeScope(likedMetadataScope(identity)); LibraryOverviewStore.remove(identity) }
        }
    }

    private fun clear(model: MainViewModel) = MainViewModel::class.java.getDeclaredMethod("clearListenerState", Boolean::class.javaPrimitiveType)
        .apply { isAccessible = true }.invoke(model, false)
    @Suppress("UNCHECKED_CAST")
    private fun <T> field(model: MainViewModel, name: String): T = MainViewModel::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.get(model) as T
}
