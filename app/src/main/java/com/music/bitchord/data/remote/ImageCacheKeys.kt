package com.music.bitchord.data.remote

import java.security.MessageDigest

/** Credentials change what lives at a private server URL; they must also change its cache key. */
object ImageCacheKeys {
    fun forUrl(url: String): String = url

    fun forRequest(url: String, authorization: String?): String {
        if (authorization == null || !url.startsWith("http")) return url
        return "$url#daylight-private=${digest(listOf(url, authorization))}"
    }

    /** Keep the existing 20% heap allowance, with a ceiling on large-heap phones. */
    fun memoryBudget(maxHeapBytes: Long): Long =
        (maxHeapBytes / 5).coerceIn(1L, 64L * 1024 * 1024)

    private fun digest(parts: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        parts.forEach {
            val bytes = it.toByteArray(Charsets.UTF_8)
            digest.update("${bytes.size}:".toByteArray(Charsets.UTF_8))
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
