package com.music.bitchord.ui

import com.music.bitchord.data.model.HomeFeed
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedRefreshTest {
    private fun shelf(title: String, id: String = title) = HomeShelf(
        title = title,
        items = listOf(ShelfItem(title = id, subtitle = "Artist", thumbnailUrl = null, videoId = id, browseId = null)),
    )
    private val oldRecents = shelf("Recents", "old")
    private val newRecents = shelf("Recents", "new")
    private val listenAgain = shelf("Listen again")
    private val feed = HomeFeed(listOf(listenAgain), "next-page")

    @Test
    fun `a fast core response cannot publish Listen Again before slow Recents`() = runBlocking {
        val recent = CompletableDeferred<Result<HomeShelf?>>()
        val refresh = async { refreshedHomeFeed(listOf(oldRecents), { Result.success(feed) }, { recent.await() }) }
        yield()
        assertFalse(refresh.isCompleted)
        recent.complete(Result.success(newRecents))
        assertEquals(listOf(newRecents, listenAgain), refresh.await().getOrThrow().shelves)
    }

    @Test
    fun `a fast history response waits for the matching core response`() = runBlocking {
        val core = CompletableDeferred<Result<HomeFeed>>()
        val refresh = async { refreshedHomeFeed(listOf(oldRecents), { core.await() }, { Result.success(newRecents) }) }
        yield()
        assertFalse(refresh.isCompleted)
        core.complete(Result.success(feed))
        val result = refresh.await().getOrThrow()
        assertEquals(listOf(newRecents, listenAgain), result.shelves)
        assertEquals("next-page", result.continuation)
    }

    @Test
    fun `history failure keeps the visible Recents shelf in its leading place`() = runBlocking {
        val result = refreshedHomeFeed(listOf(oldRecents), { Result.success(feed) }, { Result.failure(Exception("offline")) })
        assertEquals(listOf(oldRecents, listenAgain), result.getOrThrow().shelves)
    }

    @Test
    fun `confirmed empty history removes stale Recents without an intermediate feed`() = runBlocking {
        val result = refreshedHomeFeed(listOf(oldRecents), { Result.success(feed) }, { Result.success(null) })
        assertEquals(listOf(listenAgain), result.getOrThrow().shelves)
    }

    @Test
    fun `history wins over core duplicates and Listen Again may share its songs`() = runBlocking {
        val sharedSongShelf = shelf("Listen again", "new")
        val duplicateCore = HomeFeed(listOf(sharedSongShelf, oldRecents, shelf("LISTEN AGAIN")), null)
        val result = refreshedHomeFeed(emptyList(), { Result.success(duplicateCore) }, { Result.success(newRecents) })
        assertEquals(listOf(newRecents, sharedSongShelf), result.getOrThrow().shelves)
    }

    @Test
    fun `guests never request account history and failed core does not replace the feed`() = runBlocking {
        assertEquals(feed, refreshedHomeFeed(emptyList(), { Result.success(feed) }, null).getOrThrow())
        val failed = refreshedHomeFeed(listOf(oldRecents), { Result.failure(Exception("offline")) }, { Result.success(newRecents) })
        assertTrue(failed.isFailure)
    }
}
