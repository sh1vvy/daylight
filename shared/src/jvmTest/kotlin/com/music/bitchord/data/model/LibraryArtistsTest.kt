/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryArtistsTest {
    @Test fun matchesFeaturedCreditsAndTopicAliasesButNeverSubstrings() {
        val songs = listOf(
            Song("one", "One", "Lead", null, artistId = "UClead"),
            Song("two", "Two", "Lead & Guest", null, artists = listOf(ArtistRef("Lead", "UCtopic"), ArtistRef("Guest", "UCguest"))),
            Song("three", "Three", "Lead tribute", null, artistId = "UCtribute"),
            Song("four", "Four", "Other", null, artistId = "UCother"),
        )
        assertEquals(listOf("one", "two"), likedSongsForArtist(songs, ArtistRef("Lead", "UClead")).map { it.videoId })
        assertEquals(listOf("two"), likedSongsForArtist(songs, ArtistRef("Guest", "UCguest")).map { it.videoId })
    }
    @Test fun nameOnlyCreditsAndAccentedNamesRemainAvailableOffline() {
        val song = Song("one", "One", "ADÉLA", null)
        assertEquals(listOf(song), likedSongsForArtist(listOf(song, song), ArtistRef("Adéla")))
        assertTrue(likedSongsForArtist(listOf(song), ArtistRef(" ")).isEmpty())
    }
}
