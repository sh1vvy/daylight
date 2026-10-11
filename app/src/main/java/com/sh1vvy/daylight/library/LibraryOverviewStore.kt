/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.library

import android.content.Context
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.ShelfItem
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Adapter for the current UI. The core knows nothing about BitChord's models or providers. */
object LibraryOverviewStore {
    private val json = Json { ignoreUnknownKeys = true }
    private var cache: SnapshotCache<LibraryOverview>? = null

    fun init(context: Context) {
        if (cache == null) cache = SnapshotCache(File(context.filesDir, "daylight-library-overview-v1"), object : SnapshotCodec<LibraryOverview> {
            override fun encode(value: LibraryOverview) = json.encodeToString(LibraryOverview.serializer(), value).toByteArray(Charsets.UTF_8)
            override fun decode(bytes: ByteArray) = json.decodeFromString(LibraryOverview.serializer(), bytes.toString(Charsets.UTF_8))
            override fun weight(value: LibraryOverview) = value.sections.sumOf { it.cards.size }
        }, CacheBudget(diskBytes = 4L * 1024 * 1024, entryBytes = 1024 * 1024, memoryRows = 4_000, memoryEntries = 4, entriesPerAccount = 3))
    }

    private fun key(identity: String?, language: String) = identity?.let { SnapshotKey("youtube:$it", "overview:$language") }
    suspend fun read(identity: String?, language: String): LibraryOverview? = try {
        key(identity, language)?.let { cache?.read(it) }
    } catch (_: IOException) { null }

    suspend fun save(identity: String?, language: String, page: LibraryPage, verifiedAt: Long, isCurrent: () -> Boolean = { true }) {
        val key = key(identity, language) ?: return
        withContext(Dispatchers.IO) {
            val overview = LibraryOverview(page.shelves.map { shelf -> LibrarySection(
                shelf.title, shelf.items.map { card -> LibraryCard(card.title, card.subtitle, card.thumbnailUrl, card.browseId, card.videoId) },
                shelf.subtitle, shelf.moreBrowseId, shelf.moreParams,
            ) }, verifiedAt)
            try { cache?.write(key, overview, isCurrent) } catch (_: IOException) { /* Retain live browsing on an unavailable disk. */ }
        }
    }

    suspend fun remove(identity: String) {
        try { cache?.removeAccount("youtube:$identity") } catch (_: IOException) { /* Retried when the account is removed again. */ }
    }

    /** The full liked list lives in its separate collection record, never duplicated here. */
    fun page(overview: LibraryOverview) = LibraryPage(emptyList(), emptyList(), overview.sections.map { section -> HomeShelf(
        section.label, section.cards.map { card -> ShelfItem(card.label, card.byline, card.artwork, card.recordingId, card.collectionId) },
        section.caption, section.destination, section.destinationParameters,
    ) }, likedLoaded = false)

    fun retainingFailedSections(incoming: LibraryPage, previous: LibraryPage?): LibraryPage {
        val verified = incoming.loadedShelfTitles ?: return incoming
        if (previous == null) return incoming
        if (previous.shelves.none { it.title !in verified }) return incoming
        fun sections(page: LibraryPage) = page.shelves.map { shelf -> LibrarySection(shelf.title,
            shelf.items.map { LibraryCard(it.title, it.subtitle, it.thumbnailUrl, it.browseId, it.videoId) },
            shelf.subtitle, shelf.moreBrowseId, shelf.moreParams) }
        val merged = mergeLibrarySections(sections(previous), sections(incoming), verified)
        return incoming.copy(shelves = page(LibraryOverview(merged, 0)).shelves)
    }

    fun isFresh(overview: LibraryOverview, now: Long = System.currentTimeMillis()) = now - overview.verifiedAt in 0 until 60_000L
}
