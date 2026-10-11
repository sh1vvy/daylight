/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.library

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RefreshRequestsTest {
    @Test fun concurrentReadersShareOneFetchAndCancellingOneScreenDoesNotCancelOthers() = runTest {
        val repositoryScope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val gate = RefreshRequests<String, String>(repositoryScope)
            val ready = CompletableDeferred<String>()
            var calls = 0
            val readers = (0 until 20).map { async { gate.run("profile") { calls++; ready.await() } } }
            runCurrent()
            readers.first().cancel()
            ready.complete("rows")
            readers.drop(1).forEach { assertEquals("rows", it.await()) }
            assertEquals(1, calls)
            assertEquals("new rows", gate.run("profile") { calls++; "new rows" })
            assertEquals(2, calls)
        } finally { repositoryScope.cancel() }
    }

    @Test fun switchingAccountsCancelsObsoleteFetchAndTheNextRequestUsesItsOwnLoader() = runTest {
        val repositoryScope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val gate = RefreshRequests<String, String>(repositoryScope)
            val blocked = CompletableDeferred<String>()
            val old = async { gate.run("one") { blocked.await() } }
            runCurrent()
            gate.cancelAll()
            runCurrent()
            assertTrue(old.isCancelled)
            assertEquals("two", gate.run("two") { "two" })
        } finally { repositoryScope.cancel() }
    }

    @Test fun failedSectionsKeepCachedCardsButVerifiedEmptySectionsActuallyClearThem() {
        val card = LibraryCard("Saved", "Artist", "cover", "album", null)
        val original = listOf(LibrarySection("Albums", listOf(card)), LibrarySection("Playlists", listOf(card)))
        assertEquals(original, mergeLibrarySections(original, emptyList(), emptySet()))
        assertEquals(listOf(original.last()), mergeLibrarySections(original, emptyList(), setOf("Albums")))
        val refreshed = LibrarySection("Playlists", listOf(card.copy(label = "New")))
        assertEquals(listOf(refreshed, original.first()), mergeLibrarySections(original, listOf(refreshed), setOf("Playlists")))
    }
}
