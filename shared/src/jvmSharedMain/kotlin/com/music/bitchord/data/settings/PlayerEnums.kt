/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-10 through 2026-10-10.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.data.settings

/** Bitrate preference for normal streaming; verified lossless uses its own quality tiers. */
enum class AudioQuality(
    val maxKbps: Int,
    val label: String,
    val detail: String,
    val hourly: String,
) {
    LOW(64, "Low", "~64 kbps · smallest download", "29 MB/hr"),
    MEDIUM(Int.MAX_VALUE, "Medium", "Best available · ~171 kbps Opus", "77 MB/hr"),
    HIGH(Int.MAX_VALUE, "High", "Best available audio", "144 MB/hr"),
    LOSSLESS(Int.MAX_VALUE, "Lossless", "Lossless audio where available", "300+ MB/hr"),
    ;
}

/** The surface that was last open inside the expanded player. */
enum class LastPlayerScreen {
    MAIN,
    LYRICS,
    QUEUE,
}
