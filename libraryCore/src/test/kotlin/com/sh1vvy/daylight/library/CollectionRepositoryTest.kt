/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.library

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.*
import org.junit.Test
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionRepositoryTest {
    private data class Row(val recording: String, val occurrence: String = recording)
    private fun TestScope.repository(first: suspend (String) -> CollectionBatch<String, Row>,
        next: suspend (String, String) -> CollectionBatch<String, Row> = { _, _ -> error("Unexpected page") },
        read: suspend (String) -> CollectionListing<String, Row>? = { null },
        save: suspend (String, CollectionListing<String, Row>, () -> Boolean) -> Unit = { _, _, _ -> },
        budget: Int = 6) = CollectionRepository(this, first, next, read, save,
        { it.occurrence }, { it.recording }, now = { 90_000 }, maxWarmEntries = budget, io = StandardTestDispatcher(testScheduler))

    @Test fun firstPageIsVisibleWhileContinuationLoadsAndConcurrentVisitsShareWork() = runTest {
        val more = CompletableDeferred<CollectionBatch<String, Row>>()
        var calls = 0
        val repo = repository(first = { calls++; CollectionBatch("Header", listOf(Row("one")), "next") }, next = { _, _ -> more.await() })
        val first = repo.open("list")
        assertSame(first, repo.open("list"))
        runCurrent()
        assertEquals(listOf(Row("one")), first.value.listing!!.rows)
        assertTrue(first.value.loading)
        more.complete(CollectionBatch(null, listOf(Row("two")), null))
        runCurrent()
        assertEquals(listOf(Row("one"), Row("two")), first.value.listing!!.rows)
        assertFalse(first.value.loading)
        repo.open("list"); runCurrent()
        assertEquals(1, calls)
    }

    @Test fun separatePlaylistOccurrencesRemainAndRecommendationsExcludeEveryLoadedRecording() = runTest {
        val repo = repository(first = { CollectionBatch("Header", listOf(Row("same", "a")), "next", listOf(Row("same"), Row("fresh"), Row("later"))) },
            next = { _, _ -> CollectionBatch(null, listOf(Row("same", "a"), Row("same", "b"), Row("later")), null, listOf(Row("fresh"))) })
        val state = repo.open("list"); runCurrent()
        assertEquals(listOf("a", "b", "later"), state.value.listing!!.rows.map { it.occurrence })
        assertEquals(listOf(Row("fresh")), state.value.listing!!.suggestions)
    }

    @Test fun fullCachedListingIsNeverReplacedByPartialOrFailedRefresh() = runTest {
        val saved = CollectionListing("Cached", listOf(Row("one"), Row("two")), complete = true, checkedAt = 1)
        val page = CompletableDeferred<CollectionBatch<String, Row>>()
        val repo = repository(first = { CollectionBatch("Remote", listOf(Row("new")), "next") }, next = { _, _ -> page.await() }, read = { saved })
        val state = repo.open("list"); runCurrent()
        assertEquals(saved, state.value.listing)
        page.completeExceptionally(IllegalStateException("Offline")); runCurrent()
        assertEquals(saved, state.value.listing)
        assertFalse(state.value.loading)
        assertNotNull(state.value.failure)
    }

    @Test fun freshRestartAvoidsNetworkButExplicitRefreshBypassesAge() = runTest {
        var calls = 0
        val saved = CollectionListing("Cached", listOf(Row("one")), complete = true, checkedAt = 89_000)
        val repo = repository(first = { calls++; CollectionBatch("New", emptyList(), null) }, read = { saved })
        val state = repo.open("list"); runCurrent()
        assertEquals(saved, state.value.listing); assertEquals(0, calls)
        repo.open("list", force = true); runCurrent()
        assertEquals(1, calls); assertTrue(state.value.listing!!.complete); assertTrue(state.value.listing!!.rows.isEmpty())
    }

    @Test fun closingAfterCompleteCannotCancelPersistence() = runTest {
        val durable = CompletableDeferred<Unit>()
        val writes = mutableListOf<String>()
        val repo = repository(first = { CollectionBatch("New", listOf(Row("one")), null) }, save = { _, value, current ->
            durable.await(); if (current()) writes += value.header!!
        })
        val state = repo.open("list"); runCurrent()
        assertTrue(state.value.loading)
        durable.complete(Unit); runCurrent()
        assertFalse(state.value.loading)
        repo.release("list"); runCurrent()
        assertEquals(listOf("New"), writes)
    }

    @Test fun editCancelsOldContinuationAndItsWriteCannotOverwriteTheEdit() = runTest {
        val more = CompletableDeferred<CollectionBatch<String, Row>>()
        val writes = mutableListOf<CollectionListing<String, Row>>()
        val repo = repository(first = { CollectionBatch("Old", listOf(Row("one")), "next") }, next = { _, _ -> more.await() },
            save = { _, value, current -> if (current()) writes += value })
        val state = repo.open("list"); runCurrent()
        repo.edit({ it == "list" }) { it.copy(header = "Edited", rows = it.rows + Row("added")) }
        more.complete(CollectionBatch(null, listOf(Row("late")), null)); runCurrent()
        assertEquals("Edited", state.value.listing!!.header)
        assertEquals(listOf(Row("one"), Row("added")), state.value.listing!!.rows)
        assertEquals(state.value.listing, writes.last())
    }

    @Test fun releasingOneScreenKeepsAnotherSubscriberAliveButClosingAllStopsPaging() = runTest {
        val more = CompletableDeferred<CollectionBatch<String, Row>>()
        val repo = repository(first = { CollectionBatch("Header", listOf(Row("one")), "next") }, next = { _, _ -> more.await() })
        val state = repo.open("list")
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect { } }
        runCurrent(); repo.release("list"); assertTrue(state.value.loading)
        observer.cancel(); runCurrent(); repo.release("list"); runCurrent()
        assertFalse(state.value.loading); more.complete(CollectionBatch(null, listOf(Row("late")), null)); runCurrent()
        assertEquals(listOf(Row("one")), state.value.listing!!.rows)
    }

    @Test fun warmEvictionAndRepeatedContinuationTokensStayBounded() = runTest {
        var calls = 0
        val repo = repository(first = { calls++; CollectionBatch(it, listOf(Row(it)), null) }, budget = 2)
        repeat(3) { repo.open("$it"); runCurrent(); repo.release("$it") }
        repo.open("0"); runCurrent(); assertEquals(4, calls)
        var pages = 0
        val loop = repository(first = { CollectionBatch("Header", listOf(Row("one")), "loop") },
            next = { _, _ -> pages++; CollectionBatch(null, listOf(Row("two")), "loop") })
        val state = loop.open("list"); runCurrent()
        assertEquals(1, pages); assertNotNull(state.value.failure); assertFalse(state.value.loading)
    }

    @Test fun longPlaylistDeduplicationIndexesEachOccurrenceOnlyOnce() = runTest {
        var indexed = 0
        val repo = CollectionRepository<String, String, Row>(this,
            first = { CollectionBatch("Header", (0..19).map { Row("$it") }, "1") },
            next = { _, token -> val page = token.toInt()
                CollectionBatch(null, (page * 20 until (page + 1) * 20).map { Row("$it") }, if (page < 99) "${page + 1}" else null)
            }, read = { null }, save = { _, _, _ -> }, rowKey = { indexed++; it.occurrence }, recordingKey = { it.recording },
            io = StandardTestDispatcher(testScheduler))
        val state = repo.open("large"); runCurrent()
        assertEquals(2_000, state.value.listing!!.rows.size)
        assertEquals(2_000, indexed, "Indexing must scale with new rows, not rescan the accumulated list per page")
    }
}
