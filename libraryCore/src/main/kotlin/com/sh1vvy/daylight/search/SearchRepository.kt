/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-12 through 2026-10-12.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.search

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SearchStage { RECENT, EDITING, RESULTS }
data class SearchPage<T>(val rows: List<T>, val continuation: String? = null)
data class SearchSnapshot<T>(
    val query: String = "", val stage: SearchStage = SearchStage.RECENT,
    val suggestions: List<String> = emptyList(), val previews: List<T> = emptyList(),
    val rows: List<T>? = null, val loading: Boolean = false, val loadingMore: Boolean = false,
    val error: String? = null, val pageError: String? = null, val scrollEpoch: Int = 0,
)

/** Daylight request ownership. K supplies provider/account/filter identity; the core imports no app models. */
class SearchRepository<K : Any, T>(
    private val scope: CoroutineScope,
    private val first: suspend (K) -> SearchPage<T>,
    private val more: suspend (K, String) -> SearchPage<T>,
    private val suggest: suspend (K) -> List<String>,
    private val preview: suspend (K) -> List<T>,
    private val isCurrent: (K) -> Boolean,
    private val rowKey: (T) -> String,
    private val failure: (Throwable) -> String,
    private val now: () -> Long = System::currentTimeMillis,
    private val typingDelay: Long = 160,
    private val previewDelay: Long = 300,
) {
    private data class Warm<T>(val page: SearchPage<T>, val at: Long)
    private val mutable = MutableStateFlow(SearchSnapshot<T>())
    val state = mutable.asStateFlow()
    private val pages = LinkedHashMap<K, Warm<T>>(16, .75f, true)
    private val previews = LinkedHashMap<K, Warm<T>>(16, .75f, true)
    private val completions = LinkedHashMap<K, Pair<List<String>, Long>>(16, .75f, true)
    private var request: Job? = null
    private var pagination: Job? = null
    private var revision = 0L
    private var activeKey: K? = null
    private var token: String? = null
    private val consumedTokens = HashSet<String>()

    fun edit(input: String, key: K, online: Boolean = true) {
        cancel()
        val cached = warm(previews, key)?.rows.orEmpty()
        val texts = completions[key]?.takeIf { now() - it.second in 0 until TTL }?.first.orEmpty()
        mutable.value = mutable.value.copy(query = input, stage = if (input.isBlank()) SearchStage.RECENT else SearchStage.EDITING,
            suggestions = if (online) suggestions(input, texts) else emptyList(), previews = if (online) cached else emptyList(),
            rows = if (input.isBlank() || !online) null else mutable.value.rows, loading = false, loadingMore = false,
            error = null, pageError = null, scrollEpoch = mutable.value.scrollEpoch + 1)
        if (input.isBlank() || !online) return
        activeKey = key
        val generation = revision
        request = scope.launch {
            supervisorScope {
                launch {
                    delay(typingDelay)
                    try {
                        val texts = withTimeout(5_000) { suggest(key) }.distinct().take(7)
                        if (valid(generation, key)) {
                            completions[key] = texts to now(); trimTexts()
                            mutable.value = mutable.value.copy(suggestions = suggestions(input, texts))
                        }
                    } catch (e: CancellationException) { if (e !is TimeoutCancellationException) throw e }
                      catch (_: Exception) { /* The typed term remains usable even offline. */ }
                }
                if (input.trim().length >= 2) launch {
                    delay(previewDelay)
                    try {
                        val rows = withTimeout(6_000) { preview(key) }.distinctBy(rowKey).take(8)
                        if (valid(generation, key)) {
                            put(previews, key, SearchPage(rows))
                            mutable.value = mutable.value.copy(previews = rows)
                        }
                    } catch (e: CancellationException) { if (e !is TimeoutCancellationException) throw e }
                      catch (_: Exception) { /* Keep the cached preview and text completions. */ }
                }
            }
        }
    }

    fun submit(input: String, key: K, refresh: Boolean = false) {
        if (input.isBlank()) { edit(input, key); return }
        if (!refresh && activeKey == key && mutable.value.stage == SearchStage.RESULTS &&
            (mutable.value.loading || mutable.value.rows != null && mutable.value.error == null)) return
        cancel()
        activeKey = key
        val cached = if (refresh) null else warm(pages, key)
        val initial = cached?.rows ?: warm(previews, key)?.rows
        mutable.value = SearchSnapshot(input, SearchStage.RESULTS, rows = initial,
            loading = cached == null, scrollEpoch = mutable.value.scrollEpoch + 1)
        if (cached != null && isCurrent(key)) { token = cached.continuation; return }
        val generation = revision
        request = scope.launch {
            try {
                val page = withTimeout(12_000) { first(key) }
                if (valid(generation, key)) {
                    val normalized = page.copy(rows = page.rows.distinctBy(rowKey))
                    put(pages, key, normalized); token = normalized.continuation
                    mutable.value = mutable.value.copy(rows = normalized.rows, loading = false, error = null)
                }
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                if (valid(generation, key)) mutable.value = mutable.value.copy(loading = false, error = failure(e))
            } catch (e: Exception) {
                if (valid(generation, key)) mutable.value = mutable.value.copy(loading = false, error = failure(e))
            }
        }
    }

    fun loadMore(retry: Boolean = false) {
        val key = activeKey ?: return
        val next = token ?: return
        val value = mutable.value
        if (value.stage != SearchStage.RESULTS || value.loading || value.loadingMore || !isCurrent(key) ||
            next in consumedTokens || value.pageError != null && !retry) return
        val generation = revision
        mutable.value = value.copy(loadingMore = true, pageError = null)
        pagination = scope.launch {
            try {
                val page = withTimeout(12_000) { more(key, next) }
                if (valid(generation, key)) {
                    consumedTokens += next
                    val rows = (mutable.value.rows.orEmpty() + page.rows).distinctBy(rowKey)
                    token = page.continuation?.takeUnless { it in consumedTokens }
                    // Oversized results remain in the active scene. Keep the last bounded
                    // cached prefix and its matching continuation, so reopening can continue.
                    put(pages, key, SearchPage(rows, token))
                    mutable.value = mutable.value.copy(rows = rows, loadingMore = false)
                }
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                if (valid(generation, key)) mutable.value = mutable.value.copy(loadingMore = false, pageError = failure(e))
            } catch (e: Exception) {
                if (valid(generation, key)) mutable.value = mutable.value.copy(loadingMore = false, pageError = failure(e))
            }
        }
    }

    fun clear() { cancel(); pages.clear(); previews.clear(); completions.clear(); mutable.value = SearchSnapshot() }
    private fun cancel() { revision++; request?.cancel(); pagination?.cancel(); activeKey = null; token = null; consumedTokens.clear() }
    private fun valid(generation: Long, key: K): Boolean {
        if (generation != revision || activeKey != key) return false
        if (isCurrent(key)) return true
        // A provider/account change can invalidate a suspended call before the host
        // sends clear(). Retire its spinner and cached identity as well as its rows.
        cancel(); pages.clear(); previews.clear(); completions.clear()
        mutable.value = SearchSnapshot(query = mutable.value.query, stage = mutable.value.stage,
            error = if (mutable.value.stage == SearchStage.RESULTS) failure(IllegalStateException("Search source changed. Try again.")) else null,
            scrollEpoch = mutable.value.scrollEpoch + 1)
        return false
    }
    private fun warm(cache: LinkedHashMap<K, Warm<T>>, key: K): SearchPage<T>? = cache[key]
        ?.takeIf { isCurrent(key) && now() - it.at in 0 until TTL }?.page
    private fun put(cache: LinkedHashMap<K, Warm<T>>, key: K, page: SearchPage<T>) {
        if (page.rows.size > CACHE_ROWS) return
        cache[key] = Warm(page, now())
        while (cache.size > 12 || cache.values.sumOf { it.page.rows.size } > CACHE_ROWS) cache.remove(cache.keys.first())
    }
    private fun trimTexts() { while (completions.size > 24) completions.remove(completions.keys.first()) }
    private fun suggestions(input: String, fetched: List<String>) = if (input.isBlank()) emptyList() else
        (listOf(input.trim()) + fetched).distinctBy { it.lowercase(java.util.Locale.ROOT) }.take(8)
    companion object { private const val TTL = 120_000L; private const val CACHE_ROWS = 600 }
}
