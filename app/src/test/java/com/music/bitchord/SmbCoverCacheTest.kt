package com.music.bitchord

import android.content.ContextWrapper
import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.fetch.SourceFetchResult
import coil3.request.CachePolicy
import coil3.request.Options
import com.music.bitchord.data.smb.SmbCoverFetcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SmbCoverCacheTest {
    @get:Rule val directory = TemporaryFolder()

    @Test
    fun reopenedCoverUsesEncodedDiskBytesWithoutReopeningTheShare() = runBlocking {
        val cache = DiskCache.Builder().directory(directory.newFolder()).maxSizeBytes(1024 * 1024).build()
        try {
            val options = Options(context = ContextWrapper(null), diskCacheKey = "private-cover")
            val bytes = byteArrayOf(1, 2, 3, 4)
            var reads = 0
            val fetcher = SmbCoverFetcher("smb://host/Music/cover.jpg", options, cache) { reads++; bytes }
            val first = fetcher.fetch() as SourceFetchResult
            assertEquals(DataSource.NETWORK, first.dataSource)
            assertArrayEquals(bytes, first.source.use { it.source().readByteArray() })
            val second = fetcher.fetch() as SourceFetchResult
            assertEquals(DataSource.DISK, second.dataSource)
            assertArrayEquals(bytes, second.source.use { it.source().readByteArray() })
            assertEquals(1, reads)
        } finally { cache.shutdown() }
    }

    @Test
    fun simultaneousArtAndPaletteRequestsShareOneReadEvenWithoutDiskCache() = runBlocking {
        val options = Options(context = ContextWrapper(null), diskCacheKey = "concurrent-cover")
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val bytes = byteArrayOf(5, 6, 7)
        var reads = 0
        val fetcher = SmbCoverFetcher("smb://host/Music/cover.png", options, null) {
            reads++
            entered.complete(Unit)
            release.await()
            bytes
        }
        val first = async { fetcher.fetch() as SourceFetchResult }
        entered.await()
        // Enter the second fetch immediately so it joins the pending read
        // before the first reader is released, independent of IO scheduling.
        val second = async(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) { fetcher.fetch() as SourceFetchResult }
        release.complete(Unit)
        assertArrayEquals(bytes, first.await().source.use { it.source().readByteArray() })
        assertArrayEquals(bytes, second.await().source.use { it.source().readByteArray() })
        assertEquals(1, reads)
    }

    @Test
    fun disabledDiskWritesDoNotRetainTheCover() = runBlocking {
        val cache = DiskCache.Builder().directory(directory.newFolder()).maxSizeBytes(1024 * 1024).build()
        try {
            val options = Options(context = ContextWrapper(null), diskCacheKey = "uncached-cover", diskCachePolicy = CachePolicy.DISABLED)
            var reads = 0
            val fetcher = SmbCoverFetcher("smb://host/Music/cover.webp", options, cache) { reads++; byteArrayOf(1) }
            repeat(2) { (fetcher.fetch() as SourceFetchResult).source.close() }
            assertEquals(2, reads)
        } finally { cache.shutdown() }
    }

    @Test
    fun cacheOnlyMissDoesNotJoinAnActiveNetworkRead() = runBlocking {
        val options = Options(context = ContextWrapper(null), diskCacheKey = "network-policy-cover")
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val live = SmbCoverFetcher("smb://host/Music/cover.jpg", options, null) {
            entered.complete(Unit)
            release.await()
            byteArrayOf(1)
        }
        val first = async { live.fetch() as SourceFetchResult }
        entered.await()
        try {
            val offline = SmbCoverFetcher("smb://host/Music/cover.jpg", options.copy(networkCachePolicy = CachePolicy.DISABLED), null) {
                error("cache-only requests must not read the share")
            }
            assertTrue(runCatching { offline.fetch() }.exceptionOrNull() is java.io.IOException)
        } finally { release.complete(Unit) }
        first.await().source.close()
    }

    @Test
    fun cacheWriterJoiningReadOnlyProducerStillRetainsTheEncodedCover() = runBlocking {
        val cache = DiskCache.Builder().directory(directory.newFolder()).maxSizeBytes(1024 * 1024).build()
        try {
            val options = Options(context = ContextWrapper(null), diskCacheKey = "mixed-write-policy-cover")
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var reads = 0
            val readOnly = SmbCoverFetcher("smb://host/Music/cover.jpg", options.copy(diskCachePolicy = CachePolicy.READ_ONLY), cache) {
                reads++
                entered.complete(Unit)
                release.await()
                byteArrayOf(1, 2)
            }
            val writer = SmbCoverFetcher("smb://host/Music/cover.jpg", options, cache) { reads++; byteArrayOf(1, 2) }
            val first = async { readOnly.fetch() as SourceFetchResult }
            entered.await()
            val second = async(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) { writer.fetch() as SourceFetchResult }
            release.complete(Unit)
            first.await().source.close()
            second.await().source.close()
            val reopened = writer.fetch() as SourceFetchResult
            assertEquals(DataSource.DISK, reopened.dataSource)
            reopened.source.close()
            assertEquals(1, reads)
        } finally { cache.shutdown() }
    }
}
