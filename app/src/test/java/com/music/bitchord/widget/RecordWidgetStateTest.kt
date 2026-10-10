package com.music.bitchord.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordWidgetStateTest {
    private val snapshot = MediaWidgetSnapshot(
        mediaId = "song", title = "A song", artist = "An artist", artworkUrl = null,
        isPlaying = true, hasPrevious = true, hasNext = true,
        positionMs = 90_000L, durationMs = 180_000L,
        capturedAtElapsedMs = 1_000_000L, capturedAtEpochMs = 2_000_000L, clockRunning = true,
    )

    @Test fun runningClockUsesTheActualPlaybackAnchorAndEndsAtTheTrackDuration() {
        val playing = snapshot.recordProgress(1_010_000L, 2_010_000L)
        assertEquals(100_000L, playing.positionMs)
        assertEquals(555, playing.progress)
        assertTrue(playing.clockRunning)
        val ended = snapshot.recordProgress(1_300_000L, 2_300_000L)
        assertEquals(180_000L, ended.positionMs)
        assertEquals(1_000, ended.progress)
        assertFalse(ended.clockRunning)
    }

    @Test fun pausesBufferingAndNonStandardSpeedDoNotInventListenedTime() {
        for (stopped in listOf(snapshot.copy(isPlaying = false), snapshot.copy(isLoading = true), snapshot.copy(clockRunning = false))) {
            val progress = stopped.recordProgress(1_050_000L, 2_050_000L)
            assertEquals(90_000L, progress.positionMs)
            assertFalse(progress.clockRunning)
        }
    }

    @Test fun restoredOrRebootedSnapshotsNeverRunAStaleClock() {
        for ((restored, nowElapsed, nowEpoch) in listOf(
            Triple(snapshot.copy(capturedAtEpochMs = 0, capturedAtElapsedMs = 0), 1_100_000L, 2_100_000L),
            Triple(snapshot, 50_000L, 2_100_000L),
            Triple(snapshot, 1_100_000L, 3_500_000L),
        )) {
            val progress = restored.recordProgress(nowElapsed, nowEpoch)
            assertEquals(90_000L, progress.positionMs)
            assertFalse(progress.clockRunning)
        }
        assertFalse(snapshot.copy(durationMs = 0).recordProgress(1_100_000L, 2_100_000L).clockRunning)
    }

    @Test fun twoCellCardsKeepTransportTargetsInsideAndHideDetailsBeforeTheyClip() {
        assertEquals(110f, RecordWidgetSizing.MIN_WIDTH_DP, 0f)
        assertEquals(110f, RecordWidgetSizing.MIN_HEIGHT_DP, 0f)
        assertTrue(RecordWidgetSizing.MIN_WIDTH_DP - 2 * 8 >= 48)
        assertFalse(RecordWidgetSizing.showSideControls(130f))
        assertFalse(RecordWidgetSizing.showSideControls(159f))
        assertTrue(RecordWidgetSizing.showSideControls(160f))
        assertTrue(160 - 2 * 8 >= 3 * 48)
        assertFalse(RecordWidgetSizing.showArtist(110f, 1f))
        assertTrue(RecordWidgetSizing.showArtist(160f, 1f))
        assertFalse(RecordWidgetSizing.showArtist(160f, 2f))
        assertFalse(RecordWidgetSizing.showTime(160f, 1f))
        assertTrue(RecordWidgetSizing.showTime(220f, 1f))
        assertFalse(RecordWidgetSizing.showTime(220f, 2f))
        assertFalse(RecordWidgetSizing.showWave(220f, 1f))
        assertTrue(RecordWidgetSizing.showTime(300f, 2f))
        assertFalse(RecordWidgetSizing.showWave(300f, 2f))
    }

    @Test fun durationFormattingWorksForSongsAndLongRecordings() {
        assertEquals("0:00", recordWidgetTime(-1L))
        assertEquals("3:26", recordWidgetTime(206_000L))
        assertEquals("1:02:03", recordWidgetTime(3_723_000L))
    }

    @Test fun repeatedPlayerCallbacksDoNotRepublishArtworkButSeeksAndRealStateChangesDo() {
        val current = snapshot.copy(positionMs = 95_000L,
            capturedAtElapsedMs = 1_005_000L, capturedAtEpochMs = 2_005_000L)
        assertTrue(current.sameWidgetPresentationAs(snapshot))
        assertFalse(current.copy(positionMs = 60_000L).sameWidgetPresentationAs(snapshot))
        assertFalse(current.copy(isPlaying = false, clockRunning = false).sameWidgetPresentationAs(snapshot))
        assertFalse(current.copy(controlsLocked = true).sameWidgetPresentationAs(snapshot))
        assertFalse(current.copy(hasNext = false).sameWidgetPresentationAs(snapshot))
        assertFalse(current.copy(durationMs = 190_000L).sameWidgetPresentationAs(snapshot))
    }
}
