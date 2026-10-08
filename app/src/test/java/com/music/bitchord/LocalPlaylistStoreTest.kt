package com.music.bitchord

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.spotify.LocalPlaylist
import com.music.bitchord.data.spotify.LocalPlaylistCollection
import com.music.bitchord.data.spotify.LocalPlaylistSnapshotCodec
import com.music.bitchord.data.spotify.LocalPlaylistSnapshotWriter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
class LocalPlaylistStoreTest {
    private fun song(id: String) = Song(id, "Song $id", "Artist", "https://image/$id")

    @Test
    fun `new playlist is visible immediately without waiting for JSON or disk`() = runTest {
        val writes = mutableListOf<String>()
        val writer = LocalPlaylistSnapshotWriter(backgroundScope) {
            writes += LocalPlaylistSnapshotCodec.encode(it)
        }
        val collection = LocalPlaylistCollection(writer::enqueue)
        val created = collection.savePlaylist("Road", listOf(song("one")))
        assertEquals(created, collection.playlists.value.single())
        assertTrue(writes.isEmpty())
        runCurrent()
        assertEquals(collection.playlists.value, LocalPlaylistSnapshotCodec.decode(writes.single()))
    }

    @Test
    fun `slow writes remain serial and a burst keeps only the latest pending snapshot`() = runTest {
        val firstWrite = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val written = mutableListOf<String>()
        var active = 0
        var maxActive = 0
        val writer = LocalPlaylistSnapshotWriter(backgroundScope) { snapshot ->
            active++
            maxActive = maxOf(maxActive, active)
            if (written.isEmpty()) {
                firstWrite.complete(Unit)
                releaseFirst.await()
            }
            written += LocalPlaylistSnapshotCodec.encode(snapshot)
            active--
        }
        val collection = LocalPlaylistCollection(writer::enqueue)
        val original = collection.savePlaylist("Original", listOf(song("one")))
        runCurrent()
        assertTrue(firstWrite.isCompleted)
        repeat(100) { collection.renamePlaylist(original.id, "Edit $it") }
        collection.deletePlaylist(original.browseId)
        val final = collection.savePlaylist("Latest", listOf(song("two")))
        assertEquals(final, collection.playlists.value.single())
        releaseFirst.complete(Unit)
        runCurrent()

        assertEquals(1, maxActive)
        assertEquals(2, written.size)
        assertEquals("Original", LocalPlaylistSnapshotCodec.decode(written.first()).single().title)
        assertEquals(listOf(final), LocalPlaylistSnapshotCodec.decode(written.last()))
    }

    @Test
    fun `a failed snapshot does not prevent a later edit from being saved`() = runTest {
        var attempted = 0
        val written = mutableListOf<String>()
        val writer = LocalPlaylistSnapshotWriter(backgroundScope) { snapshot ->
            attempted++
            if (attempted == 1) error("Disk temporarily unavailable")
            written += LocalPlaylistSnapshotCodec.encode(snapshot)
        }
        val collection = LocalPlaylistCollection(writer::enqueue)
        val created = collection.savePlaylist("Before", listOf(song("one")))
        runCurrent()
        collection.renamePlaylist(created.id, "After")
        runCurrent()
        assertEquals(2, attempted)
        assertEquals("After", LocalPlaylistSnapshotCodec.decode(written.single()).single().title)
    }

    @Test
    fun `mutating a caller song list cannot change an already queued snapshot`() = runTest {
        val writes = mutableListOf<String>()
        val writer = LocalPlaylistSnapshotWriter(backgroundScope) {
            writes += LocalPlaylistSnapshotCodec.encode(it)
        }
        val collection = LocalPlaylistCollection(writer::enqueue)
        val incoming = mutableListOf(song("one"))
        val created = collection.savePlaylist("Road", incoming)
        incoming.clear()
        incoming += song("other")
        runCurrent()
        assertEquals(listOf("one"), created.songs.map { it.videoId })
        assertEquals(listOf("one"), LocalPlaylistSnapshotCodec.decode(writes.single()).single().songs.map { it.videoId })
    }

    @Test
    fun `IO persistence runs away from the caller thread`() = runTest {
        val caller = Thread.currentThread()
        val persistedOn = CompletableDeferred<Thread>()
        val scope = CoroutineScope(backgroundScope.coroutineContext + Dispatchers.IO)
        val writer = LocalPlaylistSnapshotWriter(scope) { snapshot ->
            LocalPlaylistSnapshotCodec.encode(snapshot)
            persistedOn.complete(Thread.currentThread())
        }
        val collection = LocalPlaylistCollection(writer::enqueue)
        collection.savePlaylist("Road", listOf(song("one")))
        assertTrue(persistedOn.await() !== caller)
    }

    @Test
    fun `simultaneous creations retain every playlist with unique UUID IDs`() {
        val collection = LocalPlaylistCollection { }
        val workers = Executors.newFixedThreadPool(4)
        try {
            val ids = workers.invokeAll((0 until 100).map { index ->
                Callable { collection.savePlaylist("Playlist $index", listOf(song("$index"))).id }
            }).map { it.get() }
            assertEquals(100, ids.toSet().size)
            assertEquals(100, collection.playlists.value.size)
            ids.forEach { UUID.fromString(it.removePrefix("sp_local_")) }
        } finally {
            workers.shutdownNow()
        }
    }

    @Test
    fun `timestamp IDs and nullable metadata from existing saved playlists still load`() {
        val raw = """[{"id":"sp_local_1723000000000","title":"Old road","songs":[
            {"videoId":"same","title":"First","artist":"Artist","thumbnailUrl":"null","isVideo":true},
            {"videoId":"same","title":"First","artist":"Artist","thumbnailUrl":null,"albumName":"", "durationText":"2:23"}
        ]}]"""
        val old = LocalPlaylistSnapshotCodec.decode(raw).single()
        val collection = LocalPlaylistCollection { }
        collection.restore(listOf(old))
        assertEquals(old, collection.getPlaylist("local:playlist:sp_local_1723000000000"))
        assertNull(old.songs.first().thumbnailUrl)
        assertTrue(old.songs.first().isVideo)
        assertEquals(2, old.songs.size)
        assertEquals(listOf(old), LocalPlaylistSnapshotCodec.decode(LocalPlaylistSnapshotCodec.encode(listOf(old))))
        collection.renamePlaylist(old.browseId, "Renamed")
        assertEquals("Renamed", collection.getPlaylist(old.id)?.title)
        collection.deletePlaylist(old.id)
        assertTrue(collection.playlists.value.isEmpty())
    }

    @Test
    fun `confirmed duplicate appends a separate local occurrence without foreign entry IDs`() {
        val saved = mutableListOf<List<LocalPlaylist>>()
        val collection = LocalPlaylistCollection { saved += it }
        val original = collection.savePlaylist("Road", listOf(song("one")))
        assertTrue(collection.addSong(original.browseId, song("one").copy(setVideoId = "remote-entry")))
        val updated = collection.getPlaylist(original.id)!!
        assertEquals(listOf("one", "one"), updated.songs.map { it.videoId })
        assertNull(updated.songs.last().setVideoId)
        assertEquals(1, original.songs.size)
        assertEquals(2, saved.size)
        assertFalse(collection.addSong("missing", song("two")))
        assertEquals(2, saved.size)
    }
}
