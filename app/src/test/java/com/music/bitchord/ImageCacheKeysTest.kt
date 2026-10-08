package com.music.bitchord

import com.music.bitchord.data.remote.ImageCacheKeys
import com.music.bitchord.data.smb.SmbAuth
import com.music.bitchord.data.webdav.WebDavAuth
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
    fun configuredWebDavAndDerivedArtworkUseTheSamePrivateIdentity() {
        val oldHost = WebDavAuth.host
        val oldHeader = WebDavAuth.authHeader
        try {
            WebDavAuth.update("music.example", "Basic private-secret")
            val url = "https://music.example/cover.jpg"
            assertEquals(ImageCacheKeys.forRequest(url, "Basic private-secret"), ImageCacheKeys.forUrl(url))
            assertEquals("https://other.example/cover.jpg", ImageCacheKeys.forUrl("https://other.example/cover.jpg"))
            WebDavAuth.update("music.example", "Basic different-secret")
            assertNotEquals(ImageCacheKeys.forRequest(url, "Basic private-secret"), ImageCacheKeys.forUrl(url))
        } finally { WebDavAuth.update(oldHost, oldHeader) }
    }

    @Test
    fun smbArtworkChangesCachePartitionWhenAccountOrShareChanges() {
        val oldUser = SmbAuth.username
        val oldPassword = SmbAuth.password
        val oldShare = SmbAuth.share
        try {
            val url = "smb://server/Music/cover.jpg"
            SmbAuth.username = "one"
            SmbAuth.password = "private-secret"
            val first = ImageCacheKeys.forUrl(url)
            SmbAuth.username = "two"
            assertNotEquals(first, ImageCacheKeys.forUrl(url))
            SmbAuth.username = "one"
            SmbAuth.share = "Other"
            assertNotEquals(first, ImageCacheKeys.forUrl(url))
            assertFalse(first.contains("private-secret"))
        } finally {
            SmbAuth.username = oldUser
            SmbAuth.password = oldPassword
            SmbAuth.share = oldShare
        }
    }

    @Test
    fun decodedBitmapBudgetStaysProportionalOnSmallHeapsAndCappedOnLargeHeaps() {
        assertEquals(25_600_000L, ImageCacheKeys.memoryBudget(128_000_000L))
        assertEquals(64L * 1024 * 1024, ImageCacheKeys.memoryBudget(1024L * 1024 * 1024))
    }
}
