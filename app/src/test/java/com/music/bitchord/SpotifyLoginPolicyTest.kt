package com.music.bitchord

import com.music.bitchord.auth.isSpotifyLoginDestination
import com.music.bitchord.auth.isSpotifySessionDestination
import com.music.bitchord.auth.spotifyLoginUserAgent
import com.music.bitchord.auth.spotifySessionCookie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyLoginPolicyTest {
    @Test
    fun `mobile agent preserves the actual device and current browser version`() {
        val agent = spotifyLoginUserAgent(
            "Mozilla/5.0 (Linux; Android 16; Pixel 9 Build/AP3A; wv) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/140.0.7339.1 Mobile Safari/537.36",
        )
        assertTrue(agent.contains("Android 16; Pixel 9"))
        assertTrue(agent.contains("Chrome/140.0.7339.1 Mobile"))
        assertFalse(agent.contains("; wv"))
        assertFalse(agent.contains("Version/4.0"))
        assertFalse(agent.contains("Windows"))
    }

    @Test
    fun `spotify and existing identity provider handoffs are permitted`() {
        for (host in listOf("accounts.spotify.com", "open.spotify.com", "accounts.google.com", "www.facebook.com", "appleid.apple.com")) {
            assertTrue(host, isSpotifyLoginDestination("https://$host/login"))
        }
        assertTrue(isSpotifySessionDestination("HTTPS://ACCOUNTS.SPOTIFY.COM/login"))
        assertFalse(isSpotifySessionDestination("https://accounts.google.com/login"))
    }

    @Test
    fun `host checks reject lookalike and insecure destinations`() {
        for (url in listOf(
            "https://open.spotify.com.evil.test/", "https://evilspotify.com/",
            "https://open.spotify.com@evil.test/", "https://user@open.spotify.com/",
            "http://accounts.spotify.com/login", "javascript:alert(1)", "file:///spotify.com", "not a uri",
        )) {
            assertFalse(url, isSpotifyLoginDestination(url))
            assertFalse(url, isSpotifySessionDestination(url))
        }
        assertFalse(isSpotifyLoginDestination(null))
    }

    @Test
    fun `exact session cookie is captured without losing equals in its value`() {
        assertEquals("abc=def==", spotifySessionCookie("sp_t=other; sp_dc = abc=def== ; expires=123"))
        assertNull(spotifySessionCookie("not_sp_dc=wrong; PREF=sp_dc=wrong; sp_dc="))
        assertNull(spotifySessionCookie("sp_dc"))
        assertNull(spotifySessionCookie(null))
    }
}
