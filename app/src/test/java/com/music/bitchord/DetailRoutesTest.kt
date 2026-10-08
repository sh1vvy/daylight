package com.music.bitchord

import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.DetailPage
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.PlaylistCreator
import com.music.bitchord.data.model.CreatorProfilePage
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.detailRouteKey
import com.music.bitchord.ui.HeaderCreditLink
import com.music.bitchord.ui.headerCreditLink
import com.music.bitchord.ui.creatorCollections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailRoutesTest {
    private fun page(id: Long, browseId: String = "VLsame-playlist") = DetailPage(
        browseId = browseId,
        title = "Playlist",
        subtitle = "",
        thumbnailUrl = null,
        songs = UiState.Loading,
        type = BrowseType.PLAYLIST,
        instanceId = id,
    )

    @Test
    fun `two visits to one playlist retain independent viewports when returning`() {
        val first = page(1)
        val second = page(2)
        assertNotEquals(first.detailRouteKey(), second.detailRouteKey())
        val viewports = mutableMapOf(first.detailRouteKey() to 47, second.detailRouteKey() to 0)
        viewports[second.detailRouteKey()] = 12
        viewports.keys.retainAll(setOf(first.detailRouteKey()))
        assertEquals(47, viewports[first.detailRouteKey()])
    }

    @Test
    fun `loaded songs and refreshed metadata keep the same navigation slot`() {
        val loading = page(1)
        val loaded = loading.copy(title = "Updated name", songs = UiState.Success(emptyList()))
        assertEquals(loading.detailRouteKey(), loaded.detailRouteKey())
    }

    @Test
    fun `compatible default IDs distinguish browse pages and cannot collide with settings or tab routes`() {
        val settingsPage = page(0, browseId = "settings").detailRouteKey()
        val tabPage = page(0, browseId = "tab:0").detailRouteKey()
        assertNotEquals(settingsPage, tabPage)
        assertNotEquals("settings", settingsPage)
        assertNotEquals("tab:0", tabPage)
    }

    @Test
    fun `playlist author opens its account and never its first song artist`() {
        val owner = PlaylistCreator("Owner", "UCowner")
        val song = Song(videoId = "song", title = "Track", artist = "Recording artist", thumbnailUrl = null, artistId = "UCartist")
        assertEquals(HeaderCreditLink.Creator(owner), page(1).copy(creator = owner).headerCreditLink(song))
        assertNull(page(1).headerCreditLink(song))
    }

    @Test
    fun `album credit continues to open the recording artist`() {
        val song = Song(videoId = "song", title = "Track", artist = "Recording artist", thumbnailUrl = null, artistId = "UCartist")
        assertEquals(
            HeaderCreditLink.Artist("UCartist", "Recording artist"),
            page(1).copy(type = BrowseType.ALBUM).headerCreditLink(song),
        )
    }

    @Test
    fun `creator keeps originating playlist once while loading errors and public collections arrive`() {
        val origin = BrowseItem("VLorigin", "Origin", "Owner", null, BrowseType.PLAYLIST)
        val other = origin.copy(browseId = "VLother", title = "Another playlist")
        val profile = CreatorProfilePage(PlaylistCreator("Owner", "UCowner"), origin, UiState.Loading)
        assertEquals(listOf(origin), profile.creatorCollections())
        assertEquals(listOf(origin), profile.copy(playlists = UiState.Error("Offline")).creatorCollections())
        assertEquals(
            listOf(origin, other),
            profile.copy(playlists = UiState.Success(listOf(other, origin, other))).creatorCollections(),
        )
    }
}
