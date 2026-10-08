package com.music.bitchord.auth

import java.net.URI
import java.util.Locale

private val LOGIN_HOST_SUFFIXES = listOf(
    "spotify.com", "scdn.co", "google.com", "gstatic.com", "facebook.com", "apple.com",
)

private val WEBVIEW_VERSION_TOKEN = Regex("""Version/\d+(\.\d+)*\s*""")

/** Keep the device's current mobile Chrome viewport rather than pretending to be Windows. */
internal fun spotifyLoginUserAgent(platformUserAgent: String): String = platformUserAgent
    .replace("; wv", "")
    .replace(WEBVIEW_VERSION_TOKEN, "")
    .trim()

internal fun isSpotifyLoginDestination(url: String?): Boolean =
    matchesHttpsHost(url, LOGIN_HOST_SUFFIXES)

internal fun isSpotifySessionDestination(url: String?): Boolean =
    matchesHttpsHost(url, listOf("spotify.com"))

private fun matchesHttpsHost(url: String?, suffixes: List<String>): Boolean {
    val uri = url?.let { runCatching { URI(it) }.getOrNull() } ?: return false
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.rawUserInfo != null) return false
    val host = uri.host?.lowercase(Locale.ROOT) ?: return false
    return suffixes.any { host == it || host.endsWith(".$it") }
}

/** Read only Spotify's exact session cookie, preserving '=' inside its value. */
internal fun spotifySessionCookie(rawCookies: String?): String? = rawCookies
    ?.split(';')
    ?.firstNotNullOfOrNull { cookie ->
        if (cookie.substringBefore('=').trim() != "sp_dc" || '=' !in cookie) return@firstNotNullOfOrNull null
        cookie.substringAfter('=').trim().takeIf(String::isNotEmpty)
    }
