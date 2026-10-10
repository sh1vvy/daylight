package com.music.bitchord

import com.music.bitchord.data.remote.ImageCacheKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ImageCacheKeysTest {
    @Test
    fun publicAndLocalArtworkKeepTheirExistingCacheKeys() {
        assertEquals("content://media/external/audio/albumart/42", ImageCacheKeys.forUrl("content://media/external/audio/albumart/42"))
        assertEquals("https://i.ytimg.com/cover.jpg", ImageCacheKeys.forUrl("https://i.ytimg.com/cover.jpg"))
    }

    @Test
    fun privateHttpArtworkIsPartitionedByCredentialWithoutLeakingItInTheKey() {
        val url = "https://music.example/cover.jpg"
        val first = ImageCacheKeys.forRequest(url, "Basic first-secret")
        val second = ImageCacheKeys.forRequest(url, "Basic second-secret")
        assertNotEquals(first, second)
        assertEquals(first, ImageCacheKeys.forRequest(url, "Basic first-secret"))
        assertFalse(first.contains("first-secret"))
        assertNotEquals(url, first)
    }

    @Test
    fun decodedBitmapBudgetStaysProportionalOnSmallHeapsAndCappedOnLargeHeaps() {
        assertEquals(25_600_000L, ImageCacheKeys.memoryBudget(128_000_000L))
        assertEquals(64L * 1024 * 1024, ImageCacheKeys.memoryBudget(1024L * 1024 * 1024))
    }
}
