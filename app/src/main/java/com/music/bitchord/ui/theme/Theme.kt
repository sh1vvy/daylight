package com.music.bitchord.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.ui.player.LocalLyricsFontFamily
import com.music.bitchord.data.settings.AppSettings

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

/** A calm jade seed for devices without wallpaper colors. Every elevation has a tonal role. */
internal val MaterialExpressiveLightColors = lightColorScheme(
    primary = Color(0xFF006A62), onPrimary = Color.White,
    primaryContainer = Color(0xFF9EF2E5), onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF49645F), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E0), onSecondaryContainer = Color(0xFF05201B),
    tertiary = Color(0xFF765A2F), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDEAC), onTertiaryContainer = Color(0xFF291800),
    background = Color(0xFFF4FBF7), onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF4FBF7), onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDAE5DF), onSurfaceVariant = Color(0xFF3F4945),
    outline = Color(0xFF6F7974), outlineVariant = Color(0xFFBEC9C3),
    surfaceTint = Color(0xFF006A62), inverseSurface = Color(0xFF2B322F),
    inverseOnSurface = Color(0xFFECF2EE), inversePrimary = Color(0xFF81D5C9),
    surfaceBright = Color(0xFFF4FBF7), surfaceDim = Color(0xFFD5DDD8),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFEFF5F1),
    surfaceContainer = Color(0xFFE9EFEB), surfaceContainerHigh = Color(0xFFE3EAE5),
    surfaceContainerHighest = Color(0xFFDEE4E0),
)

internal val MaterialExpressiveDarkColors = darkColorScheme(
    primary = Color(0xFF81D5C9), onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048), onPrimaryContainer = Color(0xFF9EF2E5),
    secondary = Color(0xFFB0CCC4), onSecondary = Color(0xFF1B3530),
    secondaryContainer = Color(0xFF324B47), onSecondaryContainer = Color(0xFFCCE8E0),
    tertiary = Color(0xFFE7C18C), onTertiary = Color(0xFF422C05),
    tertiaryContainer = Color(0xFF5C421B), onTertiaryContainer = Color(0xFFFFDEAC),
    background = Color(0xFF0F1512), onBackground = Color(0xFFDEE4E0),
    surface = Color(0xFF0F1512), onSurface = Color(0xFFDEE4E0),
    surfaceVariant = Color(0xFF3F4945), onSurfaceVariant = Color(0xFFBEC9C3),
    outline = Color(0xFF89938E), outlineVariant = Color(0xFF3F4945),
    surfaceTint = Color(0xFF81D5C9), inverseSurface = Color(0xFFDEE4E0),
    inverseOnSurface = Color(0xFF2B322F), inversePrimary = Color(0xFF006A62),
    surfaceBright = Color(0xFF353C38), surfaceDim = Color(0xFF0F1512),
    surfaceContainerLowest = Color(0xFF0A100D), surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E), surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303633),
)

private val MaterialExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private object ReducedMaterialMotion : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}

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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    displayLargeEmphasized = displayLargeEmphasized.copy(fontFamily = family),
    displayMediumEmphasized = displayMediumEmphasized.copy(fontFamily = family),
    displaySmallEmphasized = displaySmallEmphasized.copy(fontFamily = family),
    headlineLargeEmphasized = headlineLargeEmphasized.copy(fontFamily = family),
    headlineMediumEmphasized = headlineMediumEmphasized.copy(fontFamily = family),
    headlineSmallEmphasized = headlineSmallEmphasized.copy(fontFamily = family),
    titleLargeEmphasized = titleLargeEmphasized.copy(fontFamily = family),
    titleMediumEmphasized = titleMediumEmphasized.copy(fontFamily = family),
    titleSmallEmphasized = titleSmallEmphasized.copy(fontFamily = family),
    bodyLargeEmphasized = bodyLargeEmphasized.copy(fontFamily = family),
    bodyMediumEmphasized = bodyMediumEmphasized.copy(fontFamily = family),
    bodySmallEmphasized = bodySmallEmphasized.copy(fontFamily = family),
    labelLargeEmphasized = labelLargeEmphasized.copy(fontFamily = family),
    labelMediumEmphasized = labelMediumEmphasized.copy(fontFamily = family),
    labelSmallEmphasized = labelSmallEmphasized.copy(fontFamily = family),
)

// Keep Google's standard and emphasized Expressive scales, with Daylight's bundled Inter.
private val MaterialExpressiveTypography = Typography().withFamily(DaylightFont)

@Composable
private fun materialExpressiveColors(darkTheme: Boolean): ColorScheme {
    val context = LocalContext.current
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        darkTheme -> MaterialExpressiveDarkColors
        else -> MaterialExpressiveLightColors
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BitChordTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pinkCloud: Boolean = false,
    materialExpressive: Boolean = false,
    content: @Composable () -> Unit,
) {
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    CompositionLocalProvider(
        LocalLyricsFontFamily provides DaylightLyricsFont,
        LocalPinkCloud provides pinkCloud,
        LocalMaterialExpressive provides materialExpressive,
    ) {
        if (materialExpressive) {
            MaterialExpressiveTheme(
                colorScheme = materialExpressiveColors(darkTheme),
                typography = MaterialExpressiveTypography,
                shapes = MaterialExpressiveShapes,
                motionScheme = if (reduceAnimation) ReducedMaterialMotion else MotionScheme.expressive(),
                content = content,
            )
        } else {
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
