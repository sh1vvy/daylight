package com.music.bitchord

import com.music.bitchord.data.spotify.parsePlaylistPage
import com.music.bitchord.data.spotify.parseTrackPage
import com.music.bitchord.data.spotify.parseSpotifyPlaylistMetadata
import com.music.bitchord.data.model.CreatorProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyLibraryTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun playlistsIncludeFolderLeaves() {
        val root = json.parseToJsonElement(
            """
            {"data":{"me":{"libraryV3":{"totalCount":1,"items":[
              {"item":{"__typename":"PlaylistResponseWrapper","_uri":"spotify:playlist:abc","data":{
                "__typename":"Playlist","name":"Road","ownerV2":{"data":{"name":"Amit"}},
                "images":{"items":[{"sources":[{"url":"https://i.scdn.co/x"}]}]}
              }}}
            ]}}}}
            """.trimIndent(),
        ).jsonObject
        val (items, total) = parsePlaylistPage(root)
        assertEquals(1, total)
        assertEquals("abc", items.single().id)
        assertEquals("Road", items.single().name)
        assertEquals("Amit", items.single().owner)
        assertEquals("https://i.scdn.co/x", items.single().imageUrl)
    }

    @Test
    fun tracksReadTitleArtistAndDuration() {
        val root = json.parseToJsonElement(
            """
            {"data":{"playlistV2":{"content":{"totalCount":1,"items":[
              {"itemV2":{"data":{
                "name":"Song","uri":"spotify:track:t1",
                "artists":{"items":[{"uri":"spotify:artist:a","profile":{"name":"Artist"}}]},
                "albumOfTrack":{"name":"Album","coverArt":{"sources":[{"url":"https://img"}]}},
                "duration":{"totalMilliseconds":180000}
              }}}
            ]}}}}
            """.trimIndent(),
        ).jsonObject
        val track = parseTrackPage(root).first.single()
        assertEquals("t1", track.id)
        assertEquals("Song", track.title)
        assertEquals("Artist", track.artist)
        assertEquals("Album", track.album)
        assertEquals(180000, track.durationMs)
        assertEquals("https://img", track.imageUrl)
    }

    @Test
    fun playlistHeaderSeparatesOwnerAvatarFromCoverAndPreservesActualAccountId() {
        val root = json.parseToJsonElement(
            """{"data":{"playlistV2":{
              "ownerV2":{"data":{"name":"Amit","uri":"spotify:user:amit",
                "avatar":{"sources":[{"url":"https://small-avatar","width":40},{"url":"https://real-avatar","width":300}]}
              }},
              "images":{"items":[{"sources":[{"url":"https://playlist-cover","width":640}]}]}
            }}}""",
        ).jsonObject
        val metadata = parseSpotifyPlaylistMetadata(root)
        assertEquals("https://playlist-cover", metadata.coverUrl)
        assertEquals("Amit", metadata.creator?.name)
        assertEquals("spotify:user:amit", metadata.creator?.browseId)
        assertEquals("https://real-avatar", metadata.creator?.thumbnailUrl)
        assertEquals(CreatorProvider.SPOTIFY, metadata.creator?.provider)
    }

    @Test
    fun missingPlaylistOwnerDoesNotInventCreatorOrReuseCover() {
        val root = json.parseToJsonElement(
            """{"data":{"playlistV2":{"images":{"items":[{"sources":[{"url":"https://cover"}]}]}}}}""",
        ).jsonObject
        val metadata = parseSpotifyPlaylistMetadata(root)
        assertEquals("https://cover", metadata.coverUrl)
        assertNull(metadata.creator)
    }
}
