package com.music.bitchord.data.lyrics

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asContextElement
import okhttp3.Call
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Carries HTTP failures through providers that deliberately return nullable lyrics. */
internal class LyricsRequestHealth(private val job: Job? = null) {
    private val answered = AtomicBoolean(false)
    private val failed = AtomicBoolean(false)
    private val calls = ConcurrentHashMap.newKeySet<Call>()
    @OptIn(InternalCoroutinesApi::class)
    private val cancellation = job?.invokeOnCompletion(onCancelling = true, invokeImmediately = true) {
        if (job.isCancelled) calls.forEach(Call::cancel)
    }
    val context = current.asContextElement(this)
    val definitiveMissing get() = answered.get() && !failed.get()
    val hasFailure get() = failed.get()

    fun response(code: Int) {
        if (code in 200..299 || code == 404 || code == 204) answered.set(true) else failed.set(true)
    }
    fun failure() { failed.set(true) }
    fun merge(other: LyricsRequestHealth) {
        if (other.answered.get()) answered.set(true)
        if (other.failed.get()) failed.set(true)
    }
    fun register(call: Call) {
        calls.add(call)
        if (job?.isCancelled == true) call.cancel()
    }
    fun unregister(call: Call) { calls.remove(call) }
    fun close() { cancellation?.dispose() }

    companion object {
        val current = ThreadLocal<LyricsRequestHealth?>()
    }
}

/** Shares cancellation and error classification without changing each provider's parser. */
internal fun Call.lyricsBody(): String? {
    val health = LyricsRequestHealth.current.get()
    health?.register(this)
    return try {
        execute().use { response ->
            health?.response(response.code)
            if (response.isSuccessful) response.body?.string().also {
                if (it == null && response.code != 204) health?.failure()
                if (it != null) classifyLyricsBody(it, health)
            } else null
        }
    } catch (_: Exception) {
        health?.failure()
        null
    } finally {
        health?.unregister(this)
    }
}

/** Provider error envelopes and malformed JSON are failures even when served with HTTP 200. */
private fun classifyLyricsBody(body: String, health: LyricsRequestHealth?) {
    if (health == null || !body.trimStart().startsWith("{")) return
    val root = try { lyricsJson.parseToJsonElement(body) } catch (_: Exception) {
        health.failure()
        return
    }
    val obj = root as? JsonObject ?: return
    if (obj.isEmpty()) health.failure()
    if (obj["isError"]?.toString() == "true" || obj["ok"]?.toString() == "false" ||
        obj["success"]?.toString() == "false" || obj["status"]?.toString() == "\"error\"" ||
        obj["error"]?.let { it !is JsonNull && it.toString() !in setOf("false", "\"\"") } == true
    ) health.failure()
}

internal inline fun <T> lyricsParse(block: () -> T): T? = try { block() } catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    LyricsRequestHealth.current.get()?.failure()
    null
}
