/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.library

import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SnapshotCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private class TextCodec : SnapshotCodec<String> {
        var encodes = 0
        var decodes = 0
        override fun encode(value: String) = value.toByteArray().also { encodes++ }
        override fun decode(bytes: ByteArray) = bytes.toString(Charsets.UTF_8).also { decodes++ }
        override fun weight(value: String) = value.length
    }
    private fun key(id: String, account: String = "account") = SnapshotKey(account, id)
    private fun files(root: File) = root.walkTopDown().filter { it.isFile && it.extension == "snapshot" }.toList()

    @Test fun targetedWarmReadsAndUnchangedWritesDoNotDecodeOrReencodeOtherCollections() = runBlocking {
        val root = temporary.newFolder()
        val codec = TextCodec()
        val cache = SnapshotCache(root, codec)
        repeat(20) { cache.write(key("playlist-$it"), "row-$it") }
        assertEquals(20, codec.encodes)
        val originals = files(root).associate { it.path to it.readBytes().toList() }
        repeat(100) { assertEquals("row-10", cache.read(key("playlist-10"))) }
        cache.write(key("playlist-10"), "row-10")
        assertEquals(20, codec.encodes)
        assertEquals(0, codec.decodes)
        cache.write(key("playlist-10"), "updated")
        assertEquals(21, codec.encodes)
        assertEquals(1, files(root).count { originals[it.path] != it.readBytes().toList() })
        val coldCodec = TextCodec()
        val restarted = SnapshotCache(root, coldCodec)
        assertEquals("updated", restarted.read(key("playlist-10")))
        repeat(100) { restarted.read(key("playlist-10")) }
        assertEquals(1, coldCodec.decodes)
    }

    @Test fun profileSeparationRemovalAndCorruptionNeverAffectAnotherProfile() = runBlocking {
        val root = temporary.newFolder()
        val first = SnapshotCache(root, TextCodec())
        first.write(key("liked", "private-account-one"), "one")
        first.write(key("liked", "private-account-two"), "two")
        assertTrue(files(root).all { !it.path.contains("private-account") })
        assertNull(first.read(key("liked", "third-account")))
        first.removeAccount("private-account-one")
        assertNull(first.peek(key("liked", "private-account-one")))
        assertEquals("two", SnapshotCache(root, TextCodec()).read(key("liked", "private-account-two")))
        val remaining = files(root).single()
        val bytes = remaining.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        remaining.writeBytes(bytes)
        assertNull(SnapshotCache(root, TextCodec()).read(key("liked", "private-account-two")))
    }

    @Test fun memoryRowsAndDiskEntriesAreBoundedAndOversizedUpdateRetainsPreviousValue() = runBlocking {
        val root = temporary.newFolder()
        val budget = CacheBudget(diskBytes = 350, entryBytes = 150, memoryRows = 20, memoryEntries = 2, entriesPerAccount = 4, diskEntries = 5)
        val cache = SnapshotCache(root, TextCodec(), budget)
        repeat(20) { cache.write(key("list-$it", "profile-$it"), "a".repeat(20)) }
        assertTrue(files(root).size <= 5)
        assertTrue(root.listFiles()!!.size <= 5)
        assertTrue(files(root).sumOf(File::length) <= 350)
        assertEquals(1, (0 until 20).count { cache.peek(key("list-$it", "profile-$it")) != null })
        assertFalse(cache.write(key("list-19", "profile-19"), "b".repeat(151)))
        assertEquals("a".repeat(20), SnapshotCache(root, TextCodec(), budget).read(key("list-19", "profile-19")))
    }

    @Test fun concurrentUpdatesAreCompleteAfterRestartAndAccountReadsKeepRepeatedRows() = runBlocking {
        val root = temporary.newFolder()
        val cache = SnapshotCache(root, TextCodec())
        (0 until 16).map { n -> async { cache.write(key("list-$n"), "same,same,$n") } }.forEach { it.await() }
        val restored = SnapshotCache(root, TextCodec()).readAccount("account")
        assertEquals((0 until 16).map { "same,same,$it" }.toSet(), restored.toSet())
        assertTrue(root.walkTopDown().none { it.extension == "pending" })
    }

    @Test fun failedCodecCannotReplaceOrPublishThePreviousUsableList() = runBlocking {
        val root = temporary.newFolder()
        val codec = object : SnapshotCodec<String> {
            override fun encode(value: String): ByteArray { check(value != "fail"); return value.toByteArray() }
            override fun decode(bytes: ByteArray) = bytes.toString(Charsets.UTF_8)
            override fun weight(value: String) = 1
        }
        val cache = SnapshotCache(root, codec)
        cache.write(key("liked"), "usable")
        assertFailsWith<IllegalStateException> { cache.write(key("liked"), "fail") }
        assertEquals("usable", cache.peek(key("liked")))
        assertEquals("usable", SnapshotCache(root, codec).read(key("liked")))
    }

    @Test fun likedMetadataHasRetentionPriorityWhileStillRespectingHardBudgets() = runBlocking {
        val root = temporary.newFolder()
        val budget = CacheBudget(diskBytes = 350, entryBytes = 150, memoryRows = 20, memoryEntries = 2, entriesPerAccount = 4, diskEntries = 5)
        val cache = SnapshotCache(root, TextCodec(), budget, preferredCollections = setOf("liked"))
        cache.write(key("liked"), "my favourites")
        repeat(30) { cache.write(key("other-$it"), "playlist") }
        assertEquals("my favourites", cache.peek(key("liked")))
        assertEquals("my favourites", SnapshotCache(root, TextCodec(), budget).read(key("liked")))
        assertTrue(files(root).size <= 4)
        assertTrue(files(root).sumOf(File::length) <= 350)
    }

    @Test fun responseInvalidatedDuringEncodingCannotReplaceThePreviousSnapshot() = runBlocking {
        val root = temporary.newFolder()
        var current = true
        val codec = object : SnapshotCodec<String> {
            override fun encode(value: String): ByteArray {
                if (value == "obsolete") current = false
                return value.toByteArray()
            }
            override fun decode(bytes: ByteArray) = bytes.toString(Charsets.UTF_8)
            override fun weight(value: String) = 1
        }
        val cache = SnapshotCache(root, codec)
        cache.write(key("library"), "usable")
        assertFalse(cache.write(key("library"), "obsolete") { current })
        assertEquals("usable", cache.peek(key("library")))
        assertEquals("usable", SnapshotCache(root, codec).read(key("library")))
        assertTrue(root.walkTopDown().none { it.extension == "pending" })
    }
}
