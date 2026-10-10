package com.music.bitchord.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.SystemClock
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.LocalMediaRepository
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.isUnresolvedSpotify
import com.music.bitchord.data.spotify.LocalPlaylistStore
import com.music.bitchord.download.Downloads
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shared by Home and the service: one small pool, no library scan on every track change. */
object DaylightMixRepository {
    private val lock = Any()
    private val warmMutex = Mutex()
    private var scope = -1L
    private var pool = emptyList<Song>()
    private var playlistIds = emptyList<String>()
    private var playlistOffset = 0
    private var warmedAt = 0L
    private var revision = 0L

    fun clear() = synchronized(lock) {
        revision++; scope = -1L; pool = emptyList(); playlistIds = emptyList(); playlistOffset = 0; warmedAt = 0L
    }

    fun prime(songs: List<Song>, playlists: List<String>? = null) = synchronized(lock) {
        ensureScope()
        pool = boundedPool(songs + pool)
        playlists?.let {
            playlistIds = it.distinct().take(32)
            playlistOffset = if (playlistIds.isEmpty()) 0 else playlistOffset % playlistIds.size
        }
    }

    fun snapshot(): List<Song> = synchronized(lock) {
        ensureScope()
        (LocalPlaylistStore.playlists.value.asSequence().flatMap { it.songs.asSequence() }.filterNot { it.isUnresolvedSpotify }.take(100).toList() + pool)
            .filter { !it.isUnresolvedSpotify && LikeState.overrides.value[it.videoId] != LikeStatus.DISLIKE }
            .distinctBy { it.videoId }.take(600)
    }

    fun remove(videoId: String) = synchronized(lock) { pool = pool.filterNot { it.videoId == videoId } }

    fun networkAvailable(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    /** Offline sessions must not bury playable files behind unavailable streams. */
    suspend fun playable(context: Context, songs: List<Song>): List<Song> = withContext(Dispatchers.IO) {
        if (networkAvailable(context)) return@withContext songs
        songs.mapNotNull { song ->
            val uri = song.localUri ?: Downloads.verifiedSavedUri(song.videoId)
            when {
                uri != null && !Downloads.isMissingLocalFile(uri) -> song.copy(localUri = uri)
                AudioCache.isFullyCached(Uri.parse("bitchord://watch?v=${Uri.encode(song.videoId)}")) -> song
                else -> null
            }
        }
    }

    private fun boundedPool(songs: List<Song>): List<Song> {
        val playable = songs.filterNot { it.isUnresolvedSpotify }.distinctBy { it.videoId }
        return playable.filter { it.localUri == null }.take(400) + playable.filter { it.localUri != null }.take(100)
    }

    private fun ensureScope() {
        val current = Innertube.responseCacheScope
        if (scope != current) {
            scope = current; revision++; pool = emptyList(); playlistIds = emptyList(); playlistOffset = 0; warmedAt = 0L
        }
    }

    suspend fun warm(context: Context): List<Song> = warmMutex.withLock {
        val request = synchronized(lock) {
            ensureScope()
            if (warmedAt != 0L && SystemClock.elapsedRealtime() - warmedAt < 300_000L) return@withLock snapshot()
            revision to scope
        }
        val saved = ArrayList<Song>()
        // All disk/media scanning stays on IO and is performed at most once per five minutes.
        withContext(Dispatchers.IO) {
            if (LocalMediaRepository.hasStoragePermission(context)) {
                try { saved += LocalMediaRepository.getLocalMusic(context).shuffled().take(200) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* An unavailable media provider must not stop a streaming mix. */ }
            }
        }
        val online = networkAvailable(context)
        if (online && snapshot().isEmpty()) {
            val library = withTimeoutOrNull(3_000L) { YtMusicRepository.library().getOrNull() }
            library?.let {
                saved += it.likedSongs + it.librarySongs
                synchronized(lock) {
                    if (request == (revision to scope)) playlistIds = it.shelves.flatMap { shelf -> shelf.items }
                        .mapNotNull { item -> item.browseId?.takeIf { id -> id.startsWith("VL") && id != "VLLM" } }.distinct().take(32)
                }
            }
        }
        val ids = synchronized(lock) {
            val selected = (playlistIds.drop(playlistOffset) + playlistIds.take(playlistOffset)).take(2)
            playlistOffset = if (playlistIds.isEmpty()) 0 else (playlistOffset + selected.size) % playlistIds.size
            selected
        }
        for (id in ids.takeIf { online }.orEmpty()) {
            saved += withTimeoutOrNull(2_000L) { YtMusicRepository.browseSongs(id).getOrNull()?.songs }.orEmpty().shuffled().take(100)
        }
        synchronized(lock) {
            ensureScope()
            if (request != (revision to scope)) return@withLock emptyList()
            pool = boundedPool(saved + pool)
            warmedAt = SystemClock.elapsedRealtime()
        }
        snapshot()
    }
}
