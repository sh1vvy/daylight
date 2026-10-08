package com.music.bitchord.data.settings

/**
 * The quality choices available for Android's YouTube Music catalogue.
 *
 * Legacy Medium and Lossless both resolve to the best YouTube rendition, so
 * they share High's visible selection. This does not change the stored
 * preference, the native format's ceiling, or local-file decoding.
 */
internal object AndroidOnlineQuality {
    val streamingOptions = listOf(AudioQuality.HIGH, AudioQuality.LOW)
    val downloadOptions = listOf(DownloadQuality.HIGH, DownloadQuality.STANDARD)

    fun streamingSelection(quality: AudioQuality): AudioQuality =
        if (quality == AudioQuality.LOW) AudioQuality.LOW else AudioQuality.HIGH

    fun downloadSelection(quality: DownloadQuality): DownloadQuality =
        if (quality == DownloadQuality.STANDARD) DownloadQuality.STANDARD else DownloadQuality.HIGH
}
