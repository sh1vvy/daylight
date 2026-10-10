package com.music.bitchord

import com.music.bitchord.data.scrobbling.LastFM
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder
import java.security.MessageDigest

class LastFmApiTest {
    private lateinit var server: MockWebServer
    private lateinit var previous: LastFM.RuntimeConfig
    @Before fun start() {
        previous = LastFM.currentConfig()
        server = MockWebServer().also { it.start() }
        LastFM.configure(server.url("/2.0/").toString(), "test-api", "test-secret")
    }
    @After fun stop() {
        LastFM.configure(previous.endpoint, previous.apiKey, previous.secret, previous.sessionKey)
        server.shutdown()
    }

    @Test fun browserAuthorizationUsesSignedFormRequestsAndDoesNotCollectPasswords() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"token":"request-token"}"""))
        server.enqueue(MockResponse().setBody("""{"session":{"name":"error","key":"session-key","subscriber":0}}"""))
        assertEquals("request-token", LastFM.getToken().getOrThrow().token)
        assertEquals("error", LastFM.getSession("request-token").getOrThrow().session.name)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.getHeader("User-Agent")!!.startsWith("Daylight"))
        val params = request.body.readUtf8().split('&').associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        assertEquals("auth.getToken", params["method"])
        val signature = MessageDigest.getInstance("MD5").digest(
            "api_keytest-apimethodauth.getTokentest-secret".toByteArray(Charsets.UTF_8),
        ).joinToString("") { "%02x".format(it) }
        assertEquals(signature, params["api_sig"])
        assertFalse(params.containsKey("password"))
        assertFalse(LastFM.getAuthUrl("request-token").contains("test-secret"))
        assertTrue(LastFM.getAuthUrl("request-token").startsWith("https://www.last.fm/api/auth/"))
    }

    @Test fun apiErrorsPreserveTheirCodeAndCanBeRetriedAfterBrowserApproval() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"error":14,"message":"Token not authorized"}"""))
        server.enqueue(MockResponse().setBody("""{"session":{"name":"listener","key":"valid-session","subscriber":0}}"""))
        val failure = LastFM.getSession("pending-token").exceptionOrNull()
        assertEquals(14, (failure as LastFM.LastFmException).code)
        assertEquals("listener", LastFM.getSession("pending-token").getOrThrow().session.name)
    }

    @Test fun nowPlayingAndScrobbleUseTheAuthorizedSessionAndOriginalListenTimestamp() = runBlocking {
        LastFM.sessionKey = "authorized-session"
        server.enqueue(MockResponse().setBody("""{"nowplaying":{}}"""))
        server.enqueue(MockResponse().setBody("""{"scrobbles":{}}"""))
        LastFM.updateNowPlaying("Taylor Swift", "Daylight", duration = 293).getOrThrow()
        LastFM.scrobble("Taylor Swift", "Daylight", timestamp = 123456789, duration = 293).getOrThrow()
        val now = server.takeRequest().body.readUtf8()
        val scrobble = server.takeRequest().body.readUtf8()
        assertTrue(now.contains("method=track.updateNowPlaying"))
        assertTrue(now.contains("sk=authorized-session"))
        assertTrue(URLDecoder.decode(scrobble, "UTF-8").contains("timestamp[0]=123456789"))
        assertTrue(scrobble.contains("method=track.scrobble"))
    }
}
