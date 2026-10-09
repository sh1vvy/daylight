package com.music.bitchord.playback

import com.music.bitchord.data.lossless.LosslessBetaClient
import com.music.bitchord.data.lossless.LosslessBetaOptions
import com.music.bitchord.data.lossless.LosslessRecording
import com.music.bitchord.data.lossless.LosslessBetaResult
import com.music.bitchord.data.lossless.LosslessFallbackReason
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.sources.SourceStream
import com.music.bitchord.data.sources.StreamFormat
import com.music.bitchord.data.sources.TrackMatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Beta requests bypass the audio disk cache and keep one rendition for every reopen/seek. */
internal class LosslessPlayback(
    private val beta: LosslessBetaClient = LosslessBetaClient(),
    private val endpoint: String = COMMUNITY_ENDPOINT,
) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private data class Choice(val stream: SourceStream, val status: NerdStats.LosslessBetaStatus)
    private val choices = LinkedHashMap<String, Choice>()

    suspend fun resolve(
        key: String,
        target: TrackMatcher.Target,
        enabled: Boolean,
        metered: Boolean,
        inParty: Boolean,
        stillEligible: () -> Boolean = { eligible(enabled, metered, inParty, target.isVideo) },
        durationSeconds: suspend () -> Int? = { null },
        onStatus: (NerdStats.LosslessBetaStatus) -> Unit = {},
        youtube: suspend () -> SourceStream,
    ): SourceStream = locks.computeIfAbsent(key) { Mutex() }.withLock {
        synchronized(choices) { choices[key] }?.let { onStatus(it.status); return@withLock it.stream }
        val result = if (eligible(enabled, metered, inParty, target.isVideo)) {
            onStatus(NerdStats.LosslessBetaStatus.CHECKING)
            val duration = target.durationSec?.takeIf { it > 0 } ?: try {
                durationSeconds()?.takeIf { it > 0 }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { null }
            beta.resolveDetailed(
                LosslessBetaOptions(stillEligible(), endpoint),
                LosslessRecording(target.title, listOf(target.artist),
                    (duration ?: 0) * 1_000L, target.isExplicit, isVideo = target.isVideo),
            )
        } else LosslessBetaResult(fallback = LosslessFallbackReason.INELIGIBLE)
        val verified = result.stream?.takeIf { stillEligible() }
        val stream = verified?.let {
            SourceStream(
                url = it.url,
                format = StreamFormat(codec = "flac", sampleRateHz = it.info.sampleRateHz, bitDepth = it.info.bitDepth),
                headers = mapOf("User-Agent" to "Daylight-Lossless-Beta/1"),
                durationSec = (it.info.durationMs / 1_000).toInt(),
                sourceConfigId = SOURCE_ID,
            )
        } ?: youtube()
        val status = if (verified != null) NerdStats.LosslessBetaStatus.VERIFIED
            else NerdStats.LosslessBetaStatus.valueOf((result.fallback ?: LosslessFallbackReason.INELIGIBLE).name)
        onStatus(status)
        // Only opened media items enter this map; queued tracks do not cause lookups.
        synchronized(choices) {
            if (choices.size >= 256) {
                val oldest = choices.keys.first()
                choices.remove(oldest)
                locks.remove(oldest)
            }
            choices[key] = Choice(stream, status)
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
