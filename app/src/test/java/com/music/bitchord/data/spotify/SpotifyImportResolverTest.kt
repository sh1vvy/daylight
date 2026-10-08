package com.music.bitchord.data.spotify

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SpotifyImportResolverTest {
    @Test
    fun repeatedTracksShareSearchesAndPreservePlaylistOrderAndOccurrences() = runBlocking {
        val a = SpotifyImportTrack("First", "Artist")
        val b = SpotifyImportTrack("Second", "Other")
        val calls = AtomicInteger()
        val progress = mutableListOf<Int>()
        val (songs, missed) = resolveSpotifyImportTracks(
            listOf(a, b, a.copy(title = " first "), b),
            onProgress = { done, total -> assertEquals(4, total); progress += done },
        ) { track ->
            calls.incrementAndGet()
            if (track == a) delay(15)
            Song(videoId = track.title, title = track.title, artist = track.artist, thumbnailUrl = null, fromAutoplay = false)
        }
        assertEquals(2, calls.get())
        assertEquals(listOf("First", "Second", "First", "Second"), songs.map { it.videoId })
        assertTrue(missed.isEmpty())
        assertEquals(listOf(2, 4), progress)
    }

    @Test
    fun largeImportsLimitActiveWorkAndReportUnmatchedTracksInSourceOrder() = runBlocking {
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val tracks = (0 until 300).map { SpotifyImportTrack(it.toString(), "Artist") }
        val progress = mutableListOf<Int>()
        val (songs, missed) = resolveSpotifyImportTracks(tracks, { done, _ -> progress += done }) { track ->
            val count = active.incrementAndGet()
            maximum.updateAndGet { maxOf(it, count) }
            try {
                delay(1)
                if (track.title.toInt() % 3 == 0) null
                else Song(videoId = track.title, title = track.title, artist = track.artist, thumbnailUrl = null, fromAutoplay = false)
            } finally {
                active.decrementAndGet()
            }
        }
        assertTrue(maximum.get() <= 4)
        assertEquals(tracks.filter { it.title.toInt() % 3 != 0 }.map { it.title }, songs.map { it.videoId })
        assertEquals(tracks.filter { it.title.toInt() % 3 == 0 }, missed)
        assertEquals((1..300).toList(), progress)
    }

    @Test(expected = CancellationException::class)
    fun cancelledImportsDoNotReturnAPartialSuccess() = runBlocking {
        resolveSpotifyImportTracks(listOf(SpotifyImportTrack("Cancelled", "Artist")), { _, _ -> }) {
            throw CancellationException("Import dismissed")
        }
        Unit
    }
}
