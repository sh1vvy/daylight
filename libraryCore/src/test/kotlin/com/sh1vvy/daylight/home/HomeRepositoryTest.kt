/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.home

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Test
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class HomeRepositoryTest {
    private data class Shelf(val title: String, val ids: Set<String> = setOf(title), val albums: Boolean = false)
    private fun TestScope.repository(primary: suspend (String) -> HomeBatch<Shelf>,
        recent: suspend (String) -> Shelf? = { null }, extras: List<suspend (String) -> List<Shelf>> = emptyList(),
        more: suspend (String, String) -> HomeBatch<Shelf> = { _, _ -> error("Unexpected continuation") },
        read: suspend (String) -> HomeSnapshot<Shelf>? = { null },
        save: suspend (String, HomeSnapshot<Shelf>, () -> Boolean) -> Unit = { _, _, _ -> },
        preparedPages: Int = 0, preparedBeforeExtras: Boolean = false) = HomeRepository(
            this, primary, recent, extras, more, read, save, { it.title }, { if (it.albums) it.ids else emptySet() },
            { it.ids.size }, { it.title == "Recents" }, now = { 90_000 }, io = StandardTestDispatcher(testScheduler), preparedPages = preparedPages, preparedBeforeExtras = preparedBeforeExtras)

    @Test fun cachedFeedStaysVisibleUntilCoreAndHistoryCommitTogether() = runTest {
        val old = HomeSnapshot(listOf(Shelf("Listen again")), Shelf("Recents", setOf("old")), emptyList(), 1)
        val core = CompletableDeferred<HomeBatch<Shelf>>(); val history = CompletableDeferred<Shelf?>()
        val repo = repository({ core.await() }, { history.await() }, read = { old })
        val job = repo.load("one", true); assertSame(job, repo.load("one", true)); runCurrent()
        assertEquals(listOf("Recents", "Listen again"), repo.state.value.sections.map { it.title })
        core.complete(HomeBatch(listOf(Shelf("New")), "next")); runCurrent()
        assertEquals(old.recent, repo.state.value.sections.first())
        assertEquals("Listen again", repo.state.value.sections.last().title)
        history.complete(Shelf("Recents", setOf("new"))); runCurrent()
        assertEquals(listOf("Recents", "New"), repo.state.value.sections.map { it.title }); assertFalse(repo.state.value.recentLoading)
    }

    @Test fun extrasHaveStablePositionsRegardlessOfCompletionOrderAndOldSnapshotsCanGrowSlots() = runTest {
        val first = CompletableDeferred<List<Shelf>>()
        val repo = repository({ HomeBatch(listOf(Shelf("Main")), null) }, extras = listOf({ first.await() }, { listOf(Shelf("Second")) }),
            read = { HomeSnapshot(listOf(Shelf("Cached")), null, emptyList(), 1) })
        repo.load("one", false); runCurrent(); first.complete(listOf(Shelf("First"))); runCurrent()
        assertEquals(listOf("Main", "First", "Second"), repo.state.value.sections.map { it.title })
        assertEquals(0, repo.state.value.pending)
    }

    @Test fun failedHistoryKeepsRecentsAndVerifiedEmptyHistoryClearsIt() = runTest {
        var fails = true
        val repo = repository({ HomeBatch(listOf(Shelf("Main")), null) }, { if (fails) error("Offline") else null },
            read = { HomeSnapshot(listOf(Shelf("Old")), Shelf("Recents"), emptyList(), 1) })
        repo.load("one", true); runCurrent(); assertEquals("Recents", repo.state.value.sections.first().title)
        fails = false; repo.load("one", true, force = true); runCurrent()
        assertEquals(listOf("Main"), repo.state.value.sections.map { it.title })
    }

    @Test fun providerFailuresRetainCachedSectionsAndDiskReadFailureStillLoadsOnline() = runTest {
        val repo = repository({ error("Offline") }, extras = listOf({ error("Offline") }),
            read = { HomeSnapshot(listOf(Shelf("Cached")), null, listOf(listOf(Shelf("Extra"))), 1) })
        repo.load("one", false); runCurrent()
        assertEquals(listOf("Cached", "Extra"), repo.state.value.sections.map { it.title }); assertTrue(repo.state.value.ready)
        val online = repository({ HomeBatch(listOf(Shelf("Main")), null) }, read = { error("Disk unavailable") })
        online.load("one", false); runCurrent(); assertEquals("Main", online.state.value.sections.single().title)
    }

    @Test fun switchAccountsCancelsOldFetchAndPersistsOnlyTheActiveProfile() = runTest {
        val old = CompletableDeferred<HomeBatch<Shelf>>(); val saved = mutableListOf<String>()
        val repo = repository({ if (it == "one") old.await() else HomeBatch(listOf(Shelf("Two")), null) },
            save = { key, _, current -> if (current()) saved += key })
        repo.load("one", false); runCurrent(); repo.load("two", false); runCurrent()
        old.complete(HomeBatch(listOf(Shelf("One")), null)); runCurrent()
        assertEquals("two", repo.state.value.key); assertEquals(listOf("two"), saved)
    }

    @Test fun continuationRetriesFailureAndNeverLoopsOrDuplicatesSections() = runTest {
        var calls = 0
        val repo = repository({ HomeBatch(listOf(Shelf("Main")), "next") }, more = { _, _ ->
            calls++; if (calls == 1) error("Offline") else HomeBatch(listOf(Shelf("Main"), Shelf("More")), "next")
        })
        repo.load("one", false); runCurrent(); repo.loadMore(); runCurrent(); assertFalse(repo.state.value.paging)
        repo.loadMore(); runCurrent(); assertEquals(listOf("Main", "More"), repo.state.value.sections.map { it.title })
        assertNull(repo.loadMore()); assertEquals(2, calls)
    }
    @Test fun coldFeedCommitsOnlyAfterHistoryAndSupplementsSettle() = runTest {
        val history = CompletableDeferred<Shelf?>(); val extra = CompletableDeferred<List<Shelf>>()
        val repo = repository({ HomeBatch(listOf(Shelf("Main")), null) }, { history.await() }, listOf({ extra.await() }))
        repo.load("one", true); runCurrent()
        assertTrue(repo.state.value.sections.isEmpty()); assertFalse(repo.state.value.ready)
        history.complete(Shelf("Recents")); runCurrent(); assertTrue(repo.state.value.sections.isEmpty())
        extra.complete(listOf(Shelf("Extra"))); runCurrent()
        assertEquals(listOf("Recents", "Main", "Extra"), repo.state.value.sections.map { it.title })
    }

    @Test fun optionalTimeoutRetainsCacheAndCannotHangFeedOrCancelItsCore() = runTest {
        val repo = repository({ HomeBatch(listOf(Shelf("Main")), null) }, { awaitCancellation() }, listOf({ awaitCancellation() }),
            read = { HomeSnapshot(listOf(Shelf("Old")), Shelf("Recents"), listOf(listOf(Shelf("Extra"))), 1) })
        repo.load("one", true); runCurrent(); advanceTimeBy(4_001); runCurrent()
        assertEquals(listOf("Recents", "Main", "Extra"), repo.state.value.sections.map { it.title })
        assertEquals(0, repo.state.value.pending); assertFalse(repo.state.value.recentLoading)
    }

    @Test fun paginationAppendsAfterSupplementsAndKeepsOldSectionPositions() = runTest {
        val repo = repository({ HomeBatch(listOf(Shelf("Main")), "next") }, extras = listOf({ listOf(Shelf("Extra")) }),
            more = { _, _ -> HomeBatch(listOf(Shelf("Main"), Shelf("More")), null) })
        repo.load("one", false); runCurrent(); val first = repo.state.value.sections
        repo.loadMore(); runCurrent()
        assertEquals(first, repo.state.value.sections.take(first.size))
        assertEquals(listOf("Main", "Extra", "More"), repo.state.value.sections.map { it.title })
    }

    @Test fun preparedContinuationsCommitTogetherAheadOfGlobalSupplements() = runTest {
        val page = CompletableDeferred<HomeBatch<Shelf>>()
        val repo = repository({ HomeBatch(listOf(Shelf("Personal")), "one") },
            extras = listOf({ listOf(Shelf("Global")) }), more = { _, _ -> page.await() },
            preparedPages = 1, preparedBeforeExtras = true)
        repo.load("one", false); runCurrent()
        assertFalse(repo.state.value.ready); assertTrue(repo.state.value.sections.isEmpty())
        page.complete(HomeBatch(listOf(Shelf("Made for you")), null)); runCurrent()
        assertEquals(listOf("Personal", "Made for you", "Global"), repo.state.value.sections.map { it.title })
        assertNull(repo.loadMore())
    }

    @Test fun prefetchStopsAtItsBudgetAndRetainsCompletedPagesOnTimeout() = runTest {
        var calls = 0
        val repo = repository({ HomeBatch(listOf(Shelf("Main")), "first") },
            more = { _, token -> calls++; if (token == "first") HomeBatch(listOf(Shelf("Ready")), "slow") else awaitCancellation() },
            preparedPages = 2)
        repo.load("one", false); runCurrent(); assertFalse(repo.state.value.ready)
        advanceTimeBy(2_501); runCurrent()
        assertEquals(listOf("Main", "Ready"), repo.state.value.sections.map { it.title })
        assertEquals(2, calls); assertEquals(0, repo.state.value.pending)
    }

    @Test fun boundedPrefetchDoesNotLoopAndSavesItsPreparedTail() = runTest {
        var calls = 0; var persisted: HomeSnapshot<Shelf>? = null
        val repo = repository({ HomeBatch(listOf(Shelf("Main")), "loop") },
            more = { _, _ -> calls++; HomeBatch(listOf(Shelf("Ready")), "loop") },
            save = { _, value, _ -> persisted = value }, preparedPages = 3)
        repo.load("one", false); runCurrent()
        assertEquals(1, calls); assertEquals(listOf(Shelf("Ready")), persisted!!.tail)
        assertNull(repo.loadMore())
    }

    @Test fun freshDiskSnapshotNeedsNoNetworkUntilExplicitRefresh() = runTest {
        var calls = 0
        val repo = repository({ calls++; HomeBatch(listOf(Shelf("Refreshed")), null) },
            read = { HomeSnapshot(listOf(Shelf("Ready")), null, emptyList(), 89_999) })
        repo.load("one", false); runCurrent()
        assertEquals(0, calls); assertEquals("Ready", repo.state.value.sections.single().title)
        repo.load("one", false, force = true); runCurrent()
        assertEquals(1, calls); assertEquals("Refreshed", repo.state.value.sections.single().title)
    }

}
