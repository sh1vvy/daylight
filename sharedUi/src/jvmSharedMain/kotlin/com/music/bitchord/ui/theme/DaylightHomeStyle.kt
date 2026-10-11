/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-08 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/** Home and its floating navigation fade share the app's complete selected palette. */
fun daylightHomeBackground(themeBackground: Color, pinkCloud: Boolean = false): Color = themeBackground

internal fun daylightHomeColorScheme(base: ColorScheme, pinkCloud: Boolean = false): ColorScheme = base
