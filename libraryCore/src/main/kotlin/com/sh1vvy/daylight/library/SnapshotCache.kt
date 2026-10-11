/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.library

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The boundary keeps this implementation independent of any app/provider's object model. */
interface SnapshotCodec<T> {
    fun encode(value: T): ByteArray
    fun decode(bytes: ByteArray): T
    fun weight(value: T): Int
}

data class SnapshotKey(val accountScope: String, val collectionId: String) {
    init { require(accountScope.isNotBlank() && collectionId.isNotBlank()) }
}

data class CacheBudget(
    val diskBytes: Long = 24L * 1024 * 1024,
    val entryBytes: Int = 8 * 1024 * 1024,
    val memoryRows: Int = 12_000,
    val memoryEntries: Int = 32,
    val entriesPerAccount: Int = 24,
    val diskEntries: Int = 96,
) {
    init { require(diskBytes > 0 && entryBytes > 0 && memoryRows > 0 && memoryEntries > 0 && entriesPerAccount > 0 && diskEntries > 0) }
}

/**
 * One file per collection; warm reads do no IO and a write never serializes other collections.
 * Every disk operation is dispatched away from the caller. Atomic replacement and a checksum
 * keep interrupted/damaged writes from publishing partial lists. Eviction removes whole entries.
 */
class SnapshotCache<T>(
    private val directory: File,
    private val codec: SnapshotCodec<T>,
    private val budget: CacheBudget = CacheBudget(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
    preferredCollections: Set<String> = emptySet(),
) {
    private data class Address(val account: String, val entry: String)
    private data class Warm<T>(val value: T, val rows: Int)
    private val diskLock = Mutex()
    private val warmLock = Any()
    private val warm = LinkedHashMap<Address, Warm<T>>(16, .75f, true)
    private var retainedRows = 0
    private val preferred = preferredCollections.mapTo(HashSet(), ::digest)

    fun peek(key: SnapshotKey): T? = synchronized(warmLock) { warm[address(key)]?.value }

    suspend fun read(key: SnapshotKey): T? {
        peek(key)?.let { return it }
        return withContext(io) { diskLock.withLock { readLocked(address(key)) } }
    }

    suspend fun readAccount(accountScope: String): List<T> = withContext(io) {
        diskLock.withLock {
            val account = digest(accountScope)
            files(File(directory, account)).sortedByDescending(File::lastModified)
                .take(budget.entriesPerAccount).mapNotNull { readLocked(Address(account, it.nameWithoutExtension)) }
        }
    }

    /** False means an oversized or obsolete entry; a previous usable copy stays untouched. */
    suspend fun write(key: SnapshotKey, value: T, isCurrent: () -> Boolean = { true }): Boolean = withContext(io) {
        diskLock.withLock {
            if (!isCurrent()) return@withLock false
            val address = address(key)
            if (synchronized(warmLock) { warm[address]?.value == value }) return@withLock true
            val payload = codec.encode(value)
            if (!isCurrent()) return@withLock false
            if (payload.size > minOf(budget.entryBytes.toLong(), budget.diskBytes - HEADER_BYTES)) return@withLock false
            val packed = pack(payload)
            val target = file(address)
            // Equality after a process restart also avoids a physical rewrite.
            if (target.isFile && target.length() == packed.size.toLong() && target.readBytes().contentEquals(packed)) {
                remember(address, value)
                return@withLock true
            }
            if (!target.parentFile.isDirectory && !target.parentFile.mkdirs()) throw IOException("Metadata directory unavailable")
            val temporary = File(target.parentFile, target.name + ".pending")
            try {
                FileOutputStream(temporary).use { it.write(packed); it.fd.sync() }
                if (!isCurrent()) return@withLock false
                try {
                    Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } finally { temporary.delete() }
            remember(address, value)
            trim(address)
            true
        }
    }

    suspend fun removeAccount(accountScope: String) = withContext(io) {
        diskLock.withLock {
            val account = digest(accountScope)
            synchronized(warmLock) {
                warm.keys.filter { it.account == account }.forEach(::forget)
            }
            val root = File(directory, account)
            if (root.exists() && !root.deleteRecursively()) throw IOException("Metadata removal failed")
        }
    }

    private fun readLocked(address: Address): T? {
        synchronized(warmLock) { warm[address]?.value?.let { return it } }
        val source = file(address)
        if (!source.isFile) return null
        val value = try {
            if (source.length() !in HEADER_BYTES.toLong()..(budget.entryBytes.toLong() + HEADER_BYTES)) throw IOException("Metadata size invalid")
            codec.decode(unpack(source.readBytes()))
        } catch (_: IOException) {
            source.delete(); return null
        } catch (_: IllegalArgumentException) {
            source.delete(); return null
        } catch (_: IllegalStateException) {
            source.delete(); return null
        }
        remember(address, value)
        return value
    }

    private fun remember(address: Address, value: T) = synchronized(warmLock) {
        forget(address)
        val rows = maxOf(1, codec.weight(value))
        if (rows > budget.memoryRows) return@synchronized
        warm[address] = Warm(value, rows)
        retainedRows += rows
        while (warm.size > budget.memoryEntries || retainedRows > budget.memoryRows) {
            forget(warm.keys.firstOrNull { it.entry !in preferred } ?: warm.keys.first())
        }
    }

    private fun forget(address: Address) { retainedRows -= warm.remove(address)?.rows ?: 0 }

    private fun trim(protected: Address) {
        fun evict(victim: File) {
            if (victim.delete()) {
                synchronized(warmLock) { forget(Address(victim.parentFile.name, victim.nameWithoutExtension)) }
                victim.parentFile.delete() // Only succeeds when the account directory is empty.
            }
        }
        val current = file(protected)
        val accountFiles = files(current.parentFile).sortedByDescending(File::lastModified)
        val extras = accountFiles.filterNot { it == current }
            .sortedWith(compareByDescending<File> { it.nameWithoutExtension in preferred }.thenByDescending(File::lastModified))
            .drop(budget.entriesPerAccount - 1)
        extras.forEach(::evict)
        val all = directory.listFiles()?.filter(File::isDirectory)?.flatMap(::files).orEmpty()
        var total = all.sumOf(File::length)
        var count = all.size
        all.filterNot { it == current }.sortedWith(compareBy<File> { it.nameWithoutExtension in preferred }.thenBy(File::lastModified)).forEach { old ->
            if (total > budget.diskBytes || count > budget.diskEntries) {
                val bytes = old.length()
                if (old.exists()) { evict(old); if (!old.exists()) { total -= bytes; count-- } }
            }
        }
    }

    private fun files(root: File): List<File> = root.listFiles { f -> f.isFile && f.extension == "snapshot" }?.toList().orEmpty()
    private fun address(key: SnapshotKey) = Address(digest(key.accountScope), digest(key.collectionId))
    private fun file(address: Address) = File(File(directory, address.account), address.entry + ".snapshot")

    private fun pack(payload: ByteArray): ByteArray = ByteArrayOutputStream(payload.size + HEADER_BYTES).use { buffer ->
        DataOutputStream(buffer).use { output ->
            output.writeInt(MAGIC); output.writeInt(1); output.writeInt(payload.size)
            output.write(sha(payload)); output.write(payload)
        }
        buffer.toByteArray()
    }

    private fun unpack(bytes: ByteArray): ByteArray = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        if (input.readInt() != MAGIC || input.readInt() != 1) throw IOException("Unknown metadata format")
        val size = input.readInt()
        if (size < 0 || size != bytes.size - HEADER_BYTES) throw IOException("Incomplete metadata")
        val expected = ByteArray(32).also(input::readFully)
        val payload = ByteArray(size).also(input::readFully)
        if (!MessageDigest.isEqual(expected, sha(payload))) throw IOException("Damaged metadata")
        payload
    }

    private fun sha(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value)
    private fun digest(value: String): String {
        val bytes = sha(value.toByteArray(Charsets.UTF_8))
        val digits = "0123456789abcdef"
        return CharArray(bytes.size * 2).also { chars -> bytes.forEachIndexed { index, byte ->
            val number = byte.toInt() and 255
            chars[index * 2] = digits[number ushr 4]; chars[index * 2 + 1] = digits[number and 15]
        } }.concatToString()
    }
    private companion object { const val MAGIC = 0x444C5343; const val HEADER_BYTES = 44 }
}
