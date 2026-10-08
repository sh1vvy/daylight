package com.music.bitchord.playback

import com.music.bitchord.data.listentogether.PartyTrack
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PartyCollectionQueueTest {
    private val source = QueueSource("Our playlist", PlaybackSourceType.BROWSE, "playlist-123")

    private fun song(id: String, tier: QueueTier = QueueTier.CONTEXT, entryId: String? = null) = Song(
        videoId = id,
        title = "Song $id",
        artist = "Artist",
        thumbnailUrl = null,
        queueTier = tier,
        queueEntryId = entryId,
    )

    @Test
    fun `collection play puts all following songs into the publishable shared queue`() {
        val songs = List(6) { song("track-$it") }
        val result = QueueCoordinator.buildPartyCollectionQueue(songs, 0, source, emptyList(), false)

        assertEquals(songs.map { it.videoId }, result.timeline.map { it.videoId })
        assertEquals(0, result.omittedSongs)
        assertEquals(QueueTier.CONTEXT, result.timeline.first().queueTier)
        assertTrue(result.timeline.drop(1).all { it.queueTier == QueueTier.USER_QUEUE })
        assertTrue(result.timeline.all { it.playbackSource == source.title && it.playbackSourceId == source.id })

        // Exercise the actual publisher's filtering and the wire format a second listener receives.
        val published = partyPublishQueueIndices(result.timeline.map { it.videoId }, 0) {
            result.timeline[it].queueTier
        }.map { result.timeline[it].toPartyTrack(0L) }
        val serializer = ListSerializer(PartyTrack.serializer())
        val received = Json.decodeFromString(serializer, Json.encodeToString(serializer, published))
        assertEquals(songs.map { it.videoId }, received.map { it.toSong().videoId })
        assertTrue(received.all { !it.fromAutoplay })
    }

    @Test
    fun `existing requests stay ahead of playlist and stale autoplay is discarded`() {
        val result = QueueCoordinator.buildPartyCollectionQueue(
            listOf(song("first"), song("second"), song("third")), 0, source,
            listOf(
                PartyTrack("request-1"),
                PartyTrack("old-auto", fromAutoplay = true),
                PartyTrack("request-2"),
            ), false,
        )
        assertEquals(listOf("first", "request-1", "request-2", "second", "third"), result.timeline.map { it.videoId })
        assertEquals(0, result.omittedSongs)
    }

    @Test
    fun `shuffle includes songs before its randomly selected starter without shuffling requests`() {
        val songs = List(10) { song("track-$it") }
        val result = QueueTimeline.buildPartyCollectionQueue(
            songs, 7, source,
            listOf(song("request-1", QueueTier.USER_QUEUE), song("request-2", QueueTier.USER_QUEUE)),
            shuffle = true, random = Random(42),
        )
        assertEquals(listOf("track-7", "request-1", "request-2"), result.timeline.take(3).map { it.videoId })
        val shuffled = result.timeline.drop(3).map { it.videoId }
        assertEquals(songs.filterIndexed { i, _ -> i != 7 }.map { it.videoId }.toSet(), shuffled.toSet())
        assertEquals(9, shuffled.size)
        assertFalse(shuffled == songs.filterIndexed { i, _ -> i != 7 }.map { it.videoId })
        assertEquals(0, result.omittedSongs)
        // The player's second shuffle pass keeps these deliberate shared entries pinned.
        assertEquals(result.timeline, QueueTimeline.shuffledStartingOrder(result.timeline, 0))
    }

    @Test
    fun `queue limit accounts for preserved manual requests and reports omitted playlist songs`() {
        val result = QueueTimeline.buildPartyCollectionQueue(
            List(100) { song("track-$it") }, 0, source,
            listOf(song("request-1", QueueTier.USER_QUEUE), song("request-2", QueueTier.USER_QUEUE)),
        )
        assertEquals(26, result.timeline.size)
        assertEquals(listOf("track-0", "request-1", "request-2", "track-1"), result.timeline.take(4).map { it.videoId })
        assertEquals("track-23", result.timeline.last().videoId)
        assertEquals(76, result.omittedSongs)
    }

    @Test
    fun `full queue retains everyone's requests and reports that playlist tail did not fit`() {
        val requests = List(25) { song("request-$it", QueueTier.USER_QUEUE, "entry-$it") }
        val result = QueueTimeline.buildPartyCollectionQueue(
            listOf(song("first"), song("second"), song("third")), 0, source, requests,
        )
        assertEquals(listOf("first") + requests.map { it.videoId }, result.timeline.map { it.videoId })
        assertEquals(requests.map { it.queueEntryId }, result.timeline.drop(1).map { it.queueEntryId })
        assertEquals(2, result.omittedSongs)
    }

    @Test
    fun `duplicate playlist entries are preserved with separate queue identities`() {
        val repeated = song("same")
        val result = QueueTimeline.buildPartyCollectionQueue(
            listOf(repeated, song("other"), repeated), 0, source, emptyList(),
        )
        assertEquals(listOf("same", "other", "same"), result.timeline.map { it.videoId })
        result.timeline.forEach { assertNotNull(it.queueEntryId) }
        assertEquals(3, result.timeline.map { it.queueEntryId }.toSet().size)
    }

    @Test
    fun `device files are excluded but downloaded catalogue IDs remain shareable`() {
        val result = QueueTimeline.buildPartyCollectionQueue(
            listOf(song("first"), song("content://media/123"), song("file:///music/song.mp3"), song("downloaded-id")),
            0, source, listOf(song("file:///private/request.mp3", QueueTier.USER_QUEUE)),
        )
        assertEquals(listOf("first", "downloaded-id"), result.timeline.map { it.videoId })
        assertEquals(0, result.omittedSongs)
    }

    @Test
    fun `empty or device-only collection returns no replacement shared queue`() {
        assertTrue(QueueTimeline.buildPartyCollectionQueue(emptyList(), 0, source, emptyList()).timeline.isEmpty())
        assertTrue(QueueTimeline.buildPartyCollectionQueue(listOf(song("content://media/123")), 0, source, emptyList()).timeline.isEmpty())
    }

    @Test
    fun `ordinary play from a chosen index adds only the following playlist entries`() {
        val result = QueueTimeline.buildPartyCollectionQueue(
            List(5) { song("track-$it") }, 2, source, emptyList(),
        )
        assertEquals(listOf("track-2", "track-3", "track-4"), result.timeline.map { it.videoId })
    }

    @Test
    fun `publisher uses current occurrence for repeats while excluding unshared context and files`() {
        val ids = listOf("same", "previous", "same", "queued", "private-context", "file:///local.mp3", "auto")
        val tiers = listOf(QueueTier.CONTEXT, QueueTier.USER_QUEUE, QueueTier.CONTEXT, QueueTier.USER_QUEUE,
            QueueTier.CONTEXT, QueueTier.USER_QUEUE, QueueTier.AUTOPLAY)
        val indices = partyPublishQueueIndices(ids, 2) { tiers[it] }
        assertEquals(listOf(0, 1, 2, 3, 6), indices)
        assertEquals(2, indices.indexOf(2))
    }
}
