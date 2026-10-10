package com.music.bitchord.data.lyrics

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** A bounded, offline check of original lyrics, shared by every player layout. */
internal object LyricsLanguage {
    private val identifier by lazy {
        LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.8f).build(),
        )
    }
    private data class Result(val language: String?)
    private val cache = object : LinkedHashMap<String, Result>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Result>?) = size > 32
    }
    private val mutex = Mutex()

    suspend fun identify(lines: List<LyricLine>): String? = withContext(Dispatchers.Default) {
        val text = lines.asSequence()
            .flatMap { sequenceOf(it.text, it.background?.text.orEmpty()) }
            .map(String::trim)
            .filter { it.any(Char::isLetter) && !(it.startsWith("[") && it.endsWith("]")) }
            .distinct().take(80).joinToString("\n").take(3_000)
        if (text.isBlank()) return@withContext null
        mutex.withLock {
            cache[text]?.let { return@withLock it.language }
            var language = identifyText(text)
            // Do not hide translation for English verses with a non-Latin passage.
            if (language == "en" && lines.needsRomanization()) language = null
            // Check separate passages of longer lyrics rather than only the dominant language.
            if (language != null && text.length > 900) {
                val middle = (text.length - 600) / 2
                val passages = listOf(text.take(600), text.substring(middle, middle + 600), text.takeLast(600))
                for (passage in passages) {
                    if (identifyText(passage) != language) {
                        language = null
                        break
                    }
                }
            }
            cache[text] = Result(language)
            language
        }
    }

    private suspend fun identifyText(text: String): String? = suspendCancellableCoroutine { continuation ->
        identifier.identifyLanguage(text)
            .addOnSuccessListener { language ->
                if (continuation.isActive) continuation.resume(language.takeUnless { it == "und" })
            }
            .addOnFailureListener {
                if (continuation.isActive) continuation.resume(null)
            }
    }
}
