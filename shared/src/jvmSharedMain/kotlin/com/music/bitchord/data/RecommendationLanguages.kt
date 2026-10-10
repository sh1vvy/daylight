package com.music.bitchord.data

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.isRecentHomeShelf
import java.util.Locale

data class RecommendationLanguage(val code: String, val name: String, val nativeName: String)

/** Language labels, not artist nationality or script guesses. Unknown recordings remain available. */
object RecommendationLanguages {
    val catalog: List<RecommendationLanguage> = Locale.getISOLanguages().map { code ->
        val locale = Locale.forLanguageTag(code)
        RecommendationLanguage(code, locale.getDisplayLanguage(Locale.ENGLISH), locale.getDisplayLanguage(locale))
    }.distinctBy { it.code }.sortedBy { it.name }
    val codes: Set<String> = catalog.mapTo(HashSet()) { it.code }
    fun canonical(codes: Set<String>): Set<String> = codes.map { Locale.forLanguageTag(it).language }
        .filterTo(LinkedHashSet()) { code -> code in this.codes }

    private val labelCodes = buildMap<String, Set<String>> {
        catalog.forEach { language ->
            (listOf(language.name, language.nativeName) + when (language.code) {
            "hi" -> listOf("हिंदी", "हिन्दी")
            else -> emptyList()
            }).forEach { label ->
                val key = label.lowercase(Locale.ROOT)
                put(key, get(key).orEmpty() + language.code)
            }
        }
    }
    private val labelPattern = Regex(
        "(?<![\\p{L}\\p{M}\\p{N}])(?:" + labelCodes.keys.sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) } + ")(?![\\p{L}\\p{M}\\p{N}])",
        RegexOption.IGNORE_CASE,
    )
    private fun identifiedCodes(text: String): Set<String> = labelPattern.findAll(text)
        .flatMap { labelCodes[it.value.lowercase(Locale.ROOT)].orEmpty().asSequence() }.toSet()

    fun identifiedIn(text: String, excluded: Set<String>): Boolean = identifiedCodes(text).any { it in excluded }
    private val annotations = Regex("[（(\\[]([^）)\\]]+)[）)\\]]")
    private fun labelledRecording(text: String, excluded: Set<String>): Boolean =
        annotations.findAll(text).any { identifiedIn(it.groupValues[1], excluded) } ||
            labelPattern.findAll(text).any { match ->
                labelCodes[match.value.lowercase(Locale.ROOT)].orEmpty().any { it in excluded } && (
                    text.substring(match.range.last + 1).trimStart().startsWith("version", true) ||
                        text.substring(match.range.last + 1).trimStart().startsWith("lyrics", true))
            }
    fun allows(item: ShelfItem, excluded: Set<String>): Boolean =
        excluded.isEmpty() || (RecommendationLanguageHints.language(item.videoId) !in excluded &&
            !labelledRecording(item.title, excluded))
    fun allows(song: Song, excluded: Set<String>): Boolean =
        // The artist's name is never interpreted as a language label.
        excluded.isEmpty() || (RecommendationLanguageHints.language(song.videoId) !in excluded &&
            !labelledRecording("${song.title} ${song.albumName.orEmpty()}", excluded))
    fun filter(shelves: List<HomeShelf>, excluded: Set<String>): List<HomeShelf> {
        // Learn explicit shelf language labels without requesting any track metadata.
        shelves.filterNot { it.isRecentHomeShelf() }.forEach { shelf ->
            val found = identifiedCodes(shelf.title)
            if (found.size == 1) shelf.items.forEach { item -> item.videoId?.let { RecommendationLanguageHints.record(it, found.single()) } }
        }
        if (excluded.isEmpty()) return shelves
        return shelves.mapNotNull { shelf ->
            if (shelf.isRecentHomeShelf()) shelf
            else if (identifiedIn("${shelf.title} ${shelf.subtitle.orEmpty()}", excluded)) null
            else shelf.copy(items = shelf.items.filter { allows(it, excluded) }).takeIf { it.items.isNotEmpty() }
        }
    }
}


/** Bounded session hints. A listener/profile switch discards the previous listener's metadata. */
object RecommendationLanguageHints {
    private var scope = Long.MIN_VALUE
    private val hints = LinkedHashMap<String, String>(16, .75f, true)
    private fun currentScope() = com.music.bitchord.data.innertube.Innertube.responseCacheScope
    private fun resetForScope() { if (scope != currentScope()) { scope = currentScope(); hints.clear() } }
    @Synchronized fun record(trackId: String, language: String, requestScope: Long = currentScope()) {
        resetForScope()
        if (requestScope != scope || trackId.isBlank()) return
        val canonical = Locale.forLanguageTag(language).language
        if (canonical !in RecommendationLanguages.codes) return
        hints[trackId] = canonical
        while (hints.size > 1_024) hints.remove(hints.keys.first())
    }
    @Synchronized fun language(trackId: String?): String? { resetForScope(); return hints[trackId] }
}
