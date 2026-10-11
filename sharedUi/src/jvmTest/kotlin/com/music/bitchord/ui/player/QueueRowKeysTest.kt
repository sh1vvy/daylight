/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.ui.player

import com.music.bitchord.data.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class QueueRowKeysTest {
    private fun track(video: String, entry: String? = null) =
        Song(video, video, "Artist", null, queueEntryId = entry)

    @Test fun repeatedRestoredEntryIdsKeepEveryRequestVisible() {
        val request = track("one", "restored-request")
        val queue = listOf(request, request, track("other", "restored-request"))
        assertEquals(queue.size, queueRowKeys(queue).toSet().size)
    }

    @Test fun uniqueEntriesKeepTheirIdentityWhenReorderedOrAudioVersionChanges() {
        val first = track("one", "first")
        val second = track("one", "second")
        val original = queueRowKeys(listOf(first, second))
        assertNotEquals(original[0], original[1])
        assertEquals(original.reversed(), queueRowKeys(listOf(second, first.copy(videoId = "audio-version"))))
    }

    @Test fun legacyAndAssignedIdentitiesCannotCollide() {
        val queue = listOf(track("same"), track("same"), track("same", "same"), track("other", "same"))
        assertEquals(queue.size, queueRowKeys(queue).toSet().size)
        assertEquals(queueRowKeys(queue), queueRowKeys(queue.toList()))
    }
}
