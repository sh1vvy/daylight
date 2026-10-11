/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.home

import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class HomeBatch<S>(val sections: List<S>, val next: String?)
data class HomeSnapshot<S>(val primary: List<S>, val recent: S?, val extras: List<List<S>>, val checkedAt: Long, val tail: List<S> = emptyList())
data class HomeLoad<K, S>(val key: K? = null, val sections: List<S> = emptyList(), val ready: Boolean = false,
    val pending: Int = 0, val recentLoading: Boolean = false, val paging: Boolean = false, val failure: Throwable? = null)

/** Owns Home fetches, ordering, cached content and cancellation independently of a screen. */
class HomeRepository<K, S>(
    private val scope: CoroutineScope,
    private val primary: suspend (K) -> HomeBatch<S>,
    private val recent: suspend (K) -> S?,
    private val extras: List<suspend (K) -> List<S>>,
    private val more: suspend (K, String) -> HomeBatch<S>,
    private val read: suspend (K) -> HomeSnapshot<S>?,
    private val save: suspend (K, HomeSnapshot<S>, () -> Boolean) -> Unit,
    private val title: (S) -> String,
    private val browseIds: (S) -> Set<String>,
    private val itemCount: (S) -> Int,
    private val isRecent: (S) -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val revision = AtomicLong()
    private val lock = Any()
    private val _state = MutableStateFlow(HomeLoad<K, S>())
    val state: StateFlow<HomeLoad<K, S>> = _state
    private var snapshot: HomeSnapshot<S>? = null
    private var continuation: String? = null
    private val seenTokens = HashSet<String>()
    private var loading: Job? = null
    private var paging: Job? = null

    fun load(key: K, signedIn: Boolean, force: Boolean = false): Job = synchronized(lock) {
        if (!force && _state.value.key == key && loading?.isActive == true) return@synchronized loading!!
        if (!force && _state.value.key == key && snapshot?.let { now() - it.checkedAt in 0 until 60_000 } == true) return@synchronized scope.launch { }
        val same = _state.value.key == key
        val previous = snapshot.takeIf { same }
        val version = revision.incrementAndGet()
        loading?.cancel(); paging?.cancel(); continuation = null; seenTokens.clear()
        snapshot = previous
        _state.value = HomeLoad(key, if (same) _state.value.sections else emptyList(), ready = same && _state.value.ready,
            pending = 1 + extras.size, recentLoading = signedIn)
        loading = scope.launch(io, start = CoroutineStart.LAZY) {
            bootstrap(key, signedIn, version, previous)
        }.also { it.start() }
        loading!!
    }

    fun loadMore(): Job? = synchronized(lock) {
        if (loading?.isActive == true || paging?.isActive == true) return@synchronized null
        val key = _state.value.key ?: return@synchronized null
        val token = continuation ?: return@synchronized null
        if (!seenTokens.add(token)) { continuation = null; return@synchronized null }
        val version = revision.get()
        _state.value = _state.value.copy(paging = true)
        paging = scope.launch(io, start = CoroutineStart.LAZY) {
            try {
                val batch = more(key, token)
                synchronized(lock) {
                    if (revision.get() != version) return@synchronized
                    val old = snapshot ?: return@synchronized
                    val combined = unique(_state.value.sections + batch.sections)
                    val added = combined.drop(_state.value.sections.size)
                    continuation = batch.next.takeIf { added.isNotEmpty() }
                    snapshot = old.copy(tail = old.tail + added)
                    publish()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { synchronized(lock) { if (revision.get() == version) seenTokens.remove(token) } }
            finally { synchronized(lock) { if (revision.get() == version) _state.value = _state.value.copy(paging = false) } }
        }.also { it.start() }
        paging
    }

    fun edit(transform: (S) -> S) = synchronized(lock) {
        val previous = snapshot ?: return@synchronized
        val changed = previous.copy(primary = previous.primary.map(transform), recent = previous.recent?.let(transform),
            extras = previous.extras.map { it.map(transform) }, tail = previous.tail.map(transform))
        if (previous == changed) return@synchronized
        val version = revision.incrementAndGet()
        loading?.cancel(); paging?.cancel()
        snapshot = changed
        _state.value = _state.value.copy(pending = 0, recentLoading = false, paging = false)
        publish()
        _state.value.key?.let { key -> scope.launch(io) { save(key, changed) { revision.get() == version } } }
    }

    fun clear() = synchronized(lock) {
        revision.incrementAndGet(); loading?.cancel(); paging?.cancel(); snapshot = null; continuation = null
        seenTokens.clear(); _state.value = HomeLoad()
    }

    private suspend fun bootstrap(key: K, signedIn: Boolean, version: Long, existing: HomeSnapshot<S>?) {
        val owner = currentCoroutineContext()
        fun current() = owner.isActive && revision.get() == version
        try {
            val cached = existing ?: runCatchingCancellable { read(key) }.getOrNull()
            synchronized(lock) {
                if (!current()) return
                snapshot = cached ?: HomeSnapshot(emptyList(), null, List(extras.size) { emptyList() }, 0)
                if (cached != null) publish()
            }
            // Cached content stays available; network results commit as one settled feed.
            // No late source may insert a section ahead of the shelf being scrolled.
            supervisorScope {
                val history = if (signedIn) async { runCatchingCancellable { withTimeout(4_000) { recent(key) } } } else null
                val optional = extras.map { source -> async {
                    runCatchingCancellable { withTimeout(4_000) { source(key) } }
                } }
                val core = runCatchingCancellable { primary(key) }
                val leading = history?.await()
                val additions = optional.map { it.await() }
                synchronized(lock) {
                    if (!current()) return@synchronized
                    val old = snapshot!!
                    snapshot = old.copy(
                        primary = core.getOrNull()?.sections ?: old.primary,
                        recent = when { !signedIn -> null; leading?.isSuccess == true -> leading.getOrNull(); else -> old.recent },
                        extras = additions.mapIndexed { index, result -> result.getOrNull() ?: old.extras.getOrNull(index).orEmpty() },
                        tail = if (core.isSuccess) emptyList() else old.tail,
                        checkedAt = if (core.isSuccess) now() else old.checkedAt,
                    )
                    if (core.isSuccess) continuation = core.getOrThrow().next
                    _state.value = _state.value.copy(pending = 0, recentLoading = false,
                        failure = core.exceptionOrNull().takeIf { !_state.value.ready })
                    publish()
                }
                if (current() && core.isSuccess) save(key, synchronized(lock) { snapshot!! }, ::current)
            }
        } finally {
            synchronized(lock) { if (revision.get() == version) _state.value = _state.value.copy(pending = 0, recentLoading = false) }
        }
    }

    private fun publish() {
        val value = snapshot ?: return
        val base = if (_state.value.recentLoading || value.recent != null) value.primary.filterNot(isRecent) else value.primary
        val sections = unique(listOfNotNull(value.recent) + base + value.extras.flatten() + value.tail)
        _state.value = _state.value.copy(sections = sections, ready = sections.isNotEmpty() || value.checkedAt > 0,
            failure = _state.value.failure.takeIf { sections.isEmpty() })
    }

    /** Build each section's signature once, instead of rescanning artwork cards for every comparison. */
    private fun unique(sections: List<S>): List<S> {
        val titles = HashSet<String>()
        val signatures = ArrayList<Pair<Set<String>, Int>>()
        return sections.filter { section ->
            val signature = browseIds(section)
            val size = itemCount(section)
            if (size == 0 || !titles.add(title(section).trim().lowercase(Locale.ROOT))) false
            else if (signature.isNotEmpty() && signatures.any { (other, count) ->
                    val overlap = signature.count { it in other }
                    overlap > 0 && overlap * 5 >= 3 * minOf(size, count)
                }) false
            else { signatures += signature to size; true }
        }
    }

    private suspend fun <T> runCatchingCancellable(block: suspend () -> T): Result<T> = try { Result.success(block()) }
        catch (timeout: TimeoutCancellationException) { Result.failure(timeout) }
        catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { Result.failure(failure) }
}
