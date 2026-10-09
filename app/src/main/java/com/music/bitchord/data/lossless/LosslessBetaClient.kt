package com.music.bitchord.data.lossless

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

/** The extra source is contacted only after an explicit opt-in. */
data class LosslessBetaOptions(
    val enabled: Boolean = false,
    val serverUrl: String = "",
    val maxSampleRateHz: Int = 192_000,
    val maxBitDepth: Int = 24,
)

data class LosslessRecording(
    val title: String,
    val artists: List<String>,
    val durationMs: Long,
    val isExplicit: Boolean? = null,
    val isrc: String? = null,
    val isVideo: Boolean = false,
)

data class VerifiedLosslessStream(
    val url: String,
    val recordingId: String,
    val info: FlacStreamInfo,
    /** STREAMINFO includes the audio MD5, so different files never share partial bytes. */
    val fingerprint: String = "",
)

enum class LosslessFallbackReason {
    INELIGIBLE, MISSING_METADATA, NO_MATCH, NOT_FLAC, UNAVAILABLE, TIMED_OUT;
}

data class LosslessBetaResult(
    val stream: VerifiedLosslessStream? = null,
    val fallback: LosslessFallbackReason? = null,
)

/**
 * Isolated Monochrome-compatible HTTP connector for an explicitly supplied endpoint.
 * There are no public host lists, background jobs, credentials, or playback mutations here.
 * A miss (including an AAC response advertised as lossless) retains YouTube.
 */
class LosslessBetaClient(
    client: OkHttpClient = OkHttpClient(),
    private val budgetMs: Long = 5_000,
) {
    private val http = client.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(4, TimeUnit.SECONDS)
        // A supplied endpoint must answer for itself; don't follow a downgrade/login redirect.
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private data class Lookup(val endpoint: String, val recording: LosslessRecording, val rate: Int, val depth: Int)
    private data class Memo(val result: LosslessBetaResult, val expiresAtNanos: Long)
    private val memo = LinkedHashMap<Lookup, Memo>(32, .75f, true)
    private class Gate(val mutex: Mutex = Mutex(), var users: Int = 0)
    private val locks = HashMap<Lookup, Gate>()

    suspend fun resolve(options: LosslessBetaOptions, wanted: LosslessRecording): VerifiedLosslessStream? =
        resolveDetailed(options, wanted).stream

    suspend fun resolveDetailed(options: LosslessBetaOptions, wanted: LosslessRecording): LosslessBetaResult {
        if (!options.enabled || wanted.isVideo) return LosslessBetaResult(fallback = LosslessFallbackReason.INELIGIBLE)
        if (wanted.title.isBlank() ||
            wanted.artists.none { it.isNotBlank() } || wanted.durationMs <= 0
        ) return LosslessBetaResult(fallback = LosslessFallbackReason.MISSING_METADATA)
        val base = endpoint(options.serverUrl) ?: return LosslessBetaResult(fallback = LosslessFallbackReason.UNAVAILABLE)
        val key = Lookup(base.toString(), wanted, options.maxSampleRateHz, options.maxBitDepth)
        fun cached(): LosslessBetaResult? = synchronized(memo) {
            memo[key]?.takeIf { it.expiresAtNanos > System.nanoTime() }?.result
        }
        cached()?.let { return it }
        // The queue warmer and a tap on the same recording share one lookup.
        val gate = synchronized(locks) { locks.getOrPut(key) { Gate() }.also { it.users++ } }
        try {
        return withTimeoutOrNull(budgetMs) { gate.mutex.withLock {
            cached()?.let { return@withLock it }
            val result = withTimeoutOrNull(budgetMs) {
                try {
                    resolveFrom(base, wanted, options)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: IOException) {
                    LosslessBetaResult(fallback = LosslessFallbackReason.UNAVAILABLE)
                } catch (_: RuntimeException) {
                    LosslessBetaResult(fallback = LosslessFallbackReason.UNAVAILABLE)
                }
            } ?: LosslessBetaResult(fallback = LosslessFallbackReason.TIMED_OUT)
            val ttlMs = when {
                result.stream != null -> 10 * 60_000L
                result.fallback == LosslessFallbackReason.NO_MATCH || result.fallback == LosslessFallbackReason.NOT_FLAC -> 30_000L
                else -> 0L // A network failure must never hide a recovered server.
            }
            if (ttlMs > 0) synchronized(memo) {
                memo[key] = Memo(result, System.nanoTime() + ttlMs * 1_000_000)
                while (memo.size > MAX_MEMOS) memo.remove(memo.keys.first())
            }
            result
        } } ?: LosslessBetaResult(fallback = LosslessFallbackReason.TIMED_OUT)
        } finally {
            synchronized(locks) { if (--gate.users == 0) locks.remove(key, gate) }
        }
    }

    private suspend fun resolveFrom(base: HttpUrl, wanted: LosslessRecording, options: LosslessBetaOptions): LosslessBetaResult {
        val searchUrl = base.newBuilder().addPathSegments("search/tracks")
            .addQueryParameter("q", "${wanted.title} ${wanted.artists.first { it.isNotBlank() }}")
            .addQueryParameter("limit", "20").build()
        val body = bytes(searchUrl, MAX_SEARCH_BYTES, prefixOnly = false)
        val root = json.parseToJsonElement(body.decodeToString()) as? JsonObject
            ?: return LosslessBetaResult(fallback = LosslessFallbackReason.UNAVAILABLE)
        val rows = (root["tracks"] as? JsonArray).orEmpty().take(20)
        val candidates = rows.mapNotNull { it as? JsonObject }.filter { matches(it, wanted) }
        val expectedIsrc = wanted.isrc?.takeIf { it.isNotBlank() }
        val selected = candidates.sortedByDescending {
            expectedIsrc != null && expectedIsrc.equals(it.text("isrc"), ignoreCase = true)
        }.distinctBy { it.text("id") }.take(3)
        if (selected.isEmpty()) return LosslessBetaResult(fallback = LosslessFallbackReason.NO_MATCH)
        var failure = LosslessFallbackReason.NO_MATCH
        for (candidate in selected) {
            val id = candidate.text("id")?.takeIf { it.matches(Regex("[0-9]{1,30}")) } ?: continue
            val url = base.newBuilder().addPathSegment("track").addPathSegment(id).build()
            val header = try {
                bytes(url, FlacStreamInfo.HEADER_BYTES, prefixOnly = true)
            } catch (_: IOException) {
                failure = LosslessFallbackReason.UNAVAILABLE
                continue
            }
            val info = FlacStreamInfo.read(header)
            if (info == null) { failure = LosslessFallbackReason.NOT_FLAC; continue }
            if (abs(info.durationMs - wanted.durationMs) > MAX_DURATION_DELTA_MS) continue
            if (info.sampleRateHz > options.maxSampleRateHz || info.bitDepth > options.maxBitDepth) continue
            val fingerprint = MessageDigest.getInstance("SHA-256").digest(header)
                .joinToString("") { "%02x".format(it) }
            return LosslessBetaResult(stream = VerifiedLosslessStream(url.toString(), id, info, fingerprint))
        }
        return LosslessBetaResult(fallback = failure)
    }

    private fun matches(row: JsonObject, wanted: LosslessRecording): Boolean {
        if (row["playable"]?.jsonPrimitive?.booleanOrNull != true) return false
        val duration = row["duration"]?.jsonPrimitive?.longOrNull ?: return false
        if (duration <= 0 || abs(duration - wanted.durationMs) > MAX_DURATION_DELTA_MS) return false
        if (wanted.isExplicit != null && row["explicit"]?.jsonPrimitive?.booleanOrNull != wanted.isExplicit) return false
        val expectedIsrc = wanted.isrc?.takeIf { it.isNotBlank() }
        val gotIsrc = row.text("isrc")?.takeIf { it.isNotBlank() }
        if (expectedIsrc != null && gotIsrc != null) return expectedIsrc.equals(gotIsrc, ignoreCase = true)
        if (normalize(wanted.title) != normalize(row.text("title").orEmpty())) return false
        val artists = (row["artistNames"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }
        val fullCredit = normalize(artists.joinToString(", "))
        return wanted.artists.filter { normalize(it).isNotBlank() }.any { name ->
            normalize(name) == fullCredit || artists.any { normalize(name) == normalize(it) }
        }
    }

    private suspend fun bytes(url: HttpUrl, limit: Int, prefixOnly: Boolean): ByteArray =
        suspendCancellableCoroutine { continuation ->
            val request = Request.Builder().url(url).header("User-Agent", "Daylight-Lossless-Beta/1")
            if (prefixOnly) request.header("Range", "bytes=0-${limit - 1}")
            val call = http.newCall(request.build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val value = response.use {
                            if (it.code != 200 && it.code != 206) throw IOException("Lossless endpoint unavailable")
                            if (prefixOnly && it.code == 206 &&
                                !it.header("Content-Range").orEmpty().startsWith("bytes 0-")
                            ) throw IOException("Unexpected audio range")
                            val source = it.body.source()
                            if (prefixOnly) {
                                source.readByteArray(limit.toLong())
                            } else {
                                if (it.body.contentLength() > limit || source.request(limit.toLong() + 1)) {
                                    throw IOException("Lossless response too large")
                                }
                                source.readByteArray()
                            }
                        }
                        if (continuation.isActive) continuation.resume(value)
                    } catch (failure: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(failure)
                    } finally {
                        // Also stop an origin that ignored Range instead of downloading the song.
                        if (prefixOnly) call.cancel()
                    }
                }
            })
        }

    private fun endpoint(raw: String): HttpUrl? {
        val url = raw.trim().toHttpUrlOrNull() ?: return null
        val loopback = url.host in setOf("localhost", "127.0.0.1", "::1")
        if ((url.scheme != "https" && !loopback) || url.username.isNotEmpty() ||
            url.password.isNotEmpty() || url.query != null || url.fragment != null
        ) return null
        return url.newBuilder().encodedPath(url.encodedPath.trimEnd('/') + "/").build()
    }

    private fun JsonObject.text(key: String) = this[key]?.jsonPrimitive?.contentOrNull
    private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .lowercase(Locale.ROOT).replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    companion object {
        private const val MAX_MEMOS = 128
        private const val MAX_SEARCH_BYTES = 256 * 1024
        private const val MAX_DURATION_DELTA_MS = 2_000
    }
}
