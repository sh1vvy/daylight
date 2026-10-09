package com.music.bitchord.playback

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Lossless fallback files must also stay separate when YouTube selects a different itag. */
internal object YouTubeRenditionKey {
    fun forStream(videoId: String, url: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return videoId
        val itag = parsed.queryParameter("itag")?.takeIf { it.all(Char::isDigit) && it.isNotEmpty() }
            ?: return videoId
        val length = parsed.queryParameter("clen")?.takeIf { it.all(Char::isDigit) && it.isNotEmpty() }.orEmpty()
        val modified = parsed.queryParameter("lmt")?.takeIf { it.all(Char::isDigit) && it.isNotEmpty() }.orEmpty()
        return "$videoId#youtube-$itag-$length-$modified"
    }
}
