package com.music.bitchord.data.smb

import coil3.ImageLoader
import coil3.decode.ImageSource
import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.music.bitchord.ui.graphics.CoalescingLruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.FileSystem
import java.io.IOException

/**
 * Covers off the share for Coil. Reads the whole picture — covers are
 * kilobytes, and listings already cap what can be one — then hands Coil
 * bytes it decodes and caches exactly like any network image.
 *
 * Registered in [BitChordApplication][com.music.bitchord.BitChordApplication]
 * alongside the defaults: anything that isn't `smb://` falls straight
 * through to them.
 */
class SmbCoverFetcher internal constructor(
    private val url: String,
    private val options: Options,
    private val diskCache: DiskCache?,
    private val readCover: suspend (String) -> ByteArray = { SmbClient.readFully(it) },
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val key = options.diskCacheKey ?: url
        val mime = when (url.substringBefore('?').substringBefore('#').substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> null
        }
        readSnapshot(key)?.let { return@withContext it.toResult(mime, DataSource.DISK, key) }
        // A cache-only caller must never join someone else's network read.
        if (!options.networkCachePolicy.readEnabled) throw IOException("SMB cover is not cached")

        val bytes = downloads.getOrLoad("$key|disk-read=${options.diskCachePolicy.readEnabled}") {
            // A different size's request may have filled disk while this one
            // was joining the pending download. No second share read is needed.
            readSnapshot(key)?.use { snapshot ->
                return@getOrLoad fileSystem.read(snapshot.data) { readByteArray() }
            }
            readCover(url).also { writeToDisk(key, it) }
        } ?: throw IOException("SMB cover could not be read")
        // A writer may have joined a read-only producer; its own cache policy
        // still decides whether these bytes are retained.
        writeToDisk(key, bytes)
        readSnapshot(key)?.let { return@withContext it.toResult(mime, DataSource.NETWORK, key) }
        SourceFetchResult(
            source = ImageSource(Buffer().write(bytes), FileSystem.SYSTEM),
            mimeType = mime,
            dataSource = DataSource.NETWORK,
        )
    }

    private val fileSystem: FileSystem get() = diskCache?.fileSystem ?: options.fileSystem

    private fun readSnapshot(key: String): DiskCache.Snapshot? =
        if (options.diskCachePolicy.readEnabled) diskCache?.openSnapshot(key) else null

    private fun writeToDisk(key: String, bytes: ByteArray) {
        if (!options.diskCachePolicy.writeEnabled) return
        if (options.diskCachePolicy.readEnabled) {
            diskCache?.openSnapshot(key)?.use { return }
        }
        val editor = diskCache?.openEditor(key) ?: return
        try {
            fileSystem.write(editor.metadata) { }
            fileSystem.write(editor.data) { write(bytes) }
            editor.commit()
        } catch (_: IOException) {
            // A full cache directory should still let the cover display.
            editor.abort()
        }
    }

    private fun DiskCache.Snapshot.toResult(mime: String?, source: DataSource, key: String) =
        SourceFetchResult(
            source = ImageSource(data, fileSystem, diskCacheKey = key, closeable = this),
            mimeType = mime,
            dataSource = source,
        )

    class Factory : Fetcher.Factory<String> {
        override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (SmbConfig.isSmbUrl(data)) SmbCoverFetcher(data, options, imageLoader.diskCache) else null
    }

    companion object {
        // Encoded bytes live only until all simultaneous cover/palette readers
        // receive them; Coil's bounded disk and bitmap caches retain the result.
        private val downloads = CoalescingLruCache<ByteArray>(maxEntries = 0)
    }
}
