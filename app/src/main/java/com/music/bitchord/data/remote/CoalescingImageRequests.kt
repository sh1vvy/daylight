/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-08 through 2026-10-10.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.data.remote

import coil3.intercept.Interceptor
import coil3.request.ImageResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Different bitmap sizes still share one encoded HTTP image. Let the first
 * request fill Coil's disk entry before another size checks that entry.
 */
class CoalescingImageRequests : Interceptor {
    private val gate = KeyedImageRequestGate()

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val url = request.data as? String ?: return chain.proceed()
        if (!url.startsWith("http") || !request.diskCachePolicy.readEnabled ||
            !request.diskCachePolicy.writeEnabled) return chain.proceed()
        return gate.withKey(request.diskCacheKey ?: url) { chain.proceed() }
    }
}

/** Pending keys disappear after their last reader, so browsing cannot grow this map. */
internal class KeyedImageRequestGate {
    private class Entry(val mutex: Mutex = Mutex(), var readers: Int = 0)
    private val lock = Any()
    private val entries = HashMap<String, Entry>()

    suspend fun <T> withKey(key: String, block: suspend () -> T): T {
        val entry = synchronized(lock) {
            entries.getOrPut(key) { Entry() }.also { it.readers++ }
        }
        try {
            return entry.mutex.withLock { block() }
        } finally {
            synchronized(lock) {
                entry.readers--
                if (entry.readers == 0) entries.remove(key, entry)
            }
        }
    }
}
