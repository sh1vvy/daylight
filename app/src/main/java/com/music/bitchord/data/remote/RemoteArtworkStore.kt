package com.music.bitchord.data.remote

import android.content.Context
import android.net.Uri
import com.music.bitchord.data.model.Song
import java.io.File
import java.security.MessageDigest
import com.music.bitchord.ui.graphics.CoalescingLruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Embedded covers for remote tracks, extracted lazily and cached.
 *
 * Listings never touch audio bytes, so a track whose art lives inside the
 * file arrives with no thumbnail. The first surface that wants to draw it —
 * a row, an album card, the player — asks here instead: memory first, then
 * a disk cache of extracted pictures, and only then the bounded ranged
 * reads that pull the picture out of the file. Concurrent asks for one
 * track share a single extraction.
 *
 * Disk names are content-blind hashes of the track id and server account:
 * ids embed full URLs
 * with slashes and escapes that must never reach the filesystem. A
 * server-side overwrite keeps serving the stale copy — accepted, since
 * revalidating would cost a request per row per list.
 */
object RemoteArtworkStore {

    private const val MAX_DIR_BYTES = 100L * 1024 * 1024

    private data class Resolved(val uri: String?, val checkedAt: Long)
    private val memory = CoalescingLruCache<Resolved>(256)
    private const val MISS_RETRY_NANOS = 30_000_000_000L

    /**
     * A drawable art URI for [song]: its own thumbnail when it has one, else
     * the extracted embedded picture, else null. Never throws.
     */
    suspend fun resolveArt(context: Context, song: Song): String? = withContext(Dispatchers.IO) {
        song.thumbnailUrl?.let { return@withContext it }
        val url = song.localUri.orEmpty()
        val scopedUrl = ImageCacheKeys.forUrl(url)
        val key = if (scopedUrl == url) song.videoId else song.videoId + scopedUrl.removePrefix(url)
        val now = System.nanoTime()
        memory[key]?.let { cached ->
            if (cached.uri == null && now - cached.checkedAt < MISS_RETRY_NANOS) return@withContext null
            if (cached.uri != null && Uri.parse(cached.uri).path?.let { File(it).isFile } == true) {
                return@withContext cached.uri
            }
            // Android or our bounded disk cache can remove a file underneath us.
            memory.remove(key)
        }
        val appContext = context.applicationContext
        memory.getOrLoad(key) {
            try {
                Resolved(diskHit(appContext, key) ?: extractAndStore(appContext, song, key), System.nanoTime())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Resolved(null, System.nanoTime())
            }
        }?.uri
    }

    private suspend fun extractAndStore(context: Context, song: Song, key: String): String? =
        withContext(Dispatchers.IO) {
            val url = song.localUri?.takeIf { it.startsWith("http") || it.startsWith("smb://") }
                ?: return@withContext null
            val picture = RemoteArtReader.picture(url, url.startsWith("smb://"))
                ?: return@withContext null
            val dir = File(context.cacheDir, "remote_art").apply { mkdirs() }
            val file = File(dir, "${cacheKey(key)}.${extensionFor(picture.mime)}")
            runCatching {
                file.outputStream().use { it.write(picture.bytes) }
                evict(dir)
            }.getOrNull() ?: return@withContext null
            Uri.fromFile(file).toString()
        }

    private fun diskHit(context: Context, key: String): String? {
        val dir = File(context.cacheDir, "remote_art")
        if (!dir.isDirectory) return null
        // Six predictable file names avoid scanning the entire cover directory
        // for each visible row. All filesystem work is on the IO dispatcher.
        val stem = cacheKey(key)
        val match = CACHE_EXTENSIONS.map { File(dir, "$stem.$it") }
            .filter { it.isFile && it.length() > 0 }
            .maxByOrNull { it.lastModified() }
        return match?.let { Uri.fromFile(it).toString() }
    }

    private val CACHE_EXTENSIONS = listOf("jpg", "png", "webp", "gif", "bmp", "bin")

    private fun extensionFor(mime: String): String = when (mime.lowercase()) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/bmp" -> "bmp"
        else -> "bin"
    }

    private fun cacheKey(videoId: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        return digest.digest(videoId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun evict(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_DIR_BYTES) return
        files.sortedBy { it.lastModified() }.forEach {
            if (total <= MAX_DIR_BYTES) return
            total -= it.length()
            it.delete()
        }
    }
}
