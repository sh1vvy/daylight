package com.music.bitchord.data.listentogether

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

data class ParsedJamInvite(
    val code: String,
    val serverUrl: String? = null,
)

/** Relays a Daylight web or scheme invite from [com.music.bitchord.MainActivity] to Compose. */
object JamInviteLink {

    const val ORIGIN = "https://jam.sh1vvy.com"

    private const val EXTRA_CONSUMED = "daylight.jamInviteConsumed"
    private const val CUSTOM_SCHEME = "daylight"
    private const val CUSTOM_HOST = "party"

    private val _pending = MutableStateFlow<ParsedJamInvite?>(null)
    val pending: StateFlow<ParsedJamInvite?> = _pending.asStateFlow()

    /** Reads a web invite from a cold launch or a new intent on the existing task. */
    fun consume(intent: Intent?): Boolean {
        if (
            intent == null ||
            intent.action != Intent.ACTION_VIEW ||
            intent.getBooleanExtra(EXTRA_CONSUMED, false)
        ) return false

        val invite = parseInvite(intent.dataString) ?: return false
        intent.putExtra(EXTRA_CONSUMED, true)
        _pending.value = invite
        return true
    }

    fun handled() {
        _pending.value = null
    }

    /** Returns the normalized party code only for the public invite URL shape. */
    fun parse(value: String?): String? = parseInvite(value)?.code

    /**
     * Parses an incoming invite:
     * 1. daylight://party/<CODE>?server=<SERVER>
     * 2. https://jam.sh1vvy.com/invite/<CODE>
     */
    fun parseInvite(value: String?): ParsedJamInvite? {
        val uri = runCatching { URI(value ?: return null) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val query = uri.rawQuery
        val server = extractQueryParam(query, "server")?.let { sanitizeServerUrl(it) }

        // Web invites belong to this server. Query parameters cannot redirect
        // a verified Daylight link to a different deployment.
        if (scheme == "https" && host == "jam.sh1vvy.com" && uri.port == -1 && uri.userInfo == null) {
            val path = uri.path.orEmpty()
            if (!path.startsWith("/invite/")) return null
            val code = cleanCode(path.removePrefix("/invite/")) ?: return null
            return ParsedJamInvite(code = code, serverUrl = ORIGIN)
        }

        // 1. Custom scheme: daylight://party/<CODE> or daylight://party?code=<CODE>
        if (scheme == CUSTOM_SCHEME && host == CUSTOM_HOST) {
            val pathPart = uri.path.orEmpty().removePrefix("/").takeIf { it.isNotBlank() }
            val candidate = pathPart ?: extractQueryParam(query, "code") ?: return null
            val code = cleanCode(candidate) ?: return null
            return ParsedJamInvite(code = code, serverUrl = server)
        }

        return null
    }

    fun url(code: String, customServer: String? = null): String {
        val base = customServer?.trim()?.trimEnd('/')
        return "${base.takeUnless { it.isNullOrBlank() } ?: ORIGIN}/invite/${code.uppercase()}"
    }

    fun schemeUrl(code: String, customServer: String? = null): String {
        val normalizedCode = code.uppercase()
        val base = customServer?.trim()?.trimEnd('/')
        return if (!base.isNullOrBlank()) {
            val encoded = runCatching { URLEncoder.encode(base, "UTF-8") }.getOrDefault(base)
            "daylight://party/$normalizedCode?server=$encoded"
        } else {
            "daylight://party/$normalizedCode"
        }
    }

    private fun cleanCode(raw: String): String? {
        if (!raw.all { it.isLetterOrDigit() }) return null
        val cleaned = raw.uppercase()
        return if (cleaned.length == ListenTogether.CODE_LENGTH) cleaned else null
    }

    private fun extractQueryParam(query: String?, paramName: String): String? {
        if (query.isNullOrBlank()) return null
        return query.split('&').asSequence()
            .map { it.split('=', limit = 2) }
            .firstOrNull { it.isNotEmpty() && it[0].equals(paramName, ignoreCase = true) }
            ?.getOrNull(1)
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
    }

    private fun sanitizeServerUrl(raw: String?): String? {
        val trimmed = raw?.trim()?.trimEnd('/') ?: return null
        if (trimmed.isBlank()) return null
        val withScheme = if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed
        } else {
            "https://$trimmed"
        }
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        if (uri.host.isNullOrBlank()) return null
        return withScheme
    }

}
