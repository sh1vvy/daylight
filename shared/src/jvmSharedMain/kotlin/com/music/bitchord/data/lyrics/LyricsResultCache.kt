package com.music.bitchord.data.lyrics

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Positive results only: a timeout or missing lyric never becomes a lasting negative cache. */
internal class LyricsResultCache(
    private val maxEntries: Int = 48,
    private val maxWeight: Int = 2 * 1024 * 1024,
) {
    data class Key(
        val videoId: String,
        val title: String,
        val artist: String,
        val durationSeconds: Long,
        val album: String?,
        val explicit: Boolean?,
        val source: LyricsSource,
    )
    private data class Entry(val result: LyricsRepository.Result, val weight: Int)
    private val entries = LinkedHashMap<Key, Entry>(16, 0.75f, true)
    private var weight = 0
    private var window: Set<String>? = null
    private class Gate(val mutex: Mutex = Mutex(), var users: Int = 0)
    private val inFlight = mutableMapOf<Key, Gate>()

    /** A service, UI and queue warmer share a successful fetch, including across a cancelled waiter. */
    suspend fun getOrFetch(key: Key, fetch: suspend () -> LyricsRepository.Result?): LyricsRepository.Result? {
        get(key)?.let { return it }
        val gate = synchronized(this) { inFlight.getOrPut(key) { Gate() }.also { it.users += 1 } }
        return try {
            gate.mutex.withLock { get(key) ?: fetch().also { put(key, it) } }
        } finally {
            synchronized(this) {
                gate.users -= 1
                if (gate.users == 0) inFlight.remove(key)
            }
        }
    }

    @Synchronized fun get(key: Key): LyricsRepository.Result? = entries[key]?.result

    @Synchronized fun put(key: Key, result: LyricsRepository.Result?) {
        if (result == null || result.lines.none { it.text.isNotBlank() }) return
        if (window?.contains(key.videoId) == false) return
        val entryWeight = result.lines.sumOf(::lineWeight)
        if (entryWeight > maxWeight) return
        entries.remove(key)?.let { weight -= it.weight }
        entries[key] = Entry(result, entryWeight)
        weight += entryWeight
        while (entries.size > maxEntries || weight > maxWeight) {
            val oldest = entries.entries.iterator()
            weight -= oldest.next().value.weight
            oldest.remove()
        }
    }

    @Synchronized fun retain(videoIds: Set<String>) {
        window = videoIds.toSet()
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key.videoId !in videoIds) {
                weight -= entry.value.weight
                iterator.remove()
            }
        }
    }

    @Synchronized fun size(): Int = entries.size
    @Synchronized fun weight(): Int = weight

    private fun lineWeight(line: LyricLine): Int =
        96 + line.text.length * 2 + line.words.sumOf { 64 + it.text.length * 2 + it.syllables.size * 24 } +
            (line.background?.let(::lineWeight) ?: 0)
}
