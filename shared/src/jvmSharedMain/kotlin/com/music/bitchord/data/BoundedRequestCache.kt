package com.music.bitchord.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * Short-lived successful responses, with one producer for concurrent readers.
 *
 * The entry and weight limits both matter: a page holding hundreds of songs
 * should not cost the same memory budget as a page holding one. Failures are
 * never retained. The last reader abandoning a request cancels its producer,
 * while one reader leaving cannot cancel work another reader still needs.
 */
class BoundedRequestCache<K, V>(
    private val scope: CoroutineScope,
    private val ttlMs: Long,
    private val maxEntries: Int,
    private val maxWeight: Int,
    private val weightOf: (V) -> Int,
    private val now: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private class Entry<V>(val value: V, val expiresAt: Long, val weight: Int)
    private class Pending<V>(val work: Deferred<V>, var readers: Int = 0)

    private val lock = Any()
    private val entries = LinkedHashMap<K, Entry<V>>(16, .75f, true)
    private val pending = mutableMapOf<K, Pending<V>>()
    private var heldWeight = 0

    init {
        require(ttlMs >= 0 && maxEntries > 0 && maxWeight > 0)
    }

    suspend fun get(key: K, produce: suspend () -> V): V {
        val request = synchronized(lock) {
            entries[key]?.let { entry ->
                if (now() < entry.expiresAt) return entry.value
                remove(key)
            }
            pending[key]?.also { it.readers++ } ?: run {
                lateinit var created: Pending<V>
                val work = scope.async(start = CoroutineStart.LAZY) {
                    val value = produce()
                    synchronized(lock) {
                        // An edit/refresh may have detached this request. Its
                        // current reader can finish, but its old answer cannot
                        // repopulate the cache or replace a newer request.
                        if (pending[key] === created) {
                            pending.remove(key)
                            retain(key, value)
                        }
                    }
                    value
                }
                created = Pending(work, readers = 1)
                pending[key] = created
                created
            }
        }
        request.work.start()
        try {
            return request.work.await()
        } finally {
            synchronized(lock) {
                request.readers--
                if (request.readers == 0) {
                    if (pending[key] === request) pending.remove(key)
                    if (request.work.isActive) request.work.cancel()
                }
            }
        }
    }

    /** New readers fetch fresh; an older in-flight answer cannot be retained. */
    fun clear() = synchronized(lock) {
        entries.clear()
        heldWeight = 0
        pending.clear()
    }

    private fun retain(key: K, value: V) {
        val weight = weightOf(value).coerceAtLeast(1)
        if (ttlMs == 0L || weight > maxWeight) return
        val at = now()
        val expired = entries.entries.filter { it.value.expiresAt <= at }.map { it.key }
        expired.forEach(::remove)
        remove(key)
        entries[key] = Entry(value, at + ttlMs, weight)
        heldWeight += weight
        while (entries.size > maxEntries || heldWeight > maxWeight) {
            remove(entries.keys.first())
        }
    }

    private fun remove(key: K) {
        entries.remove(key)?.let { heldWeight -= it.weight }
    }
}
