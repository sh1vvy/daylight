package com.music.bitchord.ui.player

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily

/** The platform supplies Daylight's bundled lyric face; other hosts keep a system fallback. */
val LocalLyricsFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.SansSerif }
