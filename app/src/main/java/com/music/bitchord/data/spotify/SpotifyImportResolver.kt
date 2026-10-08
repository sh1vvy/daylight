package com.music.bitchord.data.spotify

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** Four search workers, even for a large import; repeated entries share one lookup. */
internal suspend fun resolveSpotifyImportTracks(
    tracks: List<SpotifyImportTrack>,
    onProgress: suspend (completed: Int, total: Int) -> Unit,
    resolve: suspend (SpotifyImportTrack) -> Song?,
): Pair<List<Song>, List<SpotifyImportTrack>> = coroutineScope {
    val groups = tracks.withIndex().groupBy {
        it.value.title.trim().lowercase(Locale.ROOT) to it.value.artist.trim().lowercase(Locale.ROOT)
    }.values.toList()
    val results = arrayOfNulls<Song>(tracks.size)
    val nextGroup = AtomicInteger(0)
    val progressLock = Mutex()
    var completed = 0

    List(minOf(4, groups.size)) {
        async(Dispatchers.IO) {
            while (true) {
                val group = groups.getOrNull(nextGroup.getAndIncrement()) ?: break
                val match = resolve(group.first().value)
                group.forEach { results[it.index] = match }
                // Serialize callbacks too, so a slower UI update cannot move progress backwards.
                progressLock.withLock {
                    completed += group.size
                    onProgress(completed, tracks.size)
                }
            }
        }
    }.awaitAll()

    results.filterNotNull() to tracks.filterIndexed { index, _ -> results[index] == null }
}
