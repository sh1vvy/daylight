/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-08 through 2026-10-08.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.data.model

/** The same remote playlist may be raw, browsable, or a downloaded on-device page. */
fun playlistCreationOrderKey(id: String): String {
    val normalized = id.trim().removePrefix("VL")
    if (normalized.startsWith("local:playlist:")) {
        val downloadedId = normalized.removePrefix("local:playlist:")
        // Locally created/imported IDs use sp_local_ (including older timestamp
        // IDs). Only known YouTube playlist IDs are aliases of a remote card;
        // arbitrary local IDs and downloaded album IDs keep their identity.
        if (downloadedId.startsWith("PL") || downloadedId.startsWith("VLPL")) {
            return downloadedId.removePrefix("VL")
        }
    }
    return normalized
}
