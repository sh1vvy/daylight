package com.music.bitchord

import com.music.bitchord.data.library.PlaylistCoverStore
import org.junit.Assert.*
import org.junit.Test

class PlaylistCoverScopeTest {
    @Test fun remoteCoversBelongToTheirAccountAndChannel() {
        assertNotEquals(PlaylistCoverStore.key("account:channel1", "VLPL123"), PlaylistCoverStore.key("account:channel2", "VLPL123"))
        assertNotEquals(PlaylistCoverStore.key("a:c", "VLPL123"), PlaylistCoverStore.key("b:c", "VLPL123"))
        assertEquals(PlaylistCoverStore.key("a:c", "VLPL123"), PlaylistCoverStore.key("a:c", "PL123"))
    }
    @Test fun devicePlaylistsKeepTheirCoversThroughAccountChanges() {
        assertEquals(PlaylistCoverStore.key(null, "local:playlist:one"), PlaylistCoverStore.key("a:c", "VLlocal:playlist:one"))
    }
}
