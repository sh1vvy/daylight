package com.music.bitchord.data.remote

import com.music.bitchord.data.smb.SmbAuth
import com.music.bitchord.data.webdav.WebDavAuth
import java.net.URI
import java.security.MessageDigest

/** Credentials change what lives at a private server URL; they must also change its cache key. */
object ImageCacheKeys {
    private data class Scope(val identity: List<String>, val digest: String)
    @Volatile private var webDavScope: Scope? = null
    @Volatile private var smbScope: Scope? = null

    fun forUrl(url: String): String = forRequest(url, null)

    fun forRequest(url: String, authorization: String?): String {
        val scope = when {
            url.startsWith("smb://") -> {
                val identity = listOf(
                    SmbAuth.host, SmbAuth.port.toString(), SmbAuth.share,
                    SmbAuth.username, SmbAuth.password,
                )
                val cached = smbScope?.takeIf { it.identity == identity }
                    ?: Scope(identity, digest(identity)).also { smbScope = it }
                cached.digest
            }
            url.startsWith("http") -> {
                val host = runCatching { URI(url).host }.getOrNull()
                val header = authorization ?: host?.takeIf(WebDavAuth::shouldAuthorize)
                    ?.let { WebDavAuth.authHeader }
                if (header == null) return url
                val identity = listOf(host.orEmpty(), header)
                val cached = webDavScope?.takeIf { it.identity == identity }
                    ?: Scope(identity, digest(identity)).also { webDavScope = it }
                cached.digest
            }
            else -> return url
        }
        return "$url#daylight-private=$scope"
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
