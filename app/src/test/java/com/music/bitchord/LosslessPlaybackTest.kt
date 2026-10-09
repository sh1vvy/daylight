package com.music.bitchord

import com.music.bitchord.data.lossless.LosslessBetaClient
import com.music.bitchord.data.lossless.flacHeader
import com.music.bitchord.data.sources.SourceStream
import com.music.bitchord.data.sources.TrackMatcher
import com.music.bitchord.playback.LosslessPlayback
import com.music.bitchord.playback.PlaybackFallback
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import okio.Buffer

class LosslessPlaybackTest {
    @Test fun `only enabled unmetered solo audio is eligible`() {
        assertTrue(LosslessPlayback.eligible(true, false, false, false))
        assertFalse(LosslessPlayback.eligible(false, false, false, false))
        assertFalse(LosslessPlayback.eligible(true, true, false, false))
        assertFalse(LosslessPlayback.eligible(true, false, true, false))
        assertFalse(LosslessPlayback.eligible(true, false, false, true))
    }

    @Test fun `each new play has a separate tag but keeps track identity`() {
        val uri = "bitchord://watch?v=123&n=Song"
        val one = LosslessPlayback.tag(uri)
        assertTrue(one.startsWith(uri))
        assertTrue(LosslessPlayback.isTagged(one))
        assertNotEquals(one, LosslessPlayback.tag(uri))
        assertFalse(LosslessPlayback.isTagged(uri))
        assertFalse(LosslessPlayback.isTagged("$uri&lossless_beta="))
    }

    @Test fun `beta stream error escapes to YouTube only once`() {
        val uri = LosslessPlayback.tag("bitchord://watch?v=123&n=Song")
        assertTrue(PlaybackFallback.isAlternative(uri, false))
        val fallback = PlaybackFallback.directYouTubeSourceUri(uri)
        assertFalse(LosslessPlayback.isTagged(fallback))
        assertFalse(PlaybackFallback.isAlternative(fallback, true))
    }

    @Test fun `disabled metered and Jam opens use YouTube without contacting community server`() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            val playback = LosslessPlayback(endpoint = server.url("/").toString())
            for ((index, flags) in listOf(Triple(false, false, false), Triple(true, true, false), Triple(true, false, true)).withIndex()) {
                val result = playback.resolve("play-$index", target(), flags.first, flags.second, flags.third) { SourceStream("https://youtube.example/audio") }
                assertEquals("https://youtube.example/audio", result.url)
            }
            assertEquals(0, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun `miss stays on one YouTube rendition through seeking and settings changes`() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setBody("{\"tracks\":[]}"))
            val playback = LosslessPlayback(endpoint = server.url("/").toString())
            var fallbackCount = 0
            val one = playback.resolve("play-1", target(), true, false, false) {
                fallbackCount++; SourceStream("https://youtube.example/first")
            }
            val seek = playback.resolve("play-1", target(), false, false, false) {
                fallbackCount++; SourceStream("https://youtube.example/different")
            }
            assertEquals(one, seek)
            assertEquals(1, server.requestCount)
            assertEquals(1, fallbackCount)
        } finally { server.shutdown() }
    }

    @Test fun `slow community server yields bounded YouTube fallback`() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(503))
            val playback = LosslessPlayback(LosslessBetaClient(budgetMs = 100), server.url("/").toString())
            val result = playback.resolve("play", target(), true, false, false) { SourceStream("https://youtube.example/audio") }
            assertNull(result.format.isLossless)
            assertEquals("https://youtube.example/audio", result.url)
        } finally { server.shutdown() }
    }

    @Test fun `verified FLAC keeps the same file across seeks even after preference changes`() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"tracks":[{"id":"123","title":"Lover","artistNames":["Taylor Swift"],"duration":221000,"playable":true}]}"""))
            server.enqueue(MockResponse().setBody(Buffer().write(flacHeader(44_100, 24, seconds = 221))))
            val playback = LosslessPlayback(endpoint = server.url("/").toString())
            val first = playback.resolve("play-1", target(), true, false, false) { error("unexpected YouTube fallback") }
            assertEquals("flac", first.format.codec)
            assertEquals(24, first.format.bitDepth)
            assertEquals(LosslessPlayback.SOURCE_ID, first.sourceConfigId)
            val seek = playback.resolve("play-1", target(), false, false, false) { error("changed file mid-stream") }
            assertEquals(first, seek)
            assertEquals(2, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun `joining a Jam during lookup cannot select the verified FLAC`() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"tracks":[{"id":"123","title":"Lover","artistNames":["Taylor Swift"],"duration":221000,"playable":true}]}"""))
            server.enqueue(MockResponse().setBody(Buffer().write(flacHeader(seconds = 221))))
            val playback = LosslessPlayback(endpoint = server.url("/").toString())
            val result = playback.resolve("play", target(), true, false, false, stillEligible = { false }) {
                SourceStream("https://youtube.example/audio")
            }
            assertNull(result.format.codec)
            assertEquals("https://youtube.example/audio", result.url)
        } finally { server.shutdown() }
    }

    private fun target() = TrackMatcher.Target("Lover", "Taylor Swift", 221)
}
