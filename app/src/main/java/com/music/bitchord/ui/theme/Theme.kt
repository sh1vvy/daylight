package com.music.bitchord.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.ui.player.LocalLyricsFontFamily

// Daylight’s warm sunrise accent; the name is retained for shared callers.
val AccentRed = Color(0xFFB94F10)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB86C),
    onPrimary = Color(0xFF3D2100),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF0D0D0F),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFF2C2C2E),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF9C440E),
    onPrimary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color(0xFFF7F7F9),
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF6E6E73),
    outline = Color(0xFFE5E5EA),
)

/** Soft blush surfaces with deep rose ink, including dialogs and elevated controls. */
internal val PinkCloudColors = lightColorScheme(
    primary = Color(0xFFA23765),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD7E6),
    onPrimaryContainer = Color(0xFF561C36),
    inversePrimary = Color(0xFFFFAFCE),
    secondary = Color(0xFF805265),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDAE8),
    onSecondaryContainer = Color(0xFF482638),
    tertiary = Color(0xFF765681),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF5DCFF),
    onTertiaryContainer = Color(0xFF3D2448),
    background = Color(0xFFFFF2F6),
    onBackground = Color(0xFF39212D),
    surface = Color(0xFFFFFAFC),
    onSurface = Color(0xFF39212D),
    surfaceVariant = Color(0xFFF7DCE6),
    onSurfaceVariant = Color(0xFF704755),
    surfaceTint = Color(0xFFA23765),
    inverseSurface = Color(0xFF49303D),
    inverseOnSurface = Color(0xFFFFECF3),
    error = Color(0xFFB32643),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAE0),
    onErrorContainer = Color(0xFF68001E),
    outline = Color(0xFF9F7687),
    outlineVariant = Color(0xFFE2BCCA),
    scrim = Color(0xFF2F1824),
    surfaceBright = Color(0xFFFFFAFC),
    surfaceDim = Color(0xFFEBD0DA),
    surfaceContainer = Color(0xFFFFE7F0),
    surfaceContainerHigh = Color(0xFFFBDDE8),
    surfaceContainerHighest = Color(0xFFF4D3E0),
    surfaceContainerLow = Color(0xFFFFEFF5),
    surfaceContainerLowest = Color(0xFFFFFCFD),
)

/** Inter 4.1, with its text-size spacing for controls and longer passages. */
val DaylightFont = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
)

/** Inter Display 4.1, bundled under the SIL Open Font License in docs/licenses. */
val DaylightLyricsFont = FontFamily(
    Font(R.font.inter_display_regular, FontWeight.Normal),
    Font(R.font.inter_display_semibold, FontWeight.SemiBold),
    Font(R.font.inter_display_bold, FontWeight.Bold),
)

// Clear headings, lighter controls, and open line spacing at reading sizes.
private val DaylightTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 42.sp, letterSpacing = (-0.45).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.4).sp),
    displaySmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.25).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = (-0.35).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp, letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = (-0.15).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = (-0.15).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.1.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.1.sp),
).withFamily(DaylightFont).let {
    it.copy(
        displayLarge = it.displayLarge.copy(fontFamily = DaylightLyricsFont),
        displayMedium = it.displayMedium.copy(fontFamily = DaylightLyricsFont),
        displaySmall = it.displaySmall.copy(fontFamily = DaylightLyricsFont),
        headlineLarge = it.headlineLarge.copy(fontFamily = DaylightLyricsFont),
        headlineMedium = it.headlineMedium.copy(fontFamily = DaylightLyricsFont),
        headlineSmall = it.headlineSmall.copy(fontFamily = DaylightLyricsFont),
    )
}

/** Applies [family] to every style in the scale, including dialogs and captions. */
private fun Typography.withFamily(family: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

@Composable
fun BitChordTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pinkCloud: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalLyricsFontFamily provides DaylightLyricsFont,
        LocalPinkCloud provides pinkCloud,
    ) {
        MaterialTheme(
            colorScheme = when {
                pinkCloud -> PinkCloudColors
                darkTheme -> DarkColors
                else -> LightColors
            },
            typography = DaylightTypography,
            content = content,
        )
    }
}

/**
 * Draws the status and navigation bar glyphs dark or light.
 *
 * `enableEdgeToEdge()` decides this from the *system* dark-mode setting, which
 * is the wrong input the moment the in-app theme disagrees with it: Light theme
 * on a phone in dark mode left white icons on a white bar, invisible. The bars
 * have to follow the content the app is actually painting. The player supplies
 * its own stable light-icon value and paints contrast behind it; every other
 * surface follows the theme. Hence a parameter rather than reading it here.
 */
@Composable
fun SystemBarIcons(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = findWindow(view) ?: return
    SideEffect {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = dark
            isAppearanceLightNavigationBars = dark
        }
    }
}
