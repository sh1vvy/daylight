package com.music.bitchord.data.innertube

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PlaylistShelfEntriesTest {
    @Test
    fun repeatedSongsKeepTheirSeparatePlaylistEntriesInOrder() {
        // The same recording can intentionally appear twice in a playlist.
        // Its two entry IDs are needed to remove the occurrence the user chose.
        val response = Json.parseToJsonElement(
            """
            {
              "continuationContents": {
                "musicPlaylistShelfContinuation": {
                  "contents": [
                    ${row("same-recording", "First occurrence", "entry-first")},
                    ${row("middle-recording", "Middle song", "entry-middle")},
                    ${row("same-recording", "Second occurrence", "entry-second")},
                    ${row("same-recording", "Duplicate renderer", "entry-second")}
                  ],
                  "continuations": [{ "nextContinuationData": { "continuation": "actual-next-page" } }]
                }
              }
            }
            """.trimIndent(),
        )
        val page = assertNotNull(InnertubeParser.parsePlaylistShelf(response))
        assertEquals(listOf("same-recording", "middle-recording", "same-recording"), page.songs.map { it.videoId })
        assertEquals(listOf("entry-first", "entry-middle", "entry-second"), page.songs.map { it.setVideoId })
        assertEquals(listOf("First occurrence", "Middle song", "Second occurrence"), page.songs.map { it.title })
        assertEquals("actual-next-page", page.continuation)
    }

    @Test
    fun rowsWithoutEntryIdsRemainReadableWithoutPromotingSuggestionsToMembership() {
        val response = Json.parseToJsonElement(
            """
            {
              "contents": {
                "twoColumnBrowseResultsRenderer": {
                  "secondaryContents": {
                    "sectionListRenderer": {
                      "contents": [
                        { "musicPlaylistShelfRenderer": {
                          "contents": [
                            ${row("saved-recording", "Saved song")},
                            ${row("saved-recording", "Same row rendered again")}
                          ]
                        } },
                        { "musicShelfRenderer": {
                          "title": { "runs": [{ "text": "Suggestions" }] },
                          "contents": [${row("suggested-recording", "Suggested song")}],
                          "continuations": [{ "nextContinuationData": { "continuation": "suggestion-refresh" } }]
                        } }
                      ]
                    }
                  }
                }
              }
            }
            """.trimIndent(),
        )
        val page = assertNotNull(InnertubeParser.parsePlaylistShelf(response))
        assertEquals(listOf("saved-recording"), page.songs.map { it.videoId })
        assertEquals(listOf("suggested-recording"), page.suggested.map { it.videoId })
        assertEquals(null, page.continuation)
    }

    private fun row(videoId: String, title: String, entryId: String? = null): String = """
        { "musicResponsiveListItemRenderer": {
          "playlistItemData": {
            "videoId": "$videoId"${entryId?.let { ", \"playlistSetVideoId\": \"$it\"" }.orEmpty()}
          },
          "flexColumns": [{ "musicResponsiveListItemFlexColumnRenderer": {
            "text": { "runs": [{ "text": "$title" }] }
          } }]
        } }
    """.trimIndent()
}
