package com.music.bitchord.data.lyrics

import com.music.bitchord.data.innertube.Innertube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** Lyrics exposed by YouTube Music's Lyrics tab for the exact playing video. */
object YouTubeMusicLyrics {
    suspend fun lyrics(videoId: String): List<LyricLine>? = withContext(Dispatchers.IO) {
        if (!YOUTUBE_ID.matches(videoId)) return@withContext null
        val next = youtubeRequest { Innertube.next(videoId) } ?: return@withContext null
        val endpoint = next.objectsNamed("tabRenderer")
            .firstOrNull { it.youtubeStrings().any { text -> text.equals("Lyrics", ignoreCase = true) } }
            ?.objectsNamed("browseEndpoint")?.firstOrNull()
            ?: next.objectsNamed("tabRenderer").drop(1).firstNotNullOfOrNull {
                it.objectsNamed("browseEndpoint").firstOrNull()
            }
            ?: return@withContext null
        val browseId = (endpoint["browseId"] as? JsonPrimitive)?.contentOrNull
            ?: return@withContext null
        val params = (endpoint["params"] as? JsonPrimitive)?.contentOrNull
        val page = youtubeRequest { Innertube.browse(browseId, params) } ?: return@withContext null
        val shelf = page.objectsNamed("musicDescriptionShelfRenderer").firstOrNull()
            ?: return@withContext null
        val text = shelf["description"]?.youtubeStrings()?.joinToString("").orEmpty().trim()
        text.lineSequence().map(String::trim).filter(String::isNotEmpty)
            .map { LyricLine(0L, it) }.toList().takeIf { it.isNotEmpty() }
    }
}

/** Timed YouTube transcript/captions for the exact playing video. */
object YouTubeTranscriptLyrics {
    suspend fun lyrics(videoId: String): List<LyricLine>? = withContext(Dispatchers.IO) {
        if (!YOUTUBE_ID.matches(videoId)) return@withContext null
        val response = youtubeRequest { Innertube.transcript(videoId) }
            ?: return@withContext null
        response.objectsNamed("transcriptCueRenderer").mapNotNull { cue ->
            val start = (cue["startOffsetMs"] as? JsonPrimitive)?.longOrNull
                ?: return@mapNotNull null
            val text = cue["cue"]?.youtubeStrings()?.joinToString("").orEmpty()
                .trim(' ', '\n', '♪')
            text.takeIf { it.isNotEmpty() }?.let { LyricLine(start, it) }
        }.sortedBy { it.timeMs }.toList().takeIf { it.isNotEmpty() }
    }
}

private val YOUTUBE_ID = Regex("""[A-Za-z0-9_-]{11}""")

private suspend fun <T> youtubeRequest(block: suspend () -> T): T? = try {
    block().also { LyricsRequestHealth.current.get()?.response(200) }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    LyricsRequestHealth.current.get()?.failure()
    null
}

private fun JsonElement.objectsNamed(name: String): Sequence<JsonObject> = sequence {
    when (this@objectsNamed) {
        is JsonObject -> for ((key, value) in this@objectsNamed) {
            if (key == name && value is JsonObject) yield(value)
            yieldAll(value.objectsNamed(name))
        }
        is JsonArray -> for (value in this@objectsNamed) yieldAll(value.objectsNamed(name))
        else -> Unit
    }
}

fun JsonElement.youtubeStrings(): List<String> = when (this) {
    is JsonPrimitive -> contentOrNull?.let(::listOf).orEmpty()
    is JsonArray -> flatMap { it.youtubeStrings() }
    is JsonObject -> {
        val direct = (this["text"] as? JsonPrimitive)?.contentOrNull
            ?: (this["simpleText"] as? JsonPrimitive)?.contentOrNull
        direct?.let(::listOf) ?: values.flatMap { it.youtubeStrings() }
    }
}
