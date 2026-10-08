package com.music.bitchord.data.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackItemIdentityTest {
    private fun song(id: String) = Song(id, "Just Keep Watching", "Tate McRae", null)

    @Test
    fun differentUploadsWithTheSameTitleAndArtistDoNotAllAppearToBePlaying() {
        val playing = song("official-video")
        assertTrue(song("official-video").isSamePlaybackItemAs(playing))
        assertFalse(song("album-audio").isSamePlaybackItemAs(playing))
        assertFalse(song("visualizer").isSamePlaybackItemAs(playing))
    }

    @Test
    fun exactIdentitySurvivesDisplayMetadataChangesAndIgnoresPlaylistSlotIds() {
        val playing = song("official-video").copy(title = "Updated title", artist = "Updated credit", setVideoId = "slot")
        assertTrue(song("official-video").isSamePlaybackItemAs(playing))
        assertFalse(song("another-video").copy(setVideoId = "slot").isSamePlaybackItemAs(playing))
    }

    @Test
    fun missingTrackOrEmptyIdentityCannotLightUpRows() {
        assertFalse(song("official-video").isSamePlaybackItemAs(null))
        assertFalse(song("").isSamePlaybackItemAs(song("")))
    }
}
