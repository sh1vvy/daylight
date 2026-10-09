package com.music.bitchord.data.lossless

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class LosslessBetaClientTest {
    private lateinit var server: MockWebServer
    private val recording = LosslessRecording("Birds of a Feather", listOf("Billie Eilish"), 210_000, true, "USXX12345678")

    @Before fun start() { server = MockWebServer(); server.start() }
    @After fun stop() { server.shutdown() }

    @Test fun `default and disabled options never contact a server`() = runBlocking {
        val client = LosslessBetaClient()
        assertNull(client.resolve(LosslessBetaOptions(), recording))
        assertNull(client.resolve(options().copy(enabled = false), recording))
        assertEquals(0, server.requestCount)
    }

    @Test fun `videos and unknown recording timing never contact a server`() = runBlocking {
        assertNull(LosslessBetaClient().resolve(options(), recording.copy(isVideo = true)))
        assertNull(LosslessBetaClient().resolve(options(), recording.copy(durationMs = 0)))
        assertEquals(0, server.requestCount)
    }

    @Test fun `cleartext public and credential query urls are rejected before networking`() = runBlocking {
        for (url in listOf("http://example.com", "https://user:pass@example.com", "https://example.com?token=secret", "invalid")) {
            assertNull(LosslessBetaClient().resolve(LosslessBetaOptions(true, url), recording))
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun `verified stream preserves base path and sends range with Daylight identity`() = runBlocking {
        search(); audio(flacHeader(96_000, 24))
        val stream = LosslessBetaClient().resolve(options("/private/"), recording)
        assertNotNull(stream)
        assertEquals(96_000, stream!!.info.sampleRateHz)
        assertEquals(24, stream.info.bitDepth)
        assertTrue(stream.info.isHiRes)
        assertEquals("/private/search/tracks", server.takeRequest().requestUrl!!.encodedPath)
        val request = server.takeRequest()
        assertEquals("/private/track/123", request.requestUrl!!.encodedPath)
        assertEquals("bytes=0-41", request.getHeader("Range"))
        assertEquals("Daylight-Lossless-Beta/1", request.getHeader("User-Agent"))
        assertNull(request.getHeader("Origin"))
    }

    @Test fun `aac with lossless content type cannot become a lossless stream`() = runBlocking {
        search(); audio(ByteArray(42) { 1 }, "audio/flac")
        assertNull(LosslessBetaClient().resolve(options(), recording))
    }

    @Test fun `actual header works even when server labels it octet stream`() = runBlocking {
        search(); audio(flacHeader(44_100, 16), "application/octet-stream")
        val info = LosslessBetaClient().resolve(options(), recording)!!.info
        assertEquals(16, info.bitDepth)
        assertFalse(info.isHiRes)
    }

    @Test fun `busy forbidden and down responses fail without retries`() = runBlocking {
        for (status in listOf(202, 403, 429, 503)) {
            server.enqueue(MockResponse().setResponseCode(status))
            assertNull(LosslessBetaClient().resolve(options(), recording))
        }
        assertEquals(4, server.requestCount)
    }

    @Test fun `redirect is not followed to another provider`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/other")))
        assertNull(LosslessBetaClient().resolve(options(), recording))
        assertEquals(1, server.requestCount)
    }

    @Test fun `wrong ISRC clean edit missing rating and unplayable rows never open audio`() = runBlocking {
        for (change in listOf(
            "isrc" to JsonPrimitive("USXX87654321"), "explicit" to JsonPrimitive(false),
            "explicit" to null, "playable" to JsonPrimitive(false), "duration" to JsonPrimitive(240_000),
        )) {
            val row = validRow().toMutableMap()
            if (change.second == null) row.remove(change.first) else row[change.first] = change.second!!
            search(JsonObject(row).toString())
            assertNull(LosslessBetaClient().resolve(options(), recording))
        }
        assertEquals(5, server.requestCount)
    }

    @Test fun `missing ISRC requires exact title version and shared artist`() = runBlocking {
        for ((title, artist) in listOf("Birds of a Feather (Live)" to "Billie Eilish", "Birds of a Feather" to "A Cover Artist")) {
            search("""{"id":"123","title":"$title","artistNames":["$artist"],"duration":210000,"explicit":true,"playable":true}""")
            assertNull(LosslessBetaClient().resolve(options(), recording.copy(isrc = null)))
        }
        assertEquals(2, server.requestCount)
    }

    @Test fun `exact recording without ISRC may use punctuation and accent normalization`() = runBlocking {
        search("""{"id":"123","title":"Birds of a Feather","artistNames":["Bíllie Eilish"],"duration":210000,"explicit":true,"playable":true}""")
        audio(flacHeader())
        assertNotNull(LosslessBetaClient().resolve(options(), recording.copy(isrc = null)))
    }

    @Test fun `header duration independently rejects mislabeled recording`() = runBlocking {
        search(); audio(flacHeader(seconds = 240))
        assertNull(LosslessBetaClient().resolve(options(), recording))
    }

    @Test fun `malformed and oversized search replies cannot crash or open audio`() = runBlocking {
        for (body in listOf("bad json", "[]", "{\"tracks\":[{\"playable\":{}}]}", " ".repeat(300_000))) {
            server.enqueue(MockResponse().setBody(body))
            assertNull(LosslessBetaClient().resolve(options(), recording))
        }
        assertEquals(4, server.requestCount)
    }

    @Test fun `nonzero range and truncated header cannot validate`() = runBlocking {
        search(); server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 42-83/10000").setBody(Buffer().write(flacHeader())))
        assertNull(LosslessBetaClient().resolve(options(), recording))
        search(); audio(flacHeader().copyOf(10))
        assertNull(LosslessBetaClient().resolve(options(), recording))
    }

    @Test fun `server ignoring range still reads only the header`() = runBlocking {
        search()
        server.enqueue(MockResponse().setBody(Buffer().write(flacHeader()).write(ByteArray(100_000))))
        assertNotNull(LosslessBetaClient().resolve(options(), recording))
    }

    @Test fun `slow provider respects total budget`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}").setHeadersDelay(2, TimeUnit.SECONDS))
        val started = System.nanoTime()
        assertNull(LosslessBetaClient(budgetMs = 100).resolve(options(), recording))
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 1_000)
    }

    @Test fun `caller cancellation propagates and cancels request`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}").setHeadersDelay(2, TimeUnit.SECONDS))
        val job = async(Dispatchers.IO) { LosslessBetaClient(budgetMs = 5_000).resolve(options(), recording) }
        assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }

    private fun options(path: String = "/") = LosslessBetaOptions(true, server.url(path).toString())
    private fun validRow() = Json.parseToJsonElement("""{"id":"123","title":"Birds of a Feather","artistNames":["Billie Eilish"],"duration":210000,"explicit":true,"playable":true,"isrc":"USXX12345678"}""") as JsonObject
    private fun search(row: String = validRow().toString()) = server.enqueue(MockResponse().setBody("{\"tracks\":[$row]}"))
    private fun audio(bytes: ByteArray, type: String = "audio/flac") = server.enqueue(MockResponse().setBody(Buffer().write(bytes)).setHeader("Content-Type", type))
}

internal fun flacHeader(rate: Int = 44_100, depth: Int = 16, channels: Int = 2, seconds: Int = 210): ByteArray {
    val bytes = ByteArray(42)
    "fLaC".toByteArray().copyInto(bytes)
    bytes[4] = 0x80.toByte(); bytes[7] = 34
    val packed = (rate.toLong() shl 44) or ((channels - 1).toLong() shl 41) or
        ((depth - 1).toLong() shl 36) or (rate.toLong() * seconds)
    for (i in 0..7) bytes[18 + i] = (packed ushr (56 - i * 8)).toByte()
    return bytes
}
