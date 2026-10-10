package com.music.bitchord

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.scrobbling.ScrobbleManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScrobbleManagerTest {
    private val song = Song("first", "Daylight", "Taylor Swift", null, "3:00")

    @Test fun pausesAndRepeatedCallbacksDoNotResetTheTimerOrDuplicateAListen() = runTest {
        val listens = mutableListOf<Triple<String, Int, Long>>()
        val manager = ScrobbleManager(backgroundScope,
            monotonicMillis = { testScheduler.currentTime }, unixSeconds = { 1234 },
            submit = { song, seconds, started -> listens += Triple(song.videoId, seconds, started) },
            nowPlaying = { _, _ -> })
        manager.onPlayerStateChanged(true, song, 180_000)
        advanceTimeBy(20_000)
        manager.onPlayerStateChanged(true, song, 180_000)
        advanceTimeBy(10_000)
        manager.onPlayerStateChanged(false, song)
        advanceTimeBy(100_000)
        assertTrue(listens.isEmpty())
        manager.onPlayerStateChanged(true, song)
        advanceTimeBy(59_999); runCurrent()
        assertTrue(listens.isEmpty())
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf(Triple("first", 180, 1234L)), listens)
        manager.onPlayerStateChanged(false, song)
        manager.onPlayerStateChanged(true, song)
        advanceTimeBy(180_000); runCurrent()
        assertEquals(1, listens.size)
        manager.destroy()
    }

    @Test fun customTimingCannotSubmitBeforeLastfmRulesAndLongTracksQualifyAtFourMinutes() = runTest {
        var count = 0
        val manager = ScrobbleManager(backgroundScope, scrobbleDelayPercent = .1f, scrobbleDelaySeconds = 30,
            monotonicMillis = { testScheduler.currentTime }, submit = { _, _, _ -> count++ }, nowPlaying = { _, _ -> })
        manager.onSongStart(song, 600_000)
        advanceTimeBy(239_999); runCurrent(); assertEquals(0, count)
        advanceTimeBy(1); runCurrent(); assertEquals(1, count)
        manager.destroy()
    }

    @Test fun skippedAndShortTracksDoNotScrobbleAndRepeatCountsAsAnotherListen() = runTest {
        val listens = mutableListOf<String>()
        val manager = ScrobbleManager(backgroundScope, monotonicMillis = { testScheduler.currentTime },
            submit = { song, _, _ -> listens += song.videoId }, nowPlaying = { _, _ -> })
        manager.onSongStart(song, 30_000)
        advanceTimeBy(60_000); runCurrent(); assertTrue(listens.isEmpty())
        manager.onSongStart(song)
        advanceTimeBy(20_000)
        manager.onSongStart(song.copy(videoId = "second"))
        advanceTimeBy(90_000); runCurrent(); assertEquals(listOf("second"), listens)
        manager.onSongStart(song.copy(videoId = "second"))
        advanceTimeBy(90_000); runCurrent(); assertEquals(listOf("second", "second"), listens)
        manager.destroy()
    }

    @Test fun signingOutCancelsPendingSubmissionsAndHourLongMetadataParsesCorrectly() = runTest {
        val durations = mutableListOf<Int>()
        val manager = ScrobbleManager(backgroundScope, monotonicMillis = { testScheduler.currentTime },
            submit = { _, duration, _ -> durations += duration }, nowPlaying = { _, _ -> })
        manager.onSongStart(song.copy(durationText = "1:02:03"))
        advanceTimeBy(240_000); runCurrent(); assertEquals(listOf(3723), durations)
        manager.onSongStart(song)
        advanceTimeBy(30_000)
        manager.destroy()
        advanceTimeBy(180_000); runCurrent(); assertEquals(1, durations.size)
    }

    @Test fun changingTracksCancelsStaleNowPlayingWithoutDiscardingAQualifiedPreviousListen() = runTest {
        val statuses = mutableListOf<String>()
        val listens = mutableListOf<String>()
        val manager = ScrobbleManager(backgroundScope,
            monotonicMillis = { testScheduler.currentTime },
            nowPlaying = { track, _ ->
                if (track.videoId == "first") delay(120_000)
                statuses += track.videoId
            },
            submit = { track, _, _ ->
                delay(20_000)
                listens += track.videoId
            })
        manager.onSongStart(song)
        advanceTimeBy(90_000); runCurrent()
        manager.onSongStart(song.copy(videoId = "second"))
        runCurrent()
        advanceTimeBy(31_000); runCurrent()

        assertEquals(listOf("second"), statuses)
        assertEquals(listOf("first"), listens)
        manager.destroy()
    }
}
