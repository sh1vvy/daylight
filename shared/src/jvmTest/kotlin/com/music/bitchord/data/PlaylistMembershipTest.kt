package com.music.bitchord.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PlaylistMembershipTest {
    @Test
    fun findingTheSongOnTheFirstPageDoesNotFetchTheRemainingPlaylist() = runBlocking {
        assertTrue(
            scanPlaylistMembership(
                "wanted",
                first = { PlaylistMembershipPage(listOf("other", "wanted"), "unused-next-page") },
                next = { error("Membership was already established; no further request should be made") },
            ),
        )
    }

    @Test
    fun aSongOnALaterPageStopsBeforeTheNextContinuation() = runBlocking {
        val requested = mutableListOf<String>()
        assertTrue(
            scanPlaylistMembership(
                "wanted",
                first = { PlaylistMembershipPage(emptyList(), "page-two") },
                next = { token ->
                    requested += token
                    when (token) {
                        "page-two" -> PlaylistMembershipPage(listOf("wanted"), "page-three")
                        else -> error("An already-present song must not require another page")
                    }
                },
            ),
        )
        assertEquals(listOf("page-two"), requested)
    }

    @Test
    fun absenceIsEstablishedOnlyAfterAllContinuationPagesEnd() = runBlocking {
        val requested = mutableListOf<String>()
        assertFalse(
            scanPlaylistMembership(
                "wanted",
                first = { PlaylistMembershipPage(listOf("first-song"), "page-two") },
                next = { token ->
                    requested += token
                    when (token) {
                        "page-two" -> PlaylistMembershipPage(emptyList(), "page-three")
                        "page-three" -> PlaylistMembershipPage(listOf("last-song"), null)
                        else -> error("Unexpected continuation")
                    }
                },
            ),
        )
        assertEquals(listOf("page-two", "page-three"), requested)
    }

    @Test
    fun anInitialReadFailureDoesNotBecomeAnAbsentSong() = runBlocking {
        val failure = IllegalStateException("Playlist is unavailable")
        val observed = assertFailsWith<IllegalStateException> {
            scanPlaylistMembership(
                "wanted",
                first = { throw failure },
                next = { error("No continuation is available") },
            )
        }
        assertSame(failure, observed)
    }

    @Test
    fun aContinuationReadFailureDoesNotOfferToAddAnExistingSongAgain() = runBlocking {
        val failure = IllegalStateException("Continuation request failed")
        val observed = assertFailsWith<IllegalStateException> {
            scanPlaylistMembership(
                "wanted",
                first = { PlaylistMembershipPage(listOf("other"), "unread-page") },
                next = { throw failure },
            )
        }
        assertSame(failure, observed)
    }

    @Test
    fun aRepeatedContinuationCannotLoopOrReportFalseAbsence() = runBlocking {
        var requests = 0
        assertFailsWith<IllegalStateException> {
            scanPlaylistMembership(
                "wanted",
                first = { PlaylistMembershipPage(emptyList(), "looping-page") },
                next = {
                    requests++
                    PlaylistMembershipPage(emptyList(), "looping-page")
                },
            )
        }
        assertEquals(1, requests)
    }

    @Test
    fun reachingThePageSafetyLimitIsIncompleteRatherThanAbsent() = runBlocking {
        var requests = 0
        assertFailsWith<IllegalStateException> {
            scanPlaylistMembership(
                "wanted",
                first = { PlaylistMembershipPage(emptyList(), "page-two") },
                next = {
                    requests++
                    PlaylistMembershipPage(emptyList(), "page-three")
                },
                maxPages = 2,
            )
        }
        assertEquals(1, requests)
    }

    @Test
    fun cancellingAnInFlightPageDoesNotReturnAnAbsentResult() = runBlocking {
        val pageStarted = CompletableDeferred<Unit>()
        var returnedResult = false
        var pageCancelled = false
        val scan = launch(start = CoroutineStart.UNDISPATCHED) {
            scanPlaylistMembership(
                "wanted",
                first = { PlaylistMembershipPage(emptyList(), "waiting-page") },
                next = {
                    pageStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        pageCancelled = true
                    }
                },
            )
            returnedResult = true
        }
        pageStarted.await()
        scan.cancelAndJoin()
        assertTrue(scan.isCancelled)
        assertTrue(pageCancelled)
        assertFalse(returnedResult)
    }

    @Test
    fun cancellationFromAProviderIsPreserved() = runBlocking {
        val cancellation = CancellationException("Playlist chooser was closed")
        val observed = assertFailsWith<CancellationException> {
            scanPlaylistMembership(
                "wanted",
                first = { PlaylistMembershipPage(emptyList(), "page-two") },
                next = { throw cancellation },
            )
        }
        assertSame(cancellation, observed)
    }
}
