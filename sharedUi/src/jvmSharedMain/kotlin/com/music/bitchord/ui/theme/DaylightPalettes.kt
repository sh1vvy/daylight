/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Daylight's supplied palettes, mapped to readable Material roles rather than raw swatches. */
val DaylightDarkColors = darkColorScheme(
    primary = Color(0xFFFFB0CC), onPrimary = Color(0xFF422137),
    primaryContainer = Color(0xFF583047), onPrimaryContainer = Color(0xFFFFDFEB),
    secondary = Color(0xFFDDAAFF), onSecondary = Color(0xFF352044),
    secondaryContainer = Color(0xFF49265F), onSecondaryContainer = Color(0xFFF0D9FF),
    tertiary = Color(0xFFFBCF4E), onTertiary = Color(0xFF382D08),
    tertiaryContainer = Color(0xFF514321), onTertiaryContainer = Color(0xFFFFE6A0),
    background = Color(0xFF222023), onBackground = Color(0xFFFAEADD),
    surface = Color(0xFF302833), onSurface = Color(0xFFFAEADD),
    surfaceVariant = Color(0xFF47364D), onSurfaceVariant = Color(0xFFE4CFDF),
    surfaceTint = Color(0xFFF29CB7),
    inverseSurface = Color(0xFFFAEADD), inverseOnSurface = Color(0xFF302833),
    inversePrimary = Color(0xFF92446B),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFA58C9F), outlineVariant = Color(0xFF5B445C), scrim = Color(0xFF18121C),
    surfaceBright = Color(0xFF55435A), surfaceDim = Color(0xFF222023),
    surfaceContainerLowest = Color(0xFF211B25), surfaceContainerLow = Color(0xFF2B2430),
    surfaceContainer = Color(0xFF352B3C), surfaceContainerHigh = Color(0xFF413449),
    surfaceContainerHighest = Color(0xFF4D3D55),
)

val DaylightLightColors = lightColorScheme(
    primary = Color(0xFF086BA5), onPrimary = Color.White,
    primaryContainer = Color(0xFFD4F0FF), onPrimaryContainer = Color(0xFF083645),
    secondary = Color(0xFF466578), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDFEDF0), onSecondaryContainer = Color(0xFF183D4E),
    tertiary = Color(0xFF4C6200), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE3FF00), onTertiaryContainer = Color(0xFF253300),
    background = Color(0xFFF8F4ED), onBackground = Color(0xFF083645),
    surface = Color(0xFFFBFAF5), onSurface = Color(0xFF083645),
    surfaceVariant = Color(0xFFE1EBEA), onSurfaceVariant = Color(0xFF405D69),
    surfaceTint = Color(0xFF057BC1),
    inverseSurface = Color(0xFF163B4C), inverseOnSurface = Color(0xFFF8F4ED),
    inversePrimary = Color(0xFF4DD8FF),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF6D8388), outlineVariant = Color(0xFFC6D9DC), scrim = Color(0xFF083645),
    surfaceBright = Color(0xFFFBFAF5), surfaceDim = Color(0xFFDAE0DB),
    surfaceContainerLowest = Color(0xFFFFFDF7), surfaceContainerLow = Color(0xFFF4EDE4),
    surfaceContainer = Color(0xFFEFF1E8), surfaceContainerHigh = Color(0xFFE6ECE6),
    surfaceContainerHighest = Color(0xFFDDE9E5),
)

val PinkCloudColors = lightColorScheme(
    primary = Color(0xFF9B1755), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFCDE3), onPrimaryContainer = Color(0xFF5A153A),
    secondary = Color(0xFF84516A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBEC), onSecondaryContainer = Color(0xFF4E233B),
    tertiary = Color(0xFF526500), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCEFF32), onTertiaryContainer = Color(0xFF293500),
    background = Color(0xFFFFF0F7), onBackground = Color(0xFF421A32),
    surface = Color(0xFFFFF7FB), onSurface = Color(0xFF421A32),
    surfaceVariant = Color(0xFFF5D7E7), onSurfaceVariant = Color(0xFF6E3D59),
    surfaceTint = Color(0xFFEC2B7A),
    inverseSurface = Color(0xFF50253E), inverseOnSurface = Color(0xFFFFF0F7),
    inversePrimary = Color(0xFFFF7BBF),
    error = Color(0xFFB32643), onError = Color.White,
    errorContainer = Color(0xFFFFDAE0), onErrorContainer = Color(0xFF68001E),
    outline = Color(0xFF9A7188), outlineVariant = Color(0xFFE7B5CF), scrim = Color(0xFF321326),
    surfaceBright = Color(0xFFFFF7FB), surfaceDim = Color(0xFFECD0DE),
    surfaceContainerLowest = Color(0xFFFFFCFE), surfaceContainerLow = Color(0xFFFFEBF5),
    surfaceContainer = Color(0xFFFFE1F0), surfaceContainerHigh = Color(0xFFFCD5E9),
    surfaceContainerHighest = Color(0xFFF5C9E1),
)

fun daylightColorScheme(dark: Boolean, pinkCloud: Boolean = false): ColorScheme = when {
    pinkCloud -> PinkCloudColors
    dark -> DaylightDarkColors
    else -> DaylightLightColors
}
