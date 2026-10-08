package com.music.bitchord

import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.DetailPage
import com.music.bitchord.data.model.UiState
import com.music.bitchord.ui.detailRouteKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
}
