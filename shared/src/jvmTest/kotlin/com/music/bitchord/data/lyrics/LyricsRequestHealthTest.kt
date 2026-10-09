package com.music.bitchord.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LyricsRequestHealthTest {
    @Test fun `a valid missing response is definitive but service errors are not`() {
        val missing = LyricsRequestHealth()
        assertFalse(missing.definitiveMissing)
        missing.response(404)
        assertTrue(missing.definitiveMissing)
        missing.response(503)
        assertFalse(missing.definitiveMissing)
    }

    @Test fun `a connection failure cannot disable lyrics even after another provider answered`() {
        val health = LyricsRequestHealth()
        health.response(200)
        health.failure()
        assertFalse(health.definitiveMissing)
        val parent = LyricsRequestHealth()
        parent.merge(health)
        assertFalse(parent.definitiveMissing)
    }

    @Test fun `cancelled prefetch interrupts its actual pending HTTP call`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("pending").setBodyDelay(30, TimeUnit.SECONDS))
            server.start()
            val worker = async(Dispatchers.IO) {
                val health = LyricsRequestHealth(currentCoroutineContext().job)
                try {
                    withContext(health.context) {
                        OkHttpClient().newCall(Request.Builder().url(server.url("/lyrics")).build()).lyricsBody()
                    }
                } finally { health.close() }
            }
            assertTrue(server.takeRequest(5, TimeUnit.SECONDS) != null)
            withTimeout(2_000L) { worker.cancelAndJoin() }
            assertTrue(worker.isCancelled)
        }
    }

    @Test fun `HTTP failure is reported even when the provider returns nullable lyrics`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(429))
            server.start()
            val health = LyricsRequestHealth()
            withContext(Dispatchers.IO + health.context) {
                assertNull(OkHttpClient().newCall(Request.Builder().url(server.url("/lyrics")).build()).lyricsBody())
            }
            assertTrue(health.hasFailure)
            assertFalse(health.definitiveMissing)
        }
    }

    @Test fun `HTTP 200 error envelopes and malformed payloads cannot establish missing lyrics`() = runBlocking {
        MockWebServer().use { server ->
            listOf("{broken", "{\"error\":\"upstream timed out\"}", "{\"success\":false}").forEach { body ->
                server.enqueue(MockResponse().setBody(body))
                val health = LyricsRequestHealth()
                withContext(Dispatchers.IO + health.context) {
                    OkHttpClient().newCall(Request.Builder().url(server.url("/lyrics")).build()).lyricsBody()
                }
                assertTrue(health.hasFailure)
                assertFalse(health.definitiveMissing)
            }
        }
    }

    @Test fun `a malformed expected lyric schema remains retryable`() = runBlocking {
        val health = LyricsRequestHealth()
        health.response(200)
        withContext(health.context) {
            assertNull(lyricsParse { lyricsJson.decodeFromString<LrcRed.Response>("{\"hits\":17}") })
        }
        assertFalse(health.definitiveMissing)
    }
}
