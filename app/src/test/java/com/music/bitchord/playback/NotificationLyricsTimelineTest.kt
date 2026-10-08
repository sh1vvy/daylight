package com.music.bitchord.playback

import com.music.bitchord.data.lyrics.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationLyricsTimelineTest {
    @Test
    fun `line boundaries and gaps preserve the notification caption`() {
        val timeline = NotificationLyricsTimeline(listOf(
            LyricLine(1_000, "First"), LyricLine(2_000, ""), LyricLine(3_000, "Last"),
        ))
        assertEquals("Artist", timeline.subtitleAt(999, "Artist"))
        assertEquals("♪ First", timeline.subtitleAt(1_000, "Artist"))
        assertEquals("Artist", timeline.subtitleAt(2_000, "Artist"))
        assertEquals("♪ Last", timeline.subtitleAt(3_000, "Artist"))
        assertNull(timeline.nextDelayMs(3_000, 1f))
    }

    @Test
    fun `a seek backwards uses the earlier line without retaining a cursor`() {
        val timeline = NotificationLyricsTimeline(listOf(LyricLine(0, "First"), LyricLine(5_000, "Last")))
        assertEquals("♪ Last", timeline.subtitleAt(6_000, "Artist"))
        assertEquals("♪ First", timeline.subtitleAt(1_000, "Artist"))
        assertEquals(4_000L, timeline.nextDelayMs(1_000, 1f))
    }

    @Test
    fun `duplicate stamps keep the last line and unordered input is sorted once`() {
        val timeline = NotificationLyricsTimeline(listOf(
            LyricLine(5_000, "Later"), LyricLine(1_000, "First"), LyricLine(1_000, "Second"),
        ))
        assertEquals("♪ Second", timeline.subtitleAt(1_000, "Artist"))
        assertEquals(4_000L, timeline.nextDelayMs(1_000, 1f))
    }

    @Test
    fun `speed and dense lines choose a bounded wakeup`() {
        val timeline = NotificationLyricsTimeline(listOf(LyricLine(2_000, "Line")))
        assertEquals(1_000L, timeline.nextDelayMs(0, 2f))
        assertEquals(4_000L, timeline.nextDelayMs(0, 0.5f))
        assertEquals(16L, timeline.nextDelayMs(1_999, 1f))
        assertEquals(2_000L, timeline.nextDelayMs(0, Float.NaN))
    }

    @Test
    fun `missing lyrics and the final line need no periodic work`() {
        val missing = NotificationLyricsTimeline(emptyList())
        assertEquals("Artist", missing.subtitleAt(10_000, "Artist"))
        assertNull(missing.nextDelayMs(0, 1f))
        val last = NotificationLyricsTimeline(listOf(LyricLine(0, "Line")))
        assertNull(last.nextDelayMs(0, 1f))
    }

    @Test
    fun `three minute lyric sample requires at least eighty percent fewer wakeups`() {
        val timeline = NotificationLyricsTimeline((0..17).map { LyricLine(it * 10_000L, "Line $it") })
        var positionMs = 0L
        var wakeups = 0
        while (positionMs < 180_000L) {
            val wait = timeline.nextDelayMs(positionMs, 1f) ?: break
            positionMs += wait
            wakeups++
        }
        val previousHalfSecondWakeups = 180_000 / 500
        assertTrue("$wakeups scheduled wakeups", wakeups < previousHalfSecondWakeups / 5)
        assertEquals(34, wakeups)
    }
}
