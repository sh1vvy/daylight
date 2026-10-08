package com.music.bitchord.data.lyrics

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.Normalizer
import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/** The existing PaxSenix Apple catalogue lookup, shared with recording identification. */
internal object AppleLyricsRecording {
    private const val SEARCH = "https://amp-api.music.apple.com/v1/catalog/us/search"
    private const val CACHE_SIZE = 100
    private const val CACHE_TTL_MS = 10 * 60_000L
    private const val IDENTIFY_TIMEOUT_MS = 1_200L
    private val tokenMutex = Mutex()
    private val token = AtomicReference<String?>(null)
    private data class Cached(val atMs: Long, val response: JsonElement)
    private val searches = Collections.synchronizedMap(
        object : LinkedHashMap<Pair<String, String>, Cached>(CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<Pair<String, String>, Cached>) =
                size > CACHE_SIZE
        },
    )
    private val queryLocks = Collections.synchronizedMap(
        object : LinkedHashMap<Pair<String, String>, Mutex>(CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<Pair<String, String>, Mutex>) =
                size > CACHE_SIZE
        },
    )

    suspend fun search(title: String, artist: String): JsonElement? = cachedCatalogue(title, artist) {
        val bearer = token.get() ?: tokenMutex.withLock {
            token.get() ?: scrapeToken()?.also(token::set)
        } ?: return@cachedCatalogue null
        val url = SEARCH.toHttpUrl().newBuilder()
            .addQueryParameter("term", "$title $artist")
            .addQueryParameter("types", "songs")
            .addQueryParameter("limit", "10")
            .addQueryParameter("l", "en-US")
            .build()
        lyricsGetCatalogue(url.toString(), bearer)
            ?.let { runCatching { lyricsJson.parseToJsonElement(it) }.getOrNull() }
    }

    /** Concurrent player/notification lookups should not issue duplicate catalogue requests. */
    internal suspend fun cachedCatalogue(
        title: String,
        artist: String,
        fetch: suspend () -> JsonElement?,
    ): JsonElement? {
        val key = title to artist
        cached(key)?.let { return it }
        val lock = synchronized(queryLocks) { queryLocks.getOrPut(key) { Mutex() } }
        return lock.withLock {
            cached(key)?.let { return@withLock it }
            val root = fetch() ?: return@withLock null
            // An upstream error encoded as JSON must not poison ordinary PaxSenix
            // requests for the lifetime of the catalogue cache.
            if (songResponse(root) != null) searches[key] = Cached(System.currentTimeMillis(), root)
            root
        }
    }

    private fun cached(key: Pair<String, String>): JsonElement? = searches[key]
        ?.takeIf { System.currentTimeMillis() - it.atMs in 0 until CACHE_TTL_MS }
        ?.response

    private suspend fun scrapeToken(): String? {
        val page = lyricsGetCatalogue("https://music.apple.com/us/new") ?: return null
        val path = INDEX_SCRIPT.find(page)?.value ?: return null
        val script = lyricsGetCatalogue("https://music.apple.com$path") ?: return null
        return TOKEN.find(script)?.value
    }

    data class Identification(val hit: BiniLyrics.Hit, val verifiedExplicit: Boolean)

    /** Failure, ambiguous metadata and the optional request deadline keep the original hit. */
    suspend fun identify(
        hits: List<BiniLyrics.Hit>,
        title: String,
        artist: String,
        durationMs: Long,
        album: String?,
        refinementEnabled: Boolean,
        searchCatalogue: suspend (String, String) -> JsonElement? = ::search,
    ): Identification? {
        val original = hits.firstOrNull() ?: return null
        if (!refinementEnabled || title.isBlank() || artist.isBlank() || durationMs <= 0L) {
            return Identification(original, false)
        }
        val explicit = try {
            withTimeoutOrNull(IDENTIFY_TIMEOUT_MS) {
                val apple = searchCatalogue(title, artist) ?: return@withTimeoutOrNull null
                preferredExplicitHit(hits, apple, title, artist, durationMs, album)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        return Identification(explicit ?: original, explicit != null)
    }

    /**
     * Prefer a published, timed document for an explicitly marked recording.
     * Both catalogues must agree on the title, artist, duration and ISRC, and
     * on the album when known. The document URL itself must name that ISRC.
     * A rating alone says nothing about lyrics censorship; this identifies a
     * recording and keeps its actual words and timestamps without rewriting.
     */
    fun preferredExplicitHit(
        hits: List<BiniLyrics.Hit>,
        apple: JsonElement,
        title: String,
        artist: String,
        durationMs: Long,
        album: String?,
    ): BiniLyrics.Hit? {
        if (title.isBlank() || artist.isBlank() || durationMs <= 0L) return null
        val explicitIsrcs = songs(apple).mapNotNull { song ->
            val attributes = (song as? JsonObject)?.get("attributes") as? JsonObject
                ?: return@mapNotNull null
            if (attributes.text("contentRating") != "explicit" ||
                runCatching { attributes["hasLyrics"]?.jsonPrimitive?.booleanOrNull }.getOrNull() != true ||
                !same(attributes.text("name"), title) ||
                !same(attributes.text("artistName"), artist) ||
                (!album.isNullOrBlank() && !same(attributes.text("albumName"), album))
            ) return@mapNotNull null
            val duration = runCatching { attributes["durationInMillis"]?.jsonPrimitive?.longOrNull }
                .getOrNull()?.takeIf { it > 0L } ?: return@mapNotNull null
            if (abs(duration - durationMs) > 2_000L) return@mapNotNull null
            attributes.text("isrc")?.canonicalIsrc()
        }.toSet()
        return hits.firstOrNull { hit ->
            val isrc = hit.isrc?.canonicalIsrc() ?: return@firstOrNull false
            isrc in explicitIsrcs &&
                same(hit.trackName, title) && same(hit.artistName, artist) &&
                (album.isNullOrBlank() || same(hit.albumName, album)) &&
                hit.duration?.takeIf { it > 0 }?.let {
                    abs(it * 1_000L - durationMs) <= 2_000L
                } == true &&
                hit.timingType in setOf("word", "line") &&
                documentNamesRecording(hit.lyricsUrl, isrc)
        }
    }

    /** The identifier for an already verified recording, rather than a fuzzy first song. */
    fun songIdForRecording(apple: JsonElement, isrc: String): String? {
        val wanted = isrc.canonicalIsrc() ?: return null
        return songs(apple).firstNotNullOfOrNull { song ->
            val entry = song as? JsonObject ?: return@firstNotNullOfOrNull null
            val attributes = entry["attributes"] as? JsonObject ?: return@firstNotNullOfOrNull null
            if (attributes.text("isrc")?.canonicalIsrc() != wanted) return@firstNotNullOfOrNull null
            entry.text("id")?.takeIf(String::isNotBlank)
        }
    }

    private fun songResponse(apple: JsonElement) = runCatching {
        (apple as? JsonObject)?.get("results")?.let { it as? JsonObject }
            ?.get("songs")?.let { it as? JsonObject }?.get("data")?.jsonArray
    }.getOrNull()

    private fun songs(apple: JsonElement) = songResponse(apple).orEmpty()

    private fun documentNamesRecording(rawUrl: String?, isrc: String): Boolean {
        val url = rawUrl?.toHttpUrlOrNull() ?: return false
        if (!url.isHttps || url.query != null || url.fragment != null) return false
        return when (url.host) {
            "lrc.red" -> url.encodedPath == "/s/$isrc.ttml"
            "lyrics-storage.binimum.org" -> url.encodedPath == "/$isrc.ttml"
            else -> false
        }
    }

    private fun String.canonicalIsrc(): String? = trim().uppercase(Locale.ROOT)
        .takeIf { LrcRed.documentUrl(it) != null }

    private fun JsonObject.text(key: String): String? =
        runCatching { get(key)?.jsonPrimitive?.contentOrNull }.getOrNull()

    private fun same(first: String?, second: String): Boolean =
        first != null && normalized(first) == normalized(second)

    private fun normalized(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)
        .lowercase(Locale.ROOT).trim().replace(WHITESPACE, " ")

    private val WHITESPACE = Regex("""\s+""")
    private val INDEX_SCRIPT = Regex("""/assets/index~[^\"]+\.js""")
    private val TOKEN = Regex("""eyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")
}
