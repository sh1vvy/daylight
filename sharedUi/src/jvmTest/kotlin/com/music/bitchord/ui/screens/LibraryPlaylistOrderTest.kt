package com.music.bitchord.ui.screens

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.settings.LibrarySort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LibraryPlaylistOrderTest {
    @Test fun `system likes disappear from playlists without hiding user playlists with the same name`() {
        val playlists = shelf(item(YtMusicRepository.LIKED_MUSIC, "Liked Music"),
            item("LM", "Musique aimée"), item("VLUSER", "Liked Music"),
            item("local:playlist:mine", "Liked Music"))
        assertEquals(listOf("VLUSER", "local:playlist:mine"), playlists.withoutLikedMusic().ids())
    }

    @Test fun `filtering system likes preserves order and ordinary provider duplicates`() {
        val playlists = shelf(item("VLONE"), item("VLLM"), item("VLTWO"), item("VLONE"))
        assertEquals(listOf("VLONE", "VLTWO", "VLONE"), playlists.withoutLikedMusic().ids())
    }

    @Test fun `localized playlist shelves with raw likes still use the playlist layout`() {
        val playlists = HomeShelf("Listes de lecture", listOf(item("LM"), item("VLPLONE")))
        assertEquals(true, playlists.isPlaylistLibraryShelf())
        assertEquals(listOf("VLPLONE"), playlists.withoutLikedMusic().ids())
    }

    private fun item(id: String, title: String = id) = ShelfItem(
        title = title,
        subtitle = "",
        thumbnailUrl = null,
        videoId = null,
        browseId = id,
    )

    private fun shelf(vararg items: ShelfItem) = HomeShelf(YtMusicRepository.PLAYLISTS_SHELF, items.toList())
    private fun HomeShelf.ids() = items.map { it.browseId }

    @Test
    fun `new playlists stay newest first even when their names sort in the opposite order`() {
        val playlists = shelf(item("VLOLD", "Older"), item("VLFIRST", "A first"), item("VLSECOND", "Z second"))
        LibrarySort.entries.forEach { sort ->
            assertEquals(
                listOf("VLSECOND", "VLFIRST", "VLOLD"),
                playlists.orderedForLibrary(emptyList(), sort, listOf("SECOND", "FIRST")).ids(),
            )
        }
    }

    @Test
    fun `provider refresh and rename cannot move a created playlist back into alphabetical order`() {
        val created = listOf("LATEST", "EARLIER")
        val before = shelf(item("VLEARLIER", "B"), item("VLLATEST", "Z"), item("VLOLD", "A"))
        val refreshed = shelf(item("VLOLD", "A"), item("VLLATEST", "Renamed"), item("VLEARLIER", "B"))
        assertEquals(before.orderedForLibrary(emptyList(), LibrarySort.DEFAULT, created).ids(),
            refreshed.orderedForLibrary(emptyList(), LibrarySort.TITLE_ASC, created).ids())
    }

    @Test
    fun `new playlist comes before deliberate pins and older pins keep their order`() {
        val playlists = shelf(item("VLOTHER"), item("VLPIN2"), item("VLNEW"), item("VLPIN1"))
        assertEquals(
            listOf("VLNEW", "VLPIN1", "VLPIN2", "VLOTHER"),
            playlists.orderedForLibrary(listOf("VLPIN1", "VLPIN2"), LibrarySort.DEFAULT, listOf("NEW")).ids(),
        )
    }

    @Test
    fun `albums and artists never acquire playlist creation or pin priorities`() {
        val albums = HomeShelf("Albums", listOf(item("MPREbSECOND", "Z"), item("MPREbFIRST", "A")))
        assertEquals(albums.ids(), albums.orderedForLibrary(
            listOf("MPREbFIRST"), LibrarySort.DEFAULT, listOf("MPREbFIRST")).ids())
        assertEquals(listOf("MPREbFIRST", "MPREbSECOND"), albums.orderedForLibrary(
            emptyList(), LibrarySort.TITLE_ASC, listOf("MPREbSECOND")).ids())
    }

    @Test
    fun `a new local playlist precedes downloaded albums on device and in show all`() {
        val onDevice = HomeShelf("On device", listOf(
            item("download:album", "A downloaded album"),
            item("local:playlist:old", "B local"),
            item("local:playlist:new", "Z latest local"),
        ))
        assertEquals(
            listOf("local:playlist:new", "local:playlist:old", "download:album"),
            onDevice.orderedForLibrary(emptyList(), LibrarySort.TITLE_ASC,
                listOf("local:playlist:new", "local:playlist:old")).ids(),
        )
    }

    @Test
    fun `missing creation ids do not fabricate a date or discard duplicate provider cards`() {
        val repeated = item("VLREPEAT", "Repeat")
        val playlists = shelf(repeated, item("VLOTHER", "Other"), repeated.copy(subtitle = "Other artwork"))
        assertEquals(playlists.items, playlists.orderedForLibrary(
            emptyList(), LibrarySort.DEFAULT, listOf("DELETED")).items)
        assertEquals(listOf("VLREPEAT", "VLREPEAT", "VLOTHER"), playlists.orderedForLibrary(
            emptyList(), LibrarySort.DEFAULT, listOf("REPEAT")).ids())
    }

    @Test
    fun `without creation history platform sorting remains unchanged`() {
        val playlists = shelf(item("VLZ", "Zulu"), item("VLA", "alpha"), item("VLH", "Hotel"))
        assertEquals(listOf("VLH", "VLZ", "VLA"), playlists.orderedForLibrary(
            listOf("VLH"), LibrarySort.DEFAULT, emptyList()).ids())
        assertEquals(listOf("VLA", "VLH", "VLZ"), playlists.orderedForLibrary(
            listOf("VLZ"), LibrarySort.TITLE_ASC, emptyList()).ids())
    }

    @Test
    fun `reveal tracks the actual newest create and never falls back after deletion or another shelf create`() {
        val playlists = shelf(item("VLFIRST"), item("VLSECOND"))
        assertEquals("SECOND", playlists.newestCreatedPlaylistId(listOf("SECOND", "FIRST")))
        assertEquals("SECOND", playlists.copy(items = playlists.items.reversed().map { it.copy(title = "Renamed") })
            .newestCreatedPlaylistId(listOf("SECOND", "FIRST")))
        assertNull(shelf(item("VLFIRST")).newestCreatedPlaylistId(listOf("SECOND", "FIRST")))
        assertNull(playlists.newestCreatedPlaylistId(listOf("local:playlist:new", "SECOND", "FIRST")))
    }

    @Test
    fun `existing playlist touched by a song addition outranks a newly created playlist in every sort`() {
        val playlists = shelf(item("VLNEW", "A newest creation"), item("VLOLD", "Z updated"), item("VLOTHER", "Other"))
        LibrarySort.entries.forEach { sort ->
            assertEquals(
                listOf("VLOLD", "VLNEW", "VLOTHER"),
                playlists.orderedForLibrary(listOf("VLNEW"), sort, listOf("OLD", "NEW")).ids(),
            )
        }
        assertEquals("OLD", playlists.newestCreatedPlaylistId(listOf("OLD", "NEW")))
    }

    @Test
    fun `localized remote playlist shelf and raw playlist cards use the same activity order`() {
        val localized = HomeShelf("Listes de lecture", listOf(
            item("PLnew", "A first alphabetically"), item("VLPLold", "Z recently updated"),
        ))
        assertEquals(true, localized.isPlaylistLibraryShelf())
        assertEquals(
            listOf("VLPLold", "PLnew"),
            localized.orderedForLibrary(emptyList(), LibrarySort.TITLE_ASC, listOf("PLold", "VLPLnew")).ids(),
        )
    }

    @Test
    fun `local activity survives localized mixed on-device shelves and live show-all snapshots`() {
        val device = HomeShelf("Sur cet appareil", listOf(
            item("local:playlist:new", "A newer creation"),
            item("download:album", "B album"),
            item("local:playlist:old", "Z recent addition"),
        ))
        assertEquals(false, device.isPlaylistLibraryShelf())
        val activity = listOf("local:playlist:old", "local:playlist:new")
        assertEquals(listOf("local:playlist:old", "local:playlist:new", "download:album"),
            device.orderedForLibrary(emptyList(), LibrarySort.TITLE_ASC, activity).ids())
        assertEquals("local:playlist:old", device.newestCreatedPlaylistId(activity))
    }

    @Test
    fun `catalogue albums backed by VLOLAK never become playlist shelves`() {
        val albums = HomeShelf("Albums", listOf(item("VLOLAKfirst", "A"), item("VLOLAKsecond", "Z")))
        assertEquals(false, albums.isPlaylistLibraryShelf())
        assertEquals(albums.ids(), albums.orderedForLibrary(
            emptyList(), LibrarySort.DEFAULT, listOf("OLAKsecond")).ids())
    }

    @Test
    fun `an updated remote playlist is first in its downloaded on-device show-all shelf too`() {
        val onDevice = HomeShelf("On device", listOf(
            item("local:playlist:sp_local_recent", "A local creation"),
            item("local:playlist:VLPLremote", "Z updated remote playlist"),
            item("local:playlist:MPREbalbum", "B downloaded album"),
        ))
        val activity = listOf("PLremote", "local:playlist:sp_local_recent")
        assertEquals(
            listOf("local:playlist:VLPLremote", "local:playlist:sp_local_recent", "local:playlist:MPREbalbum"),
            onDevice.orderedForLibrary(emptyList(), LibrarySort.TITLE_ASC, activity).ids(),
        )
        assertEquals("PLremote", onDevice.newestCreatedPlaylistId(activity))
    }
}
