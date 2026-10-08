package com.music.bitchord.ui.graphics

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext

/** A bounded cache that shares a pending decode/analysis instead of doing it per surface. */
class CoalescingLruCache<V : Any>(private val maxEntries: Int) {
    init { require(maxEntries >= 0) }

    private val lock = Any()
    private val values = LinkedHashMap<String, V>(0, 0.75f, true)
    private class Pending<V>(val task: Deferred<V?>, var readers: Int = 0)
    private val pending = HashMap<String, Pending<V>>()

    operator fun get(key: String): V? = synchronized(lock) { values[key] }

    fun remove(key: String) { synchronized(lock) { values.remove(key) } }

    /** Nulls and failures are not retained, so a transient image failure can be retried. */
    suspend fun getOrLoad(key: String, load: suspend () -> V?): V? {
        val context = currentCoroutineContext().minusKey(Job)
        val result = synchronized(lock) {
            values[key]?.let { return it }
            val result = pending[key] ?: Pending(
                CoroutineScope(context + SupervisorJob()).async(start = CoroutineStart.LAZY) {
                    val value = load()
                    val producer = currentCoroutineContext()[Job]
                    synchronized(lock) {
                        // A cancelled, non-cooperative loader can finish after
                        // its replacement. It must not overwrite that result.
                        if (pending[key]?.task === producer && value != null && maxEntries > 0) {
                            values[key] = value
                            while (values.size > maxEntries) values.remove(values.keys.first())
                        }
                    }
                    value
                },
            ).also { created ->
                pending[key] = created
                created.task.invokeOnCompletion { synchronized(lock) { pending.remove(key, created) } }
            }
            result.readers++
            result
        }
        try {
            // The first surface closing must not cancel another one's decode.
            result.task.start()
            return result.task.await()
        } finally {
            synchronized(lock) {
                result.readers--
                if (result.readers == 0 && !result.task.isCompleted) {
                    pending.remove(key, result)
                    result.task.cancel()
                }
            }
        }
    }
}

/** Hosts may partition private remote artwork without exposing credentials to shared UI. */
object ArtworkCacheIdentity {
    @Volatile private var resolver: (String) -> String = { it }

    fun install(resolver: (String) -> String) { this.resolver = resolver }

    fun key(url: String): String = resolver(url)
}
