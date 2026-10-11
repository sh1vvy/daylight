/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.home

import android.content.Context
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.model.*
import com.sh1vvy.daylight.library.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class HomeContext(val identity: String?, val language: String, val sourceRevision: Long)

@Serializable
private data class StoredHome(val primary: List<LibrarySection>, val recent: LibrarySection?,
    val extras: List<List<LibrarySection>>, val checkedAt: Long)

/** Only display metadata persists. Authentication, continuation tokens and play state stay out. */
object DaylightHomeStorage {
    private var cache: SnapshotCache<StoredHome>? = null
    @Synchronized fun init(context: Context) {
        if (cache != null) return
        val json = Json { ignoreUnknownKeys = true }
        cache = SnapshotCache(File(context.filesDir, "daylight-home-v1"), object : SnapshotCodec<StoredHome> {
            override fun encode(value: StoredHome) = json.encodeToString(StoredHome.serializer(), value).toByteArray(Charsets.UTF_8)
            override fun decode(bytes: ByteArray) = json.decodeFromString(StoredHome.serializer(), bytes.toString(Charsets.UTF_8))
            override fun weight(value: StoredHome) = (value.primary + listOfNotNull(value.recent) + value.extras.flatten()).sumOf { it.cards.size }
        }, CacheBudget(diskBytes = 4L * 1024 * 1024, entryBytes = 1024 * 1024, memoryRows = 2_000,
            memoryEntries = 4, entriesPerAccount = 3, diskEntries = 24))
    }
    private fun key(context: HomeContext) = SnapshotKey("youtube:${context.identity ?: "guest"}", "home:${context.language}")
    suspend fun read(context: HomeContext): HomeSnapshot<HomeShelf>? = try {
        cache?.read(key(context))?.let { saved -> HomeSnapshot(shelves(saved.primary), saved.recent?.let { shelves(listOf(it)).single() },
            saved.extras.map(::shelves), saved.checkedAt) }
    } catch (_: IOException) { null }
    suspend fun save(context: HomeContext, value: HomeSnapshot<HomeShelf>, current: () -> Boolean) {
        fun records(shelves: List<HomeShelf>) = shelves.take(20).map { shelf -> LibrarySection(shelf.title,
            shelf.items.take(24).map { LibraryCard(it.title, it.subtitle, it.thumbnailUrl, it.browseId, it.videoId) },
            shelf.subtitle, shelf.moreBrowseId, shelf.moreParams) }
        val record = StoredHome(records(value.primary), value.recent?.let { records(listOf(it)).single() },
            value.extras.map(::records), value.checkedAt)
        try { cache?.write(key(context), record, current) } catch (_: IOException) { }
    }
    suspend fun remove(identity: String) { try { cache?.removeAccount("youtube:$identity") } catch (_: IOException) { } }
    private fun shelves(records: List<LibrarySection>) = LibraryOverviewStore.page(LibraryOverview(records, 0)).shelves
}

/** The current screen consumes this adapter while orchestration lives in Daylight's own core. */
class DaylightHome(scope: CoroutineScope, context: Context, private val identity: () -> String?) {
    init { DaylightHomeStorage.init(context) }
    private val repository = HomeRepository<HomeContext, HomeShelf>(scope,
        primary = { YtMusicRepository.home().getOrThrow().let { HomeBatch(it.shelves, it.continuation) } },
        recent = { YtMusicRepository.homeRecentlyPlayed().getOrThrow() },
        extras = YtMusicRepository.HOME_SUPPLEMENT_BROWSE_IDS.map { id -> { _: HomeContext -> YtMusicRepository.homeSupplement(id).getOrThrow() } },
        more = { _, token -> YtMusicRepository.moreHome(token).getOrThrow().let { HomeBatch(it.shelves, it.continuation) } },
        read = { DaylightHomeStorage.read(it) },
        save = { key, value, current -> DaylightHomeStorage.save(key, value) {
            current() && key.identity == identity() && key.sourceRevision == Innertube.responseCacheScope
        } }, title = { it.title }, browseIds = { shelf -> shelf.items.mapNotNullTo(HashSet()) { it.browseId } },
        itemCount = { it.items.size }, isRecent = { it.title.equals("Recents", true) || it.title.equals("Recently played", true) })
    val state get() = repository.state
    fun load(signedIn: Boolean, force: Boolean = false) = repository.load(HomeContext(identity(), Innertube.currentLanguage, Innertube.responseCacheScope), signedIn, force)
    fun loadMore() = repository.loadMore()
    fun clear() = repository.clear()
    fun rename(id: String, title: String) = repository.edit { shelf ->
        shelf.copy(items = shelf.items.map { if (it.browseId == id) it.copy(title = title) else it })
    }
    fun remove(id: String) = repository.edit { shelf -> shelf.copy(items = shelf.items.filterNot { it.browseId == id }) }
}
