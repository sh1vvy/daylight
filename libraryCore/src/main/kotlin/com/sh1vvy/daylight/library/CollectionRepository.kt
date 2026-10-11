/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.library

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class CollectionBatch<H, T>(val header: H?, val rows: List<T>, val next: String?, val suggestions: List<T> = emptyList())
data class CollectionListing<H, T>(val header: H?, val rows: List<T>, val suggestions: List<T> = emptyList(), val complete: Boolean, val checkedAt: Long)
data class CollectionLoad<H, T>(val listing: CollectionListing<H, T>? = null, val loading: Boolean = false, val failure: Throwable? = null)

/** Repository-owned paging, shared by visits. Provider/UI objects enter only through adapters. */
class CollectionRepository<K, H, T>(
    private val scope: CoroutineScope,
    private val first: suspend (K) -> CollectionBatch<H, T>,
    private val next: suspend (K, String) -> CollectionBatch<H, T>,
    private val read: suspend (K) -> CollectionListing<H, T>?,
    private val save: suspend (K, CollectionListing<H, T>, () -> Boolean) -> Unit,
    private val rowKey: (T) -> String,
    private val recordingKey: (T) -> String,
    private val now: () -> Long = System::currentTimeMillis,
    private val freshnessMs: Long = 60_000,
    private val maxWarmEntries: Int = 6,
    private val maxWarmRows: Int = 8_000,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private inner class Entry {
        val revision = AtomicLong()
        val state = MutableStateFlow(CollectionLoad<H, T>())
        var job: Job? = null
    }
    private val lock = Any()
    private val entries = LinkedHashMap<K, Entry>(8, .75f, true)

    fun open(key: K, force: Boolean = false): StateFlow<CollectionLoad<H, T>> = synchronized(lock) {
        val entry = entries.getOrPut(key) { Entry() }
        val saved = entry.state.value.listing
        if (force || (entry.job?.isActive != true && (saved == null || !saved.complete || now() - saved.checkedAt !in 0 until freshnessMs))) {
            val revision = entry.revision.incrementAndGet()
            entry.job?.cancel()
            entry.state.value = entry.state.value.copy(loading = true, failure = null)
            entry.job = scope.launch(io, start = CoroutineStart.LAZY) { load(key, entry, revision, force) }.also { it.start() }
        }
        trim(key)
        entry.state
    }

    /** A closed screen stops unnecessary paging; completed listings remain bounded and reusable. */
    fun release(key: K) = synchronized(lock) {
        entries[key]?.let { entry ->
            if (entry.state.subscriptionCount.value == 0 && entry.job?.isActive == true) {
                entry.revision.incrementAndGet(); entry.job?.cancel()
                entry.state.value = entry.state.value.copy(loading = false)
            }
        }
        trim(null)
    }

    fun edit(matches: (K) -> Boolean, transform: (CollectionListing<H, T>) -> CollectionListing<H, T>): Set<K> = synchronized(lock) {
        val matched = entries.filter { matches(it.key) && it.value.state.value.listing != null }
        matched.forEach { (key, entry) ->
            val previous = entry.state.value.listing ?: return@forEach
            val transformed = transform(previous)
            if (transformed == previous) return@forEach
            val value = transformed.copy(checkedAt = now())
            val revision = entry.revision.incrementAndGet()
            entry.job?.cancel()
            entry.state.value = CollectionLoad(value)
            scope.launch(io) { save(key, value) { entry.revision.get() == revision } }
        }
        matched.keys.toSet()
    }

    fun invalidate(matches: (K) -> Boolean) = synchronized(lock) {
        entries.entries.filter { matches(it.key) }.forEach { (key, entry) ->
            entry.revision.incrementAndGet(); entry.job?.cancel(); entries.remove(key)
        }
    }

    fun clear() = synchronized(lock) {
        entries.values.forEach { it.revision.incrementAndGet(); it.job?.cancel() }
        entries.clear()
    }

    private suspend fun load(key: K, entry: Entry, revision: Long, force: Boolean) {
        val owner = currentCoroutineContext()
        fun current() = owner.isActive && entry.revision.get() == revision
        fun publish(value: CollectionLoad<H, T>) = synchronized(lock) { if (current()) entry.state.value = value }
        try {
            val previous = entry.state.value.listing ?: read(key)
            if (!current()) return
            if (previous != null) publish(CollectionLoad(previous, loading = true))
            if (!force && previous?.complete == true && now() - previous.checkedAt in 0 until freshnessMs) return
            val started = now()
            var batch = first(key)
            if (!current()) return
            val header = batch.header ?: previous?.header
            val rows = batch.rows.toMutableList()
            val occurrences = rows.mapTo(HashSet(), rowKey)
            val recordings = rows.mapTo(HashSet(), recordingKey)
            val suggestions = ArrayList<T>()
            val suggested = HashSet<String>()
            fun addSuggestions(items: List<T>) {
                items.forEach { if (recordingKey(it) !in recordings && suggested.add(recordingKey(it))) suggestions += it }
            }
            addSuggestions(batch.suggestions)
            fun listing(complete: Boolean) = CollectionListing(header, rows.toList(), suggestions.filter { recordingKey(it) !in recordings }, complete, started)
            if (previous?.complete != true || batch.next == null) {
                val value = listing(batch.next == null)
                publish(CollectionLoad(value, loading = true))
                if (batch.next != null) save(key, value, ::current)
            }
            val tokens = HashSet<String>()
            while (batch.next != null) {
                val token = requireNotNull(batch.next)
                check(tokens.add(token)) { "Collection pagination repeated a token" }
                yield()
                batch = next(key, token)
                if (!current()) return
                batch.rows.forEach { item -> if (occurrences.add(rowKey(item))) { rows += item; recordings += recordingKey(item) } }
                addSuggestions(batch.suggestions)
                // A refresh keeps a complete cached list visible until its replacement is complete.
                if (previous?.complete != true) publish(CollectionLoad(listing(batch.next == null), loading = true))
            }
            if (!current()) return
            val complete = listing(true)
            save(key, complete, ::current)
            publish(CollectionLoad(complete))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { synchronized(lock) { if (current()) entry.state.value = entry.state.value.copy(failure = failure) } }
        finally {
            synchronized(lock) {
                if (entry.revision.get() == revision) entry.state.value = entry.state.value.copy(loading = false)
                trim(key)
            }
        }
    }

    private fun trim(protected: K?) {
        while (entries.size > maxWarmEntries || entries.values.sumOf { it.state.value.listing?.rows?.size ?: 0 } > maxWarmRows) {
            val victim = entries.entries.firstOrNull { it.key != protected && it.value.state.subscriptionCount.value == 0 } ?: break
            victim.value.revision.incrementAndGet(); victim.value.job?.cancel(); entries.remove(victim.key)
        }
    }
}
