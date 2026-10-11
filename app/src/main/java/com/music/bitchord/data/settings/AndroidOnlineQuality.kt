/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-08 through 2026-10-08.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

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
