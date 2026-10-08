package com.music.bitchord

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.ui.executePlaylistAdd
import com.music.bitchord.ui.planPlaylistAdd
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistAddPlanTest {
    private val song = Song("selected-video", "Same name", "Artist", null)
    private fun playlist(id: String) = UserPlaylist(id, id, "", null)

    @Test
    fun `checking a mixed batch writes nothing and names just the duplicate playlists`() = runBlocking {
        val checked = mutableListOf<String>()
        val plan = planPlaylistAdd(listOf(playlist("first"), playlist("second"), playlist("first")), song, "account") { target, _ ->
            checked += target.playlistId
            target.playlistId == "first"
        }
        assertEquals(listOf("first", "second"), checked)
        assertEquals(listOf("first"), plan.duplicates.map { it.playlistId })
        assertEquals(2, plan.playlists.size)
    }

    @Test
    fun `duplicate plans cannot write without explicit confirmation`() = runBlocking {
        val plan = planPlaylistAdd(listOf(playlist("duplicate")), song, null) { _, _ -> true }
        var writes = 0
        val result = executePlaylistAdd(plan, allowDuplicates = false) { _, _ -> writes++; true }
        assertEquals(0, writes)
        assertEquals(0, result.added)
    }

    @Test
    fun `add anyway preserves duplicates and adds nonduplicate destinations in order`() = runBlocking {
        val plan = planPlaylistAdd(listOf(playlist("existing"), playlist("new")), song, null) { target, _ -> target.playlistId == "existing" }
        val writes = mutableListOf<Pair<String, String>>()
        val result = executePlaylistAdd(plan, allowDuplicates = true) { target, track ->
            writes += target.playlistId to track.videoId
            true
        }
        assertEquals(listOf("existing" to "selected-video", "new" to "selected-video"), writes)
        assertEquals(2, result.added)
        assertEquals(0, result.failed)
    }

    @Test
    fun `different videos with identical metadata are different tracks`() = runBlocking {
        val sameTitleDifferentVideo = song.copy(videoId = "alternate-upload")
        val plan = planPlaylistAdd(listOf(playlist("playlist")), song, null) { _, track ->
            listOf(sameTitleDifferentVideo).any { it.videoId == track.videoId }
        }
        assertTrue(plan.duplicates.isEmpty())
    }

    @Test
    fun `failed checks do not produce a writable partial plan`() = runBlocking {
        var failed = false
        try {
            planPlaylistAdd(listOf(playlist("checked"), playlist("unavailable")), song, null) { target, _ ->
                if (target.playlistId == "unavailable") error("No connection")
                false
            }
        } catch (_: IllegalStateException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun `batch write reports failures without skipping remaining destinations`() = runBlocking {
        val plan = planPlaylistAdd(listOf(playlist("first"), playlist("failed"), playlist("last")), song, null) { _, _ -> false }
        val writes = mutableListOf<String>()
        val result = executePlaylistAdd(plan, false) { target, _ ->
            writes += target.playlistId
            if (target.playlistId == "failed") error("Rate limited")
            true
        }
        assertEquals(listOf("first", "failed", "last"), writes)
        assertEquals(2, result.added)
        assertEquals(1, result.failed)
    }

    @Test
    fun `cancellation does not turn into a failed write or continue the batch`() = runBlocking {
        val plan = planPlaylistAdd(listOf(playlist("first"), playlist("second")), song, null) { _, _ -> false }
        var writes = 0
        var cancelled = false
        try {
            executePlaylistAdd(plan, false) { _, _ -> writes++; throw CancellationException("Cancelled") }
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertEquals(1, writes)
    }
}
