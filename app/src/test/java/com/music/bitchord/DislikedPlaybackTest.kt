package com.music.bitchord

import com.music.bitchord.data.DislikeStorage
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.playback.dislikedQueueIndices
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class DislikedPlaybackTest {
    private class MemoryDisk : DislikeStorage {
        val saved = mutableMapOf<String, Set<String>>()
        override fun read(scope: String) = saved[scope].orEmpty()
        override fun write(scope: String, videoIds: Set<String>) { saved[scope] = videoIds.toSet() }
    }
    @After fun cleanup() { LikeState.installStorage(null, null); LikeState.clear() }

    @Test fun `dislike survives process recreation but not an account switch`() {
        val disk = MemoryDisk()
        LikeState.installStorage(disk, "account:personal")
        LikeState.set("rejected", LikeStatus.DISLIKE)
        LikeState.set("favourite", LikeStatus.LIKE)
        LikeState.clear()
        LikeState.installStorage(disk, "account:personal")
        assertTrue(LikeState.isDisliked("rejected"))
        assertFalse(LikeState.isDisliked("favourite"))
        LikeState.selectScope("account:brand")
        assertFalse(LikeState.isDisliked("rejected"))
        LikeState.set("different", LikeStatus.DISLIKE)
        LikeState.selectScope("account:personal")
        assertTrue(LikeState.isDisliked("rejected"))
        assertFalse(LikeState.isDisliked("different"))
    }
    @Test fun `undo or like clears the durable dislike`() {
        val disk = MemoryDisk()
        LikeState.installStorage(disk, null)
        LikeState.set("a", LikeStatus.DISLIKE)
        LikeState.set("a", LikeStatus.INDIFFERENT)
        LikeState.installStorage(disk, null)
        assertFalse(LikeState.isDisliked("a"))
        LikeState.set("a", LikeStatus.DISLIKE)
        LikeState.set("a", LikeStatus.LIKE)
        LikeState.installStorage(disk, null)
        assertFalse(LikeState.isDisliked("a"))
    }
    @Test fun `refreshing liked lists cannot undo a restored dislike`() {
        val disk = MemoryDisk()
        LikeState.installStorage(disk, "a")
        LikeState.set("song", LikeStatus.DISLIKE)
        LikeState.installStorage(disk, "a")
        LikeState.seedLiked(setOf("song"))
        LikeState.rememberStated("song", LikeStatus.LIKE)
        assertTrue(LikeState.isDisliked("song"))
    }
    @Test fun `prune implicit entries but preserve explicitly selected and explicitly queued songs`() {
        val ids = listOf("blocked", "selected-blocked", "blocked", "fresh", "manual-blocked")
        val rejected = setOf("blocked", "selected-blocked", "manual-blocked")
        assertEquals(listOf(0, 2), dislikedQueueIndices(ids, 1,
            { if (it == 4) QueueTier.USER_QUEUE else QueueTier.CONTEXT }, { it in rejected }))
        assertEquals(listOf(0, 1, 2, 4), dislikedQueueIndices(ids, -1, { QueueTier.AUTOPLAY }, { it in rejected }))
    }
    @Test fun `only disliking the currently playing song skips it`() {
        assertTrue(shouldSkipAfterDislike(LikeStatus.INDIFFERENT, "playing", "playing"))
        assertFalse(shouldSkipAfterDislike(LikeStatus.DISLIKE, "playing", "playing"))
        assertFalse(shouldSkipAfterDislike(LikeStatus.LIKE, "different", "playing"))
    }
}
