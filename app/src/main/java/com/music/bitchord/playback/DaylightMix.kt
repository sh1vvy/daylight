package com.music.bitchord.playback

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.isUnresolvedSpotify
import com.music.bitchord.playback.QueueCoordinator.asQueueEntry

const val DAYLIGHT_MIX_PREFIX = "daylight:mix:"
fun Song.isDaylightMix(): Boolean = playbackSourceId?.startsWith(DAYLIGHT_MIX_PREFIX) == true

/** Two familiar tracks, then a discovery; no repeated recordings in the listening window. */
internal fun daylightMixBatch(
    familiar: List<Song>, discoveries: List<Song>, recent: List<Song>, upcoming: List<Song>,
    limit: Int, source: Song,
): List<Song> {
    val blocked = recent + upcoming
    fun eligible(songs: List<Song>) = songs.asSequence()
        .filter { !it.isUnresolvedSpotify && it.videoId.isNotBlank() && !com.music.bitchord.data.LikeState.isDisliked(it.videoId) }
        .distinctBy { it.videoId }.filter { song -> blocked.none { QueueBuilder.isSameRecording(it, song) } }.toList()
    var saved = eligible(familiar)
    // After a small library has completed a lap, recycle it without repeating the current or queued song.
    if (saved.isEmpty()) saved = familiar.filter { song ->
        !song.isUnresolvedSpotify && song.videoId.isNotBlank() && !com.music.bitchord.data.LikeState.isDisliked(song.videoId) &&
            (recent.takeLast(1) + upcoming).none { QueueBuilder.isSameRecording(it, song) }
    }.distinctBy { it.videoId }
    val fresh = eligible(discoveries.filterNot { it.isVideo }).filter { song -> familiar.none { QueueBuilder.isSameRecording(it, song) } }
    val out = ArrayList<Song>()
    var savedAt = 0
    var freshAt = 0
    while (out.size < limit.coerceIn(0, 20) && (savedAt < saved.size || freshAt < fresh.size)) {
        val next = if (out.size % 3 != 2 && savedAt < saved.size || freshAt >= fresh.size) saved[savedAt++] else fresh[freshAt++]
        if (out.none { QueueBuilder.isSameRecording(it, next) }) out += next
    }
    return out.map { it.copy(
        radioName = null, playbackSource = source.playbackSource,
        playbackSourceType = PlaybackSourceType.HOME, playbackSourceId = source.playbackSourceId,
    ).asQueueEntry(QueueTier.AUTOPLAY) }
}
