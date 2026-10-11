/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.library

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.normalizedArtistName
import java.io.File
import java.io.IOException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Public portrait URLs only, with bounded storage and cancellable visible-row requests. */
class LibraryArtistPortraits(
    directory: File,
    private val fetch: suspend (ShelfItem) -> Result<String?> = { YtMusicRepository.libraryArtistPortrait(it.browseId, it.title) },
    private val language: () -> String = { Innertube.currentLanguage },
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Serializable private data class Portrait(val url: String?, val checkedAt: Long, val freshFor: Long)
    private val json = Json { ignoreUnknownKeys = true }
    private val gate = Semaphore(2)
    private val cache = SnapshotCache(directory, object : SnapshotCodec<Portrait> {
        override fun encode(value: Portrait) = json.encodeToString(Portrait.serializer(), value).toByteArray()
        override fun decode(bytes: ByteArray) = json.decodeFromString(Portrait.serializer(), bytes.toString(Charsets.UTF_8))
        override fun weight(value: Portrait) = 1
    }, CacheBudget(diskBytes = 1024 * 1024, entryBytes = 16 * 1024, memoryRows = 192,
        memoryEntries = 192, entriesPerAccount = 384, diskEntries = 384))
    private fun key(item: ShelfItem) = SnapshotKey("public-artists:${language()}",
        item.browseId?.takeIf { it.startsWith("UC") } ?: "name:${normalizedArtistName(item.title)}")

    suspend fun artworkFor(item: ShelfItem): String? {
        item.thumbnailUrl?.takeIf(String::isNotBlank)?.let { return it }
        val key = key(item)
        val saved = try { cache.read(key) } catch (_: IOException) { null }
        fun fresh(value: Portrait?) = value?.let { now() - it.checkedAt in 0 until it.freshFor } == true
        if (fresh(saved)) return saved?.url
        return gate.withPermit {
            val latest = cache.peek(key) ?: saved
            if (fresh(latest)) return@withPermit latest?.url
            val result = withTimeoutOrNull(5_000) { fetch(item) }
            val url = result?.getOrNull()?.takeIf(String::isNotBlank) ?: latest?.url
            val freshFor = when {
                result?.isSuccess != true -> 60_000L
                url == null -> 3_600_000L
                else -> 7 * 24 * 60 * 60_000L
            }
            try { cache.write(key, Portrait(url, now(), freshFor)) } catch (_: IOException) { /* Live artwork stays usable. */ }
            url
        }
    }
}
