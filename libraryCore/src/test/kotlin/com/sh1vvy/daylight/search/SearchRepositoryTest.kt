/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-12 through 2026-10-12.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.search

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SearchRepositoryTest {
    private data class Key(val term: String, val account: Int = 1, val filter: Int = 0)
    private fun TestScope.repo(
        first: suspend (Key) -> SearchPage<String> = { SearchPage(listOf(it.term), "next") },
        more: suspend (Key, String) -> SearchPage<String> = { _, _ -> SearchPage(listOf("next")) },
        suggest: suspend (Key) -> List<String> = { listOf(it.term + " completion") },
        preview: suspend (Key) -> List<String> = { listOf(it.term + " preview") },
        now: () -> Long = { testScheduler.currentTime },
        current: (Key) -> Boolean = { true },
    ) = SearchRepository(backgroundScope, first, more, suggest, preview, current, { it }, { it.message ?: "Failed" }, now)

    @Test fun typingPublishesTermImmediatelyAndDebouncesEachLane() = runTest {
        var suggestions = 0; var previews = 0
        val repo = repo(suggest = { suggestions++; listOf(it.term, "Other") }, preview = { previews++; listOf("track") })
        repo.edit("a", Key("a")); advanceTimeBy(100)
        repo.edit("ab", Key("ab")); advanceTimeBy(159); runCurrent()
        assertEquals(listOf("ab"), repo.state.value.suggestions); assertEquals(0, suggestions)
        advanceTimeBy(1); runCurrent(); assertEquals(listOf("ab", "Other"), repo.state.value.suggestions)
        advanceTimeBy(140); runCurrent(); assertEquals(listOf("track"), repo.state.value.previews)
        assertEquals(1, suggestions); assertEquals(1, previews)
    }

    @Test fun latePreviewCannotOverwriteNewQueryOrSubmittedResults() = runTest {
        val old = CompletableDeferred<List<String>>()
        val repo = repo(preview = { withContext(NonCancellable) { old.await() } })
        repo.edit("old", Key("old")); advanceTimeBy(301); runCurrent()
        repo.submit("new", Key("new")); runCurrent()
        old.complete(listOf("wrong")); runCurrent()
        assertEquals("new", repo.state.value.query)
        assertEquals(SearchStage.RESULTS, repo.state.value.stage)
        assertEquals(listOf("new"), repo.state.value.rows); assertTrue(repo.state.value.previews.isEmpty())
    }

    @Test fun staleFirstPageDoesNotResurrectAfterClear() = runTest {
        val answer = CompletableDeferred<SearchPage<String>>()
        val repo = repo(first = { withContext(NonCancellable) { answer.await() } })
        repo.submit("old", Key("old")); runCurrent(); repo.clear()
        answer.complete(SearchPage(listOf("wrong"))); runCurrent()
        assertEquals(SearchSnapshot(), repo.state.value)
    }

    @Test fun stalePaginationCannotAppendToANewerQuery() = runTest {
        val answer = CompletableDeferred<SearchPage<String>>()
        val repo = repo(more = { _, _ -> withContext(NonCancellable) { answer.await() } })
        repo.submit("old", Key("old")); runCurrent(); repo.loadMore(); runCurrent()
        repo.submit("new", Key("new")); runCurrent()
        answer.complete(SearchPage(listOf("wrong"))); runCurrent()
        assertEquals(listOf("new"), repo.state.value.rows)
        assertFalse(repo.state.value.loadingMore)
    }

    @Test fun providerIdentityChangeRetiresSpinnerAndPrivateRows() = runTest {
        var account = 1
        val answer = CompletableDeferred<SearchPage<String>>()
        val repo = repo(first = { if (it.account == 1) answer.await() else SearchPage(listOf("new account")) },
            current = { it.account == account })
        repo.submit("q", Key("q")); runCurrent(); account = 2
        answer.complete(SearchPage(listOf("old account"))); runCurrent()
        assertFalse(repo.state.value.loading); assertNull(repo.state.value.rows)
        assertNotNull(repo.state.value.error)
        repo.submit("q", Key("q", account)); runCurrent()
        assertEquals(listOf("new account"), repo.state.value.rows)
    }

    @Test fun oversizedActiveResultsKeepAResumableBoundedCache() = runTest {
        var firstCalls = 0
        val repo = repo(first = { firstCalls++; SearchPage((0 until 300).map { "$it" }, "300") },
            more = { _, next -> val start = next.toInt(); SearchPage((start until start + 300).map { "$it" }, "${start + 300}") })
        repo.submit("q", Key("q")); runCurrent()
        repeat(2) { repo.loadMore(); runCurrent() }
        assertEquals(900, repo.state.value.rows?.size)
        repo.edit("", Key("")); repo.submit("q", Key("q")); runCurrent()
        assertEquals(1, firstCalls); assertEquals(600, repo.state.value.rows?.size)
        repo.loadMore(); runCurrent()
        assertEquals((0 until 900).map { "$it" }, repo.state.value.rows)
    }

    @Test fun submitIsImmediateAndDuplicateSubmitDoesNotRestart() = runTest {
        var calls = 0
        val repo = repo(first = { calls++; delay(100); SearchPage(listOf("hit")) })
        repo.submit("a", Key("a")); repo.submit("a", Key("a")); runCurrent()
        assertEquals(1, calls); assertTrue(repo.state.value.loading)
        advanceTimeBy(100); runCurrent(); assertEquals(listOf("hit"), repo.state.value.rows)
    }

    @Test fun exactCacheAndPreviewCacheAreAccountAndFilterScoped() = runTest {
        var calls = 0
        val repo = repo(first = { calls++; SearchPage(listOf("${it.account}:${it.filter}")) })
        repo.submit("same", Key("same")); runCurrent()
        repo.edit("", Key("")); repo.submit("same", Key("same")); runCurrent(); assertEquals(1, calls)
        repo.submit("same", Key("same", 2)); runCurrent()
        repo.submit("same", Key("same", 2, 1)); runCurrent()
        assertEquals(3, calls); assertEquals(listOf("2:1"), repo.state.value.rows)
    }

    @Test fun backspacingReusesPreviewImmediatelyWithoutBlankingCommittedRows() = runTest {
        val repo = repo()
        repo.edit("ab", Key("ab")); advanceTimeBy(301); runCurrent()
        repo.submit("ab", Key("ab")); runCurrent()
        repo.edit("abc", Key("abc")); repo.edit("ab", Key("ab"))
        assertEquals(listOf("ab preview"), repo.state.value.previews)
        assertEquals(listOf("ab"), repo.state.value.rows)
    }

    @Test fun localSourceCancelsNetworkAndClearsOnlinePreview() = runTest {
        var calls = 0
        val repo = repo(suggest = { calls++; listOf("remote") })
        repo.edit("ab", Key("ab")); repo.edit("ab", Key("ab"), online = false)
        advanceTimeBy(500); runCurrent()
        assertEquals(0, calls); assertTrue(repo.state.value.suggestions.isEmpty()); assertNull(repo.state.value.rows)
    }

    @Test fun pagingKeepsRowsDeduplicatesAndStopsRepeatedTokens() = runTest {
        var calls = 0
        val repo = repo(first = { SearchPage(listOf("one", "one"), "next") },
            more = { _, _ -> calls++; SearchPage(listOf("one", "two"), "next") })
        repo.submit("q", Key("q")); runCurrent(); repo.loadMore(); repo.loadMore(); runCurrent()
        assertEquals(listOf("one", "two"), repo.state.value.rows)
        repo.loadMore(); runCurrent(); assertEquals(1, calls)
    }

    @Test fun paginationFailureRetainsRowsAndWaitsForExplicitRetry() = runTest {
        var calls = 0
        val repo = repo(more = { _, _ -> if (++calls == 1) error("offline") else SearchPage(listOf("two")) })
        repo.submit("one", Key("one")); runCurrent(); repo.loadMore(); runCurrent()
        assertEquals(listOf("one"), repo.state.value.rows); assertEquals("offline", repo.state.value.pageError)
        repo.loadMore(); runCurrent(); assertEquals(1, calls)
        repo.loadMore(retry = true); runCurrent(); assertEquals(listOf("one", "two"), repo.state.value.rows)
    }

    @Test fun failedPreviewLeavesTypedSearchUsableAndFailedResultCanRetry() = runTest {
        var calls = 0
        val repo = repo(suggest = { error("offline") }, preview = { error("offline") },
            first = { if (++calls == 1) error("offline") else SearchPage(listOf("hit")) })
        repo.edit("hi", Key("hi")); advanceTimeBy(301); runCurrent(); assertEquals(listOf("hi"), repo.state.value.suggestions)
        repo.submit("hi", Key("hi")); runCurrent(); assertEquals("offline", repo.state.value.error)
        repo.submit("hi", Key("hi"), refresh = true); runCurrent(); assertEquals(listOf("hit"), repo.state.value.rows)
    }

    @Test fun cacheExpiresAndEvictsLeastRecentlyUsedEntries() = runTest {
        var calls = 0
        val repo = repo(first = { calls++; SearchPage(listOf(it.term)) })
        repeat(14) { repo.submit("$it", Key("$it")); runCurrent() }
        repo.submit("0", Key("0")); runCurrent(); assertEquals(15, calls)
        advanceTimeBy(120_001); repo.edit("", Key("")); repo.submit("0", Key("0")); runCurrent()
        assertEquals(16, calls)
    }
}
