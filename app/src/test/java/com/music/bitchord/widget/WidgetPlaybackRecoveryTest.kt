package com.music.bitchord.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetPlaybackRecoveryTest {
    @Test fun endedQueueRestartsOnTheFirstTapEvenIfReadinessStayedTrue() {
        assertEquals(WidgetToggleDecision.RESTART, widgetToggleDecision(playWhenReady = true, ended = true))
        assertEquals(WidgetToggleDecision.RESTART, widgetToggleDecision(playWhenReady = false, ended = true))
    }

    @Test fun normalTransportStillPausesDuringLoadingAndResumesWhenPaused() {
        assertEquals(WidgetToggleDecision.PAUSE, widgetToggleDecision(playWhenReady = true, ended = false))
        assertEquals(WidgetToggleDecision.PLAY, widgetToggleDecision(playWhenReady = false, ended = false))
    }

    @Test fun upgradeStopsStalePlayingAndLoadingButPreservesTheTrackAndUserOptions() {
        val snapshot = MediaWidgetSnapshot(
            mediaId = "track", title = "A song", artist = "An artist", artworkUrl = "content://art",
            isPlaying = true, hasPrevious = true, hasNext = false,
            isLiked = true, shuffleEnabled = true, isLoading = true, controlsLocked = true,
            positionMs = 45_000L, durationMs = 180_000L, clockRunning = true,
        )
        assertEquals(snapshot.copy(isPlaying = false, isLoading = false, clockRunning = false), snapshot.afterAppUpgrade())
    }

    @Test fun emptyWidgetStaysEmptyAfterUpgrade() {
        assertEquals(MediaWidgetSnapshot.EMPTY, MediaWidgetSnapshot.EMPTY.afterAppUpgrade())
    }
}
