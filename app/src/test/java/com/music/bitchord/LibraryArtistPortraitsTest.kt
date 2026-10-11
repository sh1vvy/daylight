/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord

import com.music.bitchord.data.model.ShelfItem
import com.sh1vvy.daylight.library.LibraryArtistPortraits
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicInteger

class LibraryArtistPortraitsTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun artist(id: String = "UCone") = ShelfItem("Artist", "", null, null, id)

    @Test fun freshPortraitSurvivesRestartWithoutAnotherProviderRequest() = runBlocking {
        val directory = temporary.newFolder()
        var requests = 0
        val fetch: suspend (ShelfItem) -> Result<String?> = { requests++; Result.success("https://portrait") }
        assertEquals("https://portrait", LibraryArtistPortraits(directory, fetch, { "en" }, { 1000L }).artworkFor(artist()))
        assertEquals("https://portrait", LibraryArtistPortraits(directory, fetch, { "en" }, { 2000L }).artworkFor(artist()))
        assertEquals(1, requests)
    }
    @Test fun failureRetainsStalePortraitAndBacksOffInsteadOfRequestingOnEveryVisit() = runBlocking {
        val directory = temporary.newFolder()
        var now = 1000L
        var requests = 0
        var working = true
        val cache = LibraryArtistPortraits(directory, { requests++; if (working) Result.success("https://portrait")
            else Result.failure(IllegalStateException("offline")) }, { "en" }, { now })
        cache.artworkFor(artist())
        now += 8 * 24 * 60 * 60_000L; working = false
        assertEquals("https://portrait", cache.artworkFor(artist()))
        assertEquals("https://portrait", cache.artworkFor(artist()))
        assertEquals(2, requests)
    }
    @Test fun visibleRequestsAreBoundedAndCancellationReleasesThePermit() = runBlocking {
        val active = AtomicInteger(); val maximum = AtomicInteger()
        val firstEntered = CompletableDeferred<Unit>()
        val cache = LibraryArtistPortraits(temporary.newFolder(), {
            val count = active.incrementAndGet(); maximum.updateAndGet { maxOf(it, count) }; firstEntered.complete(Unit)
            try { delay(30); Result.success("https://portrait/${it.browseId}") } finally { active.decrementAndGet() }
        }, { "en" })
        val cancelled = launch { cache.artworkFor(artist("UCcancel")) }
        firstEntered.await(); cancelled.cancelAndJoin()
        (1..12).map { async { cache.artworkFor(artist("UC$it")) } }.awaitAll()
        assertTrue(maximum.get() <= 2); assertEquals(0, active.get())
    }
}
