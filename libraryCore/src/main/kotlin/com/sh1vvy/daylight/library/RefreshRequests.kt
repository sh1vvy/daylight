/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.library

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/** A shared refresh belongs to the repository, not the lifetime of one waiting screen. */
class RefreshRequests<K, T>(private val scope: CoroutineScope) {
    private val lock = Any()
    private val active = mutableMapOf<K, Deferred<T>>()

    suspend fun run(key: K, fetch: suspend () -> T): T {
        val request = synchronized(lock) {
            active[key]?.takeIf { it.isActive || !it.isCompleted } ?: scope.async(start = CoroutineStart.LAZY) { fetch() }.also { created ->
                active[key] = created
                created.invokeOnCompletion { synchronized(lock) { if (active[key] === created) active.remove(key) } }
            }
        }
        request.start()
        return request.await()
    }

    /** Switching identity or changing library membership makes older provider work obsolete. */
    fun cancelAll() {
        val requests = synchronized(lock) { active.values.toList().also { active.clear() } }
        requests.forEach { it.cancel() }
    }
}
