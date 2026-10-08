package com.music.bitchord.data.innertube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaylistCreatorParserTest {
    @Test
    fun realCommunityPlaylistFacepileIdentifiesItsCreatorAndPersonalAvatar() {
        // Trimmed guest response from the community playlist reproduced in
        // Android QA, music.youtube.com/playlist?list=PLz0HVvbtUDlizyPzX2KrMsVWZF5Cz-HVa.
        // Current headers keep the owner in a view model, not subtitle runs.
        val root = fixture("community-playlist-facepile.json")
        val creator = InnertubeParser.parsePlaylistCreator(root)
        assertEquals("Siddh Shah", creator?.name)
        assertEquals("UCp6UBtikk9qNpyNaecUc8iw", creator?.browseId)
        assertEquals(
            "https://yt3.ggpht.com/ytc/AIdro_mw-NJSHMAYLBAx-mitZaxuUCo-r-2nFkqC2mylFRZTBHY=s48-c-k-c0x00000000-no-cc-rj-rp",
            creator?.thumbnailUrl,
        )
    }

    @Test
    fun realPublicCreatorPageLoadsItsOwnHeaderAndCollections() {
        // Trimmed guest browse response for the channel above, retaining two
        // real public cards and its personal foreground thumbnail.
        val profile = InnertubeParser.parseCreatorProfile(fixture("community-creator-profile.json"))
        assertEquals("Siddh Shah", profile.name)
        assertEquals(
            "https://yt3.googleusercontent.com/ytc/AIdro_mw-NJSHMAYLBAx-mitZaxuUCo-r-2nFkqC2mylFRZTBHY=w544-c-h544-k-c0x00ffffff-no-l90-rj",
            profile.thumbnailUrl,
        )
        assertEquals(
            listOf("VLPLz0HVvbtUDlizyPzX2KrMsVWZF5Cz-HVa", "VLPLz0HVvbtUDljxvvauXdx6okVVwO68C-Th"),
            profile.playlists.map { it.browseId },
        )
        assertEquals(listOf("MOST ROMANTIC ALBUM😘😘", "💯 UNCHE SHAUKH 💯"), profile.playlists.map { it.title })
    }

    @Test
    fun creatorComesFromThePlaylistHeaderRatherThanFirstTrackArtist() {
        val root = Json.parseToJsonElement(
            """{
              "header":{"musicResponsiveHeaderRenderer":{
                "title":{"runs":[{"text":"Clouds"}]},
                "straplineTextOne":{"runs":[{"text":"Gunnu","navigationEndpoint":{"browseEndpoint":{"browseId":"UCcreator"}}}]},
                "straplineThumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://creator-avatar"}]}}},
                "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://playlist-cover"}]}}}
              }},
              "contents":{"musicResponsiveListItemRenderer":{"flexColumns":[
                {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"First song"}]}}},
                {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Recording artist","navigationEndpoint":{"browseEndpoint":{"browseId":"UCfirst-song-artist"}}}]}}}
              ]}}
            }""",
        )
        val creator = InnertubeParser.parsePlaylistCreator(root)
        assertEquals("Gunnu", creator?.name)
        assertEquals("UCcreator", creator?.browseId)
        assertEquals("https://creator-avatar", creator?.thumbnailUrl)
    }

    @Test
    fun oldSubtitleHeaderUsesItsAuthorEndpointWithoutReusingPlaylistArt() {
        val root = Json.parseToJsonElement(
            """{"header":{"musicDetailHeaderRenderer":{
              "subtitle":{"runs":[{"text":"Playlist • "},{"text":"Owner","navigationEndpoint":{"browseEndpoint":{"browseId":"UCowner"}}},{"text":" • 42 songs"}]},
              "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://cover"}]}}}
            }}}""",
        )
        val creator = InnertubeParser.parsePlaylistCreator(root)
        assertEquals("Owner", creator?.name)
        assertEquals("UCowner", creator?.browseId)
        assertNull(creator?.thumbnailUrl)
    }

    @Test
    fun missingCreatorHeaderDoesNotInferAnAccountFromSongCredits() {
        val root = Json.parseToJsonElement(
            """{"contents":{"musicResponsiveListItemRenderer":{
              "subtitle":{"runs":[{"text":"Artist","navigationEndpoint":{"browseEndpoint":{"browseId":"UCartist"}}}]}
            }}}""",
        )
        assertNull(InnertubeParser.parsePlaylistCreator(root))
    }

    @Test
    fun explicitUnlinkedAuthorStillHasANameWithoutAnInventedChannel() {
        val root = Json.parseToJsonElement(
            """{"header":{"musicDetailHeaderRenderer":{"author":{"runs":[{"text":"A friend"}]}}}}""",
        )
        val creator = InnertubeParser.parsePlaylistCreator(root)
        assertEquals("A friend", creator?.name)
        assertNull(creator?.browseId)
    }

    @Test
    fun creatorProfileKeepsRealHeaderAndOnlyPlaylistCards() {
        val root = Json.parseToJsonElement(
            """{
              "header":{"musicVisualHeaderRenderer":{
                "title":{"runs":[{"text":"Gunnu"}]},
                "foregroundThumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://profile-photo"}]}}}
              }},
              "metadata":{"channelMetadataRenderer":{"description":"Some favourite songs."}},
              "contents":{"items":[
                ${card("VLclouds", "Clouds")},
                ${card("VLclouds", "Repeated renderer")},
                ${card("PLsunset", "Sunset")},
                ${card("MPREbalbum", "An album")},
                ${card("UCartist", "An artist")}
              ]}
            }""",
        ).jsonObject
        val profile = InnertubeParser.parseCreatorProfile(root)
        assertEquals("Gunnu", profile.name)
        assertEquals("https://profile-photo", profile.thumbnailUrl)
        assertEquals("Some favourite songs.", profile.description)
        assertEquals(listOf("VLclouds", "VLPLsunset"), profile.playlists.map { it.browseId })
        assertEquals(listOf("Clouds", "Sunset"), profile.playlists.map { it.title })
    }

    private fun card(id: String, name: String) = """{"musicTwoRowItemRenderer":{
      "title":{"runs":[{"text":"$name"}]},
      "subtitle":{"runs":[{"text":"Gunnu"}]},
      "navigationEndpoint":{"browseEndpoint":{"browseId":"$id"}}
    }}"""

    private fun fixture(name: String) = Json.parseToJsonElement(
        checkNotNull(javaClass.getResource("/innertube/$name")).readText(),
    ).jsonObject
}
