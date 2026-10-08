package com.music.bitchord

import com.music.bitchord.data.library.PlaylistCreationOrder
import com.music.bitchord.data.library.playlistActivityHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class PlaylistCreationOrderTest {
    @Test
    fun `new creates persist newest first without treating metadata refresh as a create`() {
        val snapshots = mutableListOf<List<String>>()
        val order = PlaylistCreationOrder { snapshots += it }
        order.restore(listOf("VLOLD"))
        order.recordCreated("FIRST")
        order.recordCreated("VLSECOND")
        order.recordCreated("VLFIRST")
        assertEquals(listOf("SECOND", "FIRST", "OLD"), order.createdPlaylistIds.value)
        assertEquals(2, snapshots.size)
        assertEquals(order.createdPlaylistIds.value, snapshots.last())
    }

    @Test
    fun `restoring normalizes remote ids but retains exact local identities`() {
        val order = PlaylistCreationOrder { }
        order.restore(listOf("VLREMOTE", "REMOTE", "local:playlist:one", " ", " REMOTE "))
        assertEquals(listOf("REMOTE", "local:playlist:one"), order.createdPlaylistIds.value)
        order.recordCreated("   ")
        assertEquals(2, order.createdPlaylistIds.value.size)
    }

    @Test
    fun `creation before initialization remains newer than disk and is persisted after restore`() {
        val snapshots = mutableListOf<List<String>>()
        val order = PlaylistCreationOrder { snapshots += it }
        order.recordCreated("LATEST")
        order.restore(listOf("OLD", "VLVERYOLD"))
        assertEquals(listOf("LATEST", "OLD", "VERYOLD"), snapshots.last())
        val restarted = PlaylistCreationOrder { }
        restarted.restore(snapshots.last())
        assertEquals(order.createdPlaylistIds.value, restarted.createdPlaylistIds.value)
    }

    @Test
    fun `simultaneous creates retain all playlists and each creator's order`() {
        val order = PlaylistCreationOrder { }
        val workers = Executors.newFixedThreadPool(4)
        try {
            workers.invokeAll((0 until 4).map { worker ->
                Callable {
                    repeat(25) { index -> order.recordCreated("$worker:$index") }
                }
            }).forEach { it.get() }
            val ids = order.createdPlaylistIds.value
            assertEquals(100, ids.size)
            assertEquals(100, ids.toSet().size)
            repeat(4) { worker ->
                repeat(24) { index ->
                    assertTrue(ids.indexOf("$worker:${index + 1}") < ids.indexOf("$worker:$index"))
                }
            }
        } finally {
            workers.shutdownNow()
        }
    }

    @Test
    fun `adding to an existing playlist promotes it and persists its new position`() {
        val snapshots = mutableListOf<List<String>>()
        val order = PlaylistCreationOrder { snapshots += it }
        order.restore(listOf("NEWEST", "OLDER", "local:playlist:local"))
        order.recordActivity("VLOLDER")
        assertEquals(listOf("OLDER", "NEWEST", "local:playlist:local"), order.createdPlaylistIds.value)
        order.recordActivity("local:playlist:local")
        assertEquals(listOf("local:playlist:local", "OLDER", "NEWEST"), order.createdPlaylistIds.value)
        order.recordActivity("VLOLDER")
        assertEquals(listOf("OLDER", "local:playlist:local", "NEWEST"), snapshots.last())
        val restarted = PlaylistCreationOrder { }
        restarted.restore(snapshots.last())
        assertEquals(order.createdPlaylistIds.value, restarted.createdPlaylistIds.value)
    }

    @Test
    fun `repeated writes to the newest playlist do not duplicate it or rewrite an identical order`() {
        val snapshots = mutableListOf<List<String>>()
        val order = PlaylistCreationOrder { snapshots += it }
        order.restore(listOf("OTHER"))
        order.recordActivity("KNOWN")
        order.recordActivity("VLKNOWN")
        order.recordActivity("   ")
        assertEquals(listOf("KNOWN", "OTHER"), order.createdPlaylistIds.value)
        assertEquals(1, snapshots.size)
    }

    @Test
    fun `activity history migrates earlier creation preferences without losing their order`() {
        val legacy = """["SECOND","FIRST","local:playlist:old"]"""
        assertEquals(listOf("SECOND", "FIRST", "local:playlist:old"), playlistActivityHistory(null, legacy))
        assertEquals(listOf("MODIFIED", "SECOND"), playlistActivityHistory("""["MODIFIED","SECOND"]""", legacy))
        assertEquals(listOf("SECOND", "FIRST", "local:playlist:old"), playlistActivityHistory("bad json", legacy))
        assertEquals(emptyList<String>(), playlistActivityHistory(null, null))
    }

    @Test
    fun `a successful addition before initialization wins over disk history`() {
        val snapshots = mutableListOf<List<String>>()
        val order = PlaylistCreationOrder { snapshots += it }
        order.recordActivity("MODIFIED")
        order.restore(listOf("OLD", "VLMODIFIED", "ANOTHER"))
        assertEquals(listOf("MODIFIED", "OLD", "ANOTHER"), snapshots.last())
    }
}
