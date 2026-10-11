/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-10 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord

import com.music.bitchord.data.library.*
import com.music.bitchord.data.model.*
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CollectionMetadataCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private val song = Song("video", "Song", "Artist", "https://cover", "3:12", "UCART",
        listOf(ArtistRef("Artist", "UCART"), ArtistRef("Guest", "UCGUEST")), "MPREalbum", "Album",
        isVideo = false, isVideoOrigin = true, setVideoId = "occurrence", isExplicit = true)
    private fun snapshot(id: String = "VLLM", songs: List<Song> = listOf(song)) = CollectionSnapshot(
        id, "Collection", "Author", "https://header", PlaylistCreator("Author", "UCAUTHOR", "https://avatar"),
        songs, complete = true, updatedAt = 1_000, trackIds = songs.map { "recording-${it.videoId}" })

    @Test fun listSurvivesRestartWithCreditsFlagsAndRepeatedEntriesButWithoutQueueOrAudioState() = runBlocking {
        val directory = temporary.newFolder()
        val withPlayback = song.copy(localUri = "file:///audio.flac", playbackSource = "Private queue", queueEntryId = "queue-only")
        val original = snapshot(songs = listOf(withPlayback, song))
        CollectionMetadataCache(directory).save("youtube:account:profile", original)
        val restored = CollectionMetadataCache(directory).read("youtube:account:profile").single()
        assertEquals(listOf(song, song), restored.songs)
        assertEquals(original.creator, restored.creator)
        assertEquals(original.trackIds, restored.trackIds)
        assertTrue(restored.complete)
        val raw = directory.walkTopDown().single { it.isFile && it.extension == "snapshot" }.readText()
        assertFalse(raw.contains("audio.flac"))
        assertFalse(raw.contains("queue-only"))
        assertFalse(raw.contains("Private queue"))
        val warm = CollectionMetadataCache(directory)
        warm.save("youtube:account:profile", original)
        assertEquals("Warm cache is also catalogue-only", listOf(song, song), warm.readCollection("youtube:account:profile", "VLLM")!!.songs)
    }
    @Test fun googleProfilesAndSpotifySessionsNeverShareRowsOrSecretFileNames() = runBlocking {
        val directory = temporary.newFolder()
        val cache = CollectionMetadataCache(directory)
        cache.save("youtube:a:profile-one", snapshot())
        cache.save("youtube:a:profile-two", snapshot(songs = emptyList()))
        val spotify = spotifyMetadataScope("private-session-cookie")!!
        cache.save(spotify, snapshot("spotify:playlist:one"))
        assertTrue(cache.read("youtube:a:profile-two").single().songs.isEmpty())
        assertTrue(cache.read(spotifyMetadataScope("different-cookie")!!).isEmpty())
        assertTrue(cache.read("youtube:b:profile-one").isEmpty())
        assertEquals(listOf(song), cache.read("youtube:a:profile-one").single().songs)
        assertTrue(directory.walkTopDown().filter { it.isFile }.all { !it.name.contains("cookie") && !it.readText().contains("private-session-cookie") })
        assertNull(spotifyMetadataScope(""))
    }
    @Test fun onlyPlaybackMarksAPlaylistAndARefreshCannotLoseThatPlay() = runBlocking {
        val directory = temporary.newFolder()
        var clock = 90_000L
        val cache = CollectionMetadataCache(directory, now = { clock })
        val item = snapshot("spotify:playlist:one")
        cache.save("spotify:one", item)
        assertEquals(0L, cache.read("spotify:one").single().lastPlayedAt)
        coroutineScope {
            val play = async { cache.played("spotify:one", item.browseId) }
            val refresh = async { cache.save("spotify:one", item.copy(updatedAt = 2_000)) }
            play.await(); refresh.await()
        }
        assertEquals(clock, CollectionMetadataCache(directory).read("spotify:one").single().lastPlayedAt)
        clock += 1_000
        cache.played("spotify:one", item.browseId)
        assertEquals(90_000, cache.read("spotify:one").single().lastPlayedAt)
        cache.played("spotify:one", "never-opened")
        assertEquals(1, cache.read("spotify:one").size)
    }
    @Test fun verifiedEmptyAndPartialRemainDistinctAndFreshnessExpiresWithoutDeletingRows() = runBlocking {
        val cache = CollectionMetadataCache(temporary.newFolder())
        val empty = snapshot(songs = emptyList())
        cache.save("one", empty)
        assertTrue(cache.read("one").single().isFresh(1_001))
        assertFalse(empty.isFresh(301_001))
        assertFalse(empty.isFresh(999))
        assertFalse(empty.copy(complete = false).isFresh(1_001))
    }
    @Test fun damagedOrFutureSchemaDoesNotBreakBrowsingAndDiskUseStaysBounded() = runBlocking {
        val directory = temporary.newFolder()
        val cache = CollectionMetadataCache(directory, maxBytes = 2_000)
        repeat(8) { cache.save("identity-$it", snapshot()) }
        assertTrue(directory.walkTopDown().filter(File::isFile).sumOf(File::length) <= 2_000)
        directory.walkTopDown().filter(File::isFile).forEach { it.writeText("{broken") }
        assertTrue(CollectionMetadataCache(directory).read("identity-7").isEmpty())
        cache.removeScope("identity-7")
        assertTrue(cache.read("identity-7").isEmpty())
    }
    @Test fun playedShelvesMergeOnceWithoutTurningPlaylistCardsIntoQuickPickSongs() {
        val item = snapshot("spotify:playlist:one").shelfItem()
        val feed = listOf(HomeShelf("Recents", listOf(ShelfItem("Track", "Artist", null, "song", null))),
            HomeShelf("Listen again", listOf(item, ShelfItem("Album", "Artist", null, null, "MPREalbum"))))
        val merged = withPlayedCollections(feed, listOf(item, item))
        assertEquals(1, merged.count { it.title == "Listen again" })
        assertEquals(2, merged.last().items.size)
        assertEquals(listOf("song"), homeRecentTracks(merged).map { it.videoId })
        assertEquals(listOf(item), withPlayedCollections(feed.take(1), listOf(item)).last().items)
        assertSame(feed, withPlayedCollections(feed, emptyList()))
    }
    @Test fun sizeLimitEvictsWholeOldCollectionsAndNeverTruncatesAnOversizedList() = runBlocking {
        val directory = temporary.newFolder()
        val cache = CollectionMetadataCache(directory, maxBytes = 1_400)
        val first = snapshot("first", listOf(song.copy(title = "a".repeat(500))))
        val second = snapshot("second", listOf(song.copy(title = "b".repeat(500)))).copy(updatedAt = 2_000)
        cache.save("one", first)
        cache.save("one", second)
        assertEquals(listOf(second), CollectionMetadataCache(directory).read("one"))
        cache.save("one", snapshot("oversized", listOf(song.copy(title = "c".repeat(3_000)))))
        assertEquals(listOf(second), CollectionMetadataCache(directory).read("one"))
        assertTrue(directory.walkTopDown().filter(File::isFile).sumOf(File::length) <= 1_400)
    }

    @Test fun legacyBundlesMigrateOnceAndAnUpdateOnlyWritesTheChangedCollection() = runBlocking {
        val directory = temporary.newFolder()
        val scope = "youtube:migration:profile"
        val name = java.security.MessageDigest.getInstance("SHA-256").digest(scope.toByteArray()).joinToString("") { "%02x".format(it) }
        val legacy = File(directory, "$name.json")
        legacy.writeText("""{"version":1,"collections":[{"id":"VLLM","title":"Liked","subtitle":"","cover":null,"creator":null,"songs":[],"complete":true,"updated":1000,"played":0},{"id":"playlist","title":"Saved playlist","subtitle":"","cover":null,"creator":null,"songs":[],"complete":true,"updated":1000,"played":0}]}""")
        val cache = CollectionMetadataCache(directory)
        assertEquals(setOf("VLLM", "playlist"), cache.read(scope).map { it.browseId }.toSet())
        assertFalse(legacy.exists())
        val originals = directory.walkTopDown().filter { it.isFile }.associate { it.path to it.readBytes().toList() }
        cache.save(scope, cache.readCollection(scope, "playlist")!!.copy(title = "Renamed playlist"))
        assertEquals(1, directory.walkTopDown().filter { it.isFile }.count { originals[it.path] != it.readBytes().toList() })
        assertEquals("Liked", CollectionMetadataCache(directory).readCollection(scope, "VLLM")!!.title)
        assertEquals("Renamed playlist", CollectionMetadataCache(directory).readCollection(scope, "playlist")!!.title)
    }
}
