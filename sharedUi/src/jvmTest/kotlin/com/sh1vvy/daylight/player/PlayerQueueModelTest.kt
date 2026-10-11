/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-12 through 2026-10-12.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.player

import kotlin.test.*

class PlayerQueueModelTest {
    private fun track(id: String, key: String = id, lane: PlayerQueueLane = PlayerQueueLane.COLLECTION) =
        PlayerTrack(id, id, "Artist", null, key, lane)

    @Test fun onlyFutureOccurrencesAppearWhileAbsoluteTimelineIndicesRemainIntact() {
        val queue = listOf(track("past"), track("playing"), track("next"), track("playing", "future-occurrence"))
        val upcoming = upcomingTracks(queue, 1)
        assertEquals(listOf("next", "playing"), upcoming.map { it.track.id })
        assertEquals(listOf(2, 3), upcoming.map { it.index })
    }
    @Test fun missingAndExhaustedCurrentIndicesDoNotCrashOrInventRows() {
        val queue = listOf(track("a"), track("b"))
        assertEquals(2, upcomingTracks(queue, -1).size)
        assertEquals(2, upcomingTracks(queue, Int.MIN_VALUE).size)
        assertTrue(upcomingTracks(queue, Int.MAX_VALUE).isEmpty())
        assertTrue(upcomingTracks(queue, 1).isEmpty())
        assertTrue(upcomingTracks(emptyList(), 0).isEmpty())
    }
    @Test fun repeatedEntriesAndSuffixLikeIdsHaveDistinctKeys() {
        val rows = upcomingTracks(listOf(track("a"), track("a"), track("a:1"), track("x", "1:a:0")), -1)
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
        val afterPlaying = upcomingTracks(rows.map { it.track }, 0)
        assertEquals(rows.drop(1).map { it.key }, afterPlaying.map { it.key })
    }
    @Test fun forwardDropSendsOneAbsoluteMoveWithoutChangingTrackMetadata() {
        val original = upcomingTracks(listOf(track("past"), track("current"), track("a"), track("b"), track("c")), 1)
        val preview = previewQueueMove(original, original[0].key, original[2].key)
        assertEquals(listOf("b", "c", "a"), preview.map { it.track.id })
        assertEquals(2 to 4, queueDropMove(original, preview, original[0].key))
        assertEquals(listOf("a", "b", "c"), original.map { it.track.id })
        assertEquals(original.toSet(), preview.toSet())
    }
    @Test fun backwardMoveUsesTheOriginalDestinationAndNoOpDoesNotSendAMove() {
        val original = upcomingTracks(listOf(track("current"), track("a"), track("b"), track("c")), 0)
        val preview = previewQueueMove(original, original[2].key, original[0].key)
        assertEquals(3 to 1, queueDropMove(original, preview, original[2].key))
        assertNull(queueDropMove(original, original, original[0].key))
        assertSame(original, previewQueueMove(original, "unknown", original[0].key))
    }
    @Test fun manualCollectionAndAutoplayOrderCannotBeCrossedByDragging() {
        val original = upcomingTracks(listOf(track("manual", lane = PlayerQueueLane.ADDED),
            track("collection"), track("radio", lane = PlayerQueueLane.RADIO)), -1)
        assertSame(original, previewQueueMove(original, original[0].key, original[1].key))
        assertSame(original, previewQueueMove(original, original[1].key, original[2].key))
    }
    @Test fun aMembershipChangeCannotCommitAStaleReorder() {
        val original = upcomingTracks(listOf(track("a"), track("b")), -1)
        assertNull(queueDropMove(original, original.dropLast(1), original[0].key))
    }
}
