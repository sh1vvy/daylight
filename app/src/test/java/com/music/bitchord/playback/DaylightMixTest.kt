package com.music.bitchord.playback

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.QueueTier
import org.junit.Test
import org.junit.Assert.*

class DaylightMixTest {
    private fun song(id: String, artist: String = "Artist $id") = Song(id, "Track $id", artist, null)
    private val source = song("source").copy(playbackSource = "Play my mix", playbackSourceId = DAYLIGHT_MIX_PREFIX + "test")

    @Test fun `familiar music and discoveries alternate with a bounded tail`() {
        val batch = daylightMixBatch((1..8).map { song("saved-$it") }, (1..8).map { song("fresh-$it") }, emptyList(), emptyList(), 9, source)
        assertEquals(listOf("saved-1", "saved-2", "fresh-1", "saved-3", "saved-4", "fresh-2", "saved-5", "saved-6", "fresh-3"), batch.map { it.videoId })
        assertTrue(batch.all { it.isDaylightMix() && it.queueTier == QueueTier.AUTOPLAY && it.queueEntryId != null })
    }
    @Test fun `played queued and alternate uploads of the same recording are excluded`() {
        val heard = song("heard").copy(title = "Daylight", artist = "Taylor Swift")
        val alternate = heard.copy(videoId = "alternate", title = "Daylight (Official Audio)")
        val batch = daylightMixBatch(listOf(song("saved"), song("queued"), alternate), listOf(heard, alternate, song("new")), listOf(heard), listOf(song("queued")), 10, source)
        assertEquals(listOf("saved", "new"), batch.map { it.videoId })
    }
    @Test fun `offline mix recycles a completed small library without repeating current or upcoming`() {
        val pool = (1..4).map { song("$it") }
        val batch = daylightMixBatch(pool, emptyList(), pool.take(3), listOf(pool[3]), 10, source)
        assertEquals(listOf("1", "2"), batch.map { it.videoId })
    }
    @Test fun `discovery only and saved only pools remain playable`() {
        assertEquals(3, daylightMixBatch(emptyList(), (1..3).map { song("$it") }, emptyList(), emptyList(), 12, source).size)
        assertEquals(3, daylightMixBatch((1..3).map { song("$it") }, emptyList(), emptyList(), emptyList(), 12, source).size)
    }
    @Test fun `local tracks retain their playable uri while Spotify placeholders are excluded`() {
        val local = song("local:one").copy(localUri = "file:///private/music.flac")
        val unresolved = song("sp:one")
        val batch = daylightMixBatch(listOf(local, unresolved), emptyList(), emptyList(), emptyList(), 12, source)
        assertEquals(listOf(local.localUri), batch.map { it.localUri })
    }
    @Test fun `duplicate candidates and saved discoveries cannot repeat in a batch`() {
        val batch = daylightMixBatch(listOf(song("1"), song("1")), listOf(song("1"), song("2"), song("2")), emptyList(), emptyList(), 12, source)
        assertEquals(listOf("1", "2"), batch.map { it.videoId })
    }
    @Test fun `every refill keeps the same session identity and a hard size cap`() {
        val pool = (1..200).map { song("$it") }
        val batch = daylightMixBatch(pool, emptyList(), emptyList(), emptyList(), 200, source)
        assertEquals(20, batch.size)
        assertEquals(1, batch.map { it.playbackSourceId }.distinct().size)
        assertEquals(0, daylightMixBatch(pool, emptyList(), emptyList(), emptyList(), 0, source).size)
    }
    @Test fun `saved music videos keep their identity and playback policy flags`() {
        val favorite = song("favorite-video").copy(isVideo = true, isVideoOrigin = true)
        val batch = daylightMixBatch(listOf(favorite), listOf(song("recommendation-video").copy(isVideo = true)), emptyList(), emptyList(), 12, source)
        assertEquals(listOf("favorite-video"), batch.map { it.videoId })
        assertTrue(batch.single().isVideo && batch.single().isVideoOrigin)
    }
}
