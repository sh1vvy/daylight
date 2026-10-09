package com.music.bitchord.playback

import com.music.bitchord.data.lossless.LosslessBetaClient
import com.music.bitchord.data.lossless.LosslessBetaOptions
import com.music.bitchord.data.lossless.LosslessRecording
import com.music.bitchord.data.sources.SourceStream
import com.music.bitchord.data.sources.StreamFormat
import com.music.bitchord.data.sources.TrackMatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Beta requests bypass the audio disk cache and keep one rendition for every reopen/seek. */
internal class LosslessPlayback(
    private val beta: LosslessBetaClient = LosslessBetaClient(),
    private val endpoint: String = COMMUNITY_ENDPOINT,
) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val choices = LinkedHashMap<String, SourceStream>()

    suspend fun resolve(
        key: String,
        target: TrackMatcher.Target,
        enabled: Boolean,
        metered: Boolean,
        inParty: Boolean,
        stillEligible: () -> Boolean = { eligible(enabled, metered, inParty, target.isVideo) },
        youtube: suspend () -> SourceStream,
    ): SourceStream = locks.computeIfAbsent(key) { Mutex() }.withLock {
        synchronized(choices) { choices[key] }?.let { return@withLock it }
        val verified = if (eligible(enabled, metered, inParty, target.isVideo)) {
            beta.resolve(
                LosslessBetaOptions(true, endpoint),
                LosslessRecording(target.title, listOf(target.artist),
                    (target.durationSec ?: 0) * 1_000L, target.isExplicit, isVideo = target.isVideo),
            )
        } else null
        val stream = verified?.takeIf { stillEligible() }?.let {
            SourceStream(
                url = it.url,
                format = StreamFormat(codec = "flac", sampleRateHz = it.info.sampleRateHz, bitDepth = it.info.bitDepth),
                headers = mapOf("User-Agent" to "Daylight-Lossless-Beta/1"),
                durationSec = (it.info.durationMs / 1_000).toInt(),
                sourceConfigId = SOURCE_ID,
            )
        } ?: youtube()
        // Only opened media items enter this map; queued tracks do not cause lookups.
        synchronized(choices) {
            if (choices.size >= 256) {
                val oldest = choices.keys.first()
                choices.remove(oldest)
                locks.remove(oldest)
            }
            choices[key] = stream
        }
        stream
    }

    companion object {
        const val PARAMETER = "lossless_beta"
        const val SOURCE_ID = "daylight-lossless-beta"
        const val COMMUNITY_ENDPOINT = "https://tracks.monochrome.st"

        fun eligible(enabled: Boolean, metered: Boolean, inParty: Boolean, isVideo: Boolean): Boolean =
            enabled && !metered && !inParty && !isVideo

        fun tag(uri: String): String = "$uri&$PARAMETER=${UUID.randomUUID()}"
        fun isTagged(uri: String): Boolean = uri.substringAfter('?', "").substringBefore('#')
            .split('&').any { it.substringBefore('=') == PARAMETER && it.substringAfter('=', "").isNotBlank() }
    }
}
