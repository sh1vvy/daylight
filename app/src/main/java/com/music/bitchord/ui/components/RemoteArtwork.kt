/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-10 through 2026-10-10.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.ui.components

import androidx.compose.runtime.Composable
import com.music.bitchord.data.model.Song

/** Streaming and device tracks carry their own artwork URI. */
@Composable
fun rememberRemoteArtworkUrl(song: Song?): String? = song?.thumbnailUrl
