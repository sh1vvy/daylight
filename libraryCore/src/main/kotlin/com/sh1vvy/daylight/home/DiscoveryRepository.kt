/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.home

import com.sh1vvy.daylight.library.RefreshRequests
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class DiscoverySeed(val id: String, val artist: String)

/** A small, profile-scoped discovery pool. It never scans the full library or changes playback. */
class DiscoveryRepository<K, T>(
    scope: CoroutineScope,
    private val fetch: suspend (K, String) -> List<T>,
    private val id: (T) -> String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private data class Request<K>(val context: K, val seed: String)
    private data class Cached<T>(val rows: List<T>, val checkedAt: Long)
    private val requests = RefreshRequests<Request<K>, List<T>>(scope)
    private val lock = Any()
    private val warm = LinkedHashMap<Request<K>, Cached<T>>(16, .75f, true)
    private val gate = Semaphore(2)
    private val revision = AtomicLong()

    suspend fun discover(context: K, seeds: List<DiscoverySeed>, heard: Set<String>, limit: Int = 24): List<T> = supervisorScope {
        require(limit >= 0)
        if (limit == 0) return@supervisorScope emptyList()
        val version = revision.get()
        val selected = selectSeeds(seeds)
        val pools = selected.map { seed -> async {
            val key = Request(context, seed.id)
            val cached = synchronized(lock) { warm[key]?.takeIf { now() - it.checkedAt in 0 until 600_000 } }
            cached?.rows ?: requests.run(key) {
                gate.withPermit {
                    withTimeout(4_000) { fetch(context, seed.id) }.take(32).also { rows ->
                        synchronized(lock) {
                            if (revision.get() == version) {
                                warm[key] = Cached(rows, now())
                                while (warm.size > 24) warm.remove(warm.keys.first())
                            }
                        }
                    }
                }
            }
        } }.map { job -> try { job.await() } catch (cancelled: TimeoutCancellationException) { emptyList() }
            catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { emptyList() } }
        val used = heard.toMutableSet()
        val mixed = ArrayList<T>(limit)
        val positions = IntArray(pools.size)
        while (mixed.size < limit) {
            var advanced = false
            pools.forEachIndexed { index, pool ->
                while (positions[index] < pool.size) {
                    val track = pool[positions[index]++]
                    advanced = true
                    if (used.add(id(track))) { if (mixed.size < limit) mixed += track; break }
                }
            }
            if (!advanced) break
        }
        mixed
    }

    fun clear() { revision.incrementAndGet(); requests.cancelAll(); synchronized(lock) { warm.clear() } }

    companion object {
        fun selectSeeds(seeds: List<DiscoverySeed>): List<DiscoverySeed> {
            val ids = HashSet<String>()
            val candidates = seeds.filter { it.id.isNotBlank() && ids.add(it.id) }
            val artists = HashSet<String>()
            val diverse = candidates.filter { it.artist.isNotBlank() && artists.add(it.artist.trim().lowercase(Locale.ROOT)) }.take(3)
            return (diverse + candidates.filterNot { it in diverse }).take(3)
        }
    }
}
