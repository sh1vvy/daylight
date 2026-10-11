/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-10 through 2026-10-10.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.data.settings

/** Retired settings cannot be revived by an older backup. */
internal object RetiredSettings {
    fun isRetired(key: String): Boolean =
        key == "legacy_mesh_gradient" || key.startsWith("webdav_") || key.startsWith("smb_")
}
