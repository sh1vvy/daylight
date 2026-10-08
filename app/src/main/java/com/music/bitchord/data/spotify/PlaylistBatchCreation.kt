package com.music.bitchord.data.spotify

import kotlinx.coroutines.CancellationException

internal data class PlaylistBatchCreation(
    val playlistId: String,
    val addedCount: Int,
    val totalCount: Int,
) {
    val complete: Boolean get() = addedCount == totalCount
}

/** Preserve source order and stop at the first failed edit instead of claiming a full import. */
internal suspend fun createPlaylistInBatches(
    videoIds: List<String>,
    create: suspend (List<String>) -> Result<String>,
    append: suspend (String, List<String>) -> Result<Unit>,
): Result<PlaylistBatchCreation> {
    val first = videoIds.take(50)
    val created = create(first)
    created.exceptionOrNull()?.let { error ->
        if (error is CancellationException) throw error
        return Result.failure(error)
    }
    val playlistId = created.getOrThrow()
    var added = first.size
    // Only allocate each next batch as it is needed; no coroutine per song or batch.
    while (added < videoIds.size) {
        val chunk = videoIds.subList(added, minOf(added + 50, videoIds.size))
        val result = append(playlistId, chunk)
        result.exceptionOrNull()?.let { error ->
            if (error is CancellationException) throw error
            return Result.success(PlaylistBatchCreation(playlistId, added, videoIds.size))
        }
        added += chunk.size
    }
    return Result.success(PlaylistBatchCreation(playlistId, added, videoIds.size))
}
