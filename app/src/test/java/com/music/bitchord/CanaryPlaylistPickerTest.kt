package com.music.bitchord

import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.ui.recentPlaylistChoices
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CanaryPlaylistPickerTest {
    @Test fun writableDialogExcludesDisabledAndBuiltInChoices() {
        val response = Json.parseToJsonElement("""{"contents":{"addToPlaylistRenderer":{"playlists":[
          {"playlistAddToOptionRenderer":{"playlistId":"VLPLmine","title":{"simpleText":"My playlist"}}},
          {"playlistAddToOptionRenderer":{"playlistId":"PLdisabled","isDisabled":true,"title":{"simpleText":"Unavailable"}}},
          {"playlistAddToOptionRenderer":{"playlistId":"PLsaved","isEditable":false,"title":{"simpleText":"Someone else's"}}},
          {"playlistAddToOptionRenderer":{"playlistId":"VLLM","title":{"simpleText":"Liked Music"}}},
          {"playlistAddToOptionRenderer":{"playlistId":"WL","title":{"simpleText":"Watch later"}}},
          {"playlistAddToOptionRenderer":{"serviceEndpoint":{"playlistEditEndpoint":{"playlistId":"PLother"}},"title":{"runs":[{"text":"Other own list"}]}}}
        ]}}}""")
        assertEquals(listOf("PLmine", "PLother"), InnertubeParser.parseEditablePlaylistOptions(response)!!.map { it.playlistId })
        assertNull(InnertubeParser.parseEditablePlaylistOptions(Json.parseToJsonElement("{}")))
        assertEquals(emptyList<UserPlaylist>(), InnertubeParser.parseEditablePlaylistOptions(Json.parseToJsonElement("""{"addToPlaylistRenderer":{"playlists":[]}}""")))
    }
    @Test fun activityOrderBeatsAlphabeticalAndRetainsOlderProviderOrder() {
        fun list(id: String) = UserPlaylist(id, id, "", null)
        val choices = listOf(list("PLAlpha"), list("PLBeta"), list("PLZulu"), list("PLNew"))
        assertEquals(listOf("PLZulu", "PLNew", "PLAlpha", "PLBeta"),
            recentPlaylistChoices(choices, listOf("local:playlist:VLPLZulu", "VLPLNew")).map { it.playlistId })
    }
}
