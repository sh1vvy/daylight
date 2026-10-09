package com.music.bitchord

import com.music.bitchord.data.settings.LosslessQuality
import com.music.bitchord.data.lossless.LosslessBetaClient
import com.music.bitchord.data.lossless.flacHeader
import com.music.bitchord.data.NerdStats
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

    @Test fun `quality changes retag queued metadata while removing the previous choice marker`() {
        val original = "bitchord://watch?v=abc&n=Song%20Title&a=Artist&d=210"
        val old = LosslessPlayback.tag(original)
        val next = LosslessPlayback.retag(old, true)
        assertTrue(next.startsWith(original))
        assertNotEquals(old, next)
        assertEquals(1, next.split("lossless_beta=").size - 1)
        assertEquals(original, LosslessPlayback.retag(next, false))
    }

    @Test fun `temporary Jam markers can be removed without discarding recording metadata`() {
        val original = "bitchord://watch?v=abc&n=Song%20Title&d=210"
        val forced = "$original&direct_youtube=1&rendition=original&jam_youtube=1"
        assertEquals(original, LosslessPlayback.withoutParameters(forced, setOf("direct_youtube", "rendition", "jam_youtube")))
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

    @Test fun `queued FLAC uses same separate disk identity when opened and sought`() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"tracks":[{"id":"123","title":"Lover","artistNames":["Taylor Swift"],"duration":221000,"playable":true}]}"""))
            server.enqueue(MockResponse().setBody(Buffer().write(flacHeader(44_100, 24, seconds = 221))))
            val playback = LosslessPlayback(endpoint = server.url("/").toString())
            val uri = LosslessPlayback.tag("bitchord://watch?v=abc&n=Lover")
            val warm = playback.resolve(uri, target(), true, false, false, commit = false) { error("fallback") }
            val key = playback.cacheKey(uri)!!
            assertTrue(key.startsWith("abc#lossless-123-"))
            assertEquals(warm, playback.resolve(uri, target(), true, false, false) { error("second resolve") })
            assertEquals(warm, playback.resolve(uri, target(), false, true, true) { error("seek changed bytes") })
            assertEquals(key, playback.cacheKey(uri))
            assertEquals(2, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun `unopened warm FLAC cannot survive cellular or a lower quality selection`() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            for (key in listOf("cellular", "tier")) {
                server.enqueue(MockResponse().setBody("""{"tracks":[{"id":"123","title":"Lover","artistNames":["Taylor Swift"],"duration":221000,"playable":true}]}"""))
                server.enqueue(MockResponse().setBody(Buffer().write(flacHeader(96_000, 24, seconds = 221))))
                val playback = LosslessPlayback(endpoint = server.url("/").toString())
                playback.resolve(key, target(), true, false, false, commit = false) { error("fallback") }
                if (key == "tier") {
                    server.enqueue(MockResponse().setBody("""{"tracks":[{"id":"123","title":"Lover","artistNames":["Taylor Swift"],"duration":221000,"playable":true}]}"""))
                    server.enqueue(MockResponse().setBody(Buffer().write(flacHeader(96_000, 24, seconds = 221))))
                }
                val result = playback.resolve(key, target(), true, key == "cellular", false,
                    quality = LosslessQuality.LOSSLESS) { SourceStream("https://youtube.example/audio") }
                assertNull(result.format.codec)
                assertNull(playback.cacheKey(key))
            }
        } finally { server.shutdown() }
    }

    @Test fun `an ineligible unopened warm decision is retried after WiFi becomes available`() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            val playback = LosslessPlayback(endpoint = server.url("/").toString())
            playback.resolve("warm", target(), true, true, false, commit = false) { SourceStream("https://youtube.example/audio") }
            server.enqueue(MockResponse().setBody("""{"tracks":[{"id":"123","title":"Lover","artistNames":["Taylor Swift"],"duration":221000,"playable":true}]}"""))
            server.enqueue(MockResponse().setBody(Buffer().write(flacHeader(seconds = 221))))
            assertEquals("flac", playback.resolve("warm", target(), true, false, false) { error("stale fallback") }.format.codec)
        } finally { server.shutdown() }
    }

    private fun target() = TrackMatcher.Target("Lover", "Taylor Swift", 221)

    @Test fun `home row without duration uses original extraction timing before matching`() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"tracks":[{"id":"123","title":"Lover","artistNames":["Taylor Swift"],"duration":221000,"playable":true}]}"""))
            server.enqueue(MockResponse().setBody(Buffer().write(flacHeader(seconds = 221))))
            val statuses = mutableListOf<NerdStats.LosslessBetaStatus>()
            var durationCalls = 0
            val playback = LosslessPlayback(endpoint = server.url("/").toString())
            val result = playback.resolve("home", target().copy(durationSec = null), true, false, false,
                durationSeconds = { durationCalls++; 221 }, onStatus = statuses::add) { error("unexpected fallback") }
            assertEquals("flac", result.format.codec)
            assertEquals(1, durationCalls)
            assertEquals(listOf(NerdStats.LosslessBetaStatus.CHECKING, NerdStats.LosslessBetaStatus.VERIFIED), statuses)
            playback.resolve("home", target().copy(durationSec = null), true, false, false,
                durationSeconds = { error("seek must keep timing and rendition") }) { error("unexpected fallback") }
            assertEquals(2, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun `missing or mismatched original timing never relaxes recording checks`() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            val statuses = mutableListOf<NerdStats.LosslessBetaStatus>()
            val playback = LosslessPlayback(endpoint = server.url("/").toString())
            val result = playback.resolve("unknown", target().copy(durationSec = null), true, false, false,
                durationSeconds = { null }, onStatus = statuses::add) { SourceStream("https://youtube.example/audio") }
            assertNull(result.format.isLossless)
            assertEquals(NerdStats.LosslessBetaStatus.MISSING_METADATA, statuses.last())
            assertEquals(0, server.requestCount)
            server.enqueue(MockResponse().setBody("""{"tracks":[{"id":"123","title":"Lover","artistNames":["Taylor Swift"],"duration":221000,"playable":true}]}"""))
            playback.resolve("wrong", target().copy(durationSec = null), true, false, false,
                durationSeconds = { 240 }, onStatus = statuses::add) { SourceStream("https://youtube.example/audio") }
            assertEquals(NerdStats.LosslessBetaStatus.NO_MATCH, statuses.last())
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun `off metered and Jam paths do not enrich timing`() = runBlocking {
        val playback = LosslessPlayback()
        for ((index, flags) in listOf(Triple(false, false, false), Triple(true, true, false), Triple(true, false, true)).withIndex()) {
            playback.resolve("skip-$index", target().copy(durationSec = null), flags.first, flags.second, flags.third,
                durationSeconds = { error("extra work on excluded path") }) { SourceStream("https://youtube.example/audio") }
        }
    }

    @Test fun `disabling beta during duration recovery prevents community requests`() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            var enabled = true
            val playback = LosslessPlayback(endpoint = server.url("/").toString())
            playback.resolve("changed", target().copy(durationSec = null), true, false, false,
                stillEligible = { enabled }, durationSeconds = { enabled = false; 221 }) {
                SourceStream("https://youtube.example/audio")
            }
            assertEquals(0, server.requestCount)
        } finally { server.shutdown() }
    }
}
