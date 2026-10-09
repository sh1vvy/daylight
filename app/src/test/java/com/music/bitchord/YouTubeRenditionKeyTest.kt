package com.music.bitchord

import com.music.bitchord.playback.YouTubeRenditionKey
import org.junit.Assert.*
import org.junit.Test

class YouTubeRenditionKeyTest {
    @Test fun `signed url expiry does not change stable rendition identity`() {
        assertEquals(YouTubeRenditionKey.forStream("song", "https://media.example/audio?itag=251&clen=12345&lmt=7&expire=10"),
            YouTubeRenditionKey.forStream("song", "https://other.example/audio?expire=20&itag=251&clen=12345&lmt=7"))
    }
    @Test fun `different quality or replaced file never shares partial bytes`() {
        val low = YouTubeRenditionKey.forStream("song", "https://media.example/audio?itag=249&clen=123&lmt=7")
        assertNotEquals(low, YouTubeRenditionKey.forStream("song", "https://media.example/audio?itag=251&clen=456&lmt=7"))
        assertNotEquals(low, YouTubeRenditionKey.forStream("song", "https://media.example/audio?itag=249&clen=123&lmt=8"))
        assertTrue(low.startsWith("song#youtube-"))
    }
    @Test fun `non YouTube stream without itag keeps legacy key`() {
        assertEquals("song", YouTubeRenditionKey.forStream("song", "https://media.example/audio"))
    }
}
