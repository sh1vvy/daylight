/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-08 through 2026-10-08.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

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
