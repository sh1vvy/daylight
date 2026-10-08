package com.music.bitchord.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PlaylistCreationOrderKeyTest {
    @Test
    fun remoteAndDownloadedPlaylistCardsShareTheirActivityKey() {
        listOf("PLremote", "VLPLremote", "local:playlist:PLremote", "local:playlist:VLPLremote").forEach { id ->
            assertEquals("PLremote", playlistCreationOrderKey(id))
        }
    }

    @Test
    fun actualLocalPlaylistIdsAndOlderTimestampIdsRemainIndependent() {
        listOf("local:playlist:sp_local_1723000000000", "local:playlist:sp_local_some-uuid", "local:playlist:one").forEach { id ->
            assertEquals(id, playlistCreationOrderKey(id))
            assertNotEquals(id.removePrefix("local:playlist:"), playlistCreationOrderKey(id))
        }
    }

    @Test
    fun downloadedAlbumPagesAreNotAliasedToPlaylistActivity() {
        listOf("local:playlist:MPREbalbum", "local:playlist:VLOLAKalbum").forEach { id ->
            assertEquals(id, playlistCreationOrderKey(id))
        }
    }
}
