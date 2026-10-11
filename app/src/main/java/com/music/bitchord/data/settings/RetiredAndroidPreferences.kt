/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-08 through 2026-10-08.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.data.settings

import android.content.SharedPreferences

/** Run on launch and backup restore so retired integrations and lyric overrides cannot return. */
internal fun removeRetiredAndroidPreferences(prefs: SharedPreferences) {
    val retiredKeys = listOf(
        "listenbrainz_enabled",
        "listenbrainz_token",
        "listenbrainz_primary_artist_only",
        "spotify_canvas_auto_hide",
        "prioritize_spotify_canvas",
        "translation_language",
    ).filter(prefs::contains)
    if (retiredKeys.isEmpty()) return
    val editor = prefs.edit()
    retiredKeys.forEach(editor::remove)
    editor.apply()
}
