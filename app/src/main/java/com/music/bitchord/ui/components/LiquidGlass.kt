/*
 * The glass rendering itself is Kyant0/backdrop (Apache-2.0), vendored at
 * [com.music.bitchord.ui.components.backdrop] — see that package for the
 * upstream attribution. This file is the integration glue, adapted from
 * EchoMusicApp/Echo-Music's GlassEffectConfig/Modifier.liquidGlass
 * (GPL-3.0), cut down from Echo's full per-component/vibrancy-slider config
 * to the single on/off switch BitChord exposes in Settings. It is used by the
 * floating nav bar and, while enabled, the app-wide floating top controls.
 */
package com.music.bitchord.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.music.bitchord.ui.theme.LocalPinkCloud
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.backdrop.Backdrop
import com.music.bitchord.ui.components.backdrop.backdrops.LayerBackdrop
import com.music.bitchord.ui.components.backdrop.drawBackdrop
import com.music.bitchord.ui.components.backdrop.effects.blur
import com.music.bitchord.ui.components.backdrop.effects.colorControls
import com.music.bitchord.ui.components.backdrop.effects.lens
import com.music.bitchord.ui.components.backdrop.highlight.Highlight
import com.music.bitchord.ui.components.backdrop.highlight.HighlightElement
import com.music.bitchord.ui.components.backdrop.highlight.HighlightStyle
import com.music.bitchord.ui.components.backdrop.internal.ShapeProvider
import com.music.bitchord.ui.components.backdrop.shadow.InnerShadow
import com.music.bitchord.ui.components.backdrop.shadow.Shadow

/** Whether the liquid glass nav bar is turned on — see [AppSettings.liquidGlass]. */
val LocalLiquidGlassEnabled = staticCompositionLocalOf { false }

/** The backdrop content (app UI) that a liquid glass surface samples from. */
val LocalAppBackdrop = staticCompositionLocalOf<Backdrop> { error("No AppBackdrop provided") }

/**
 * Where a [liquidGlass] surface composed under it also records the glass it
 * draws — the sampled, blurred and tinted backdrop, without its rim or shadow —
 * so a lens above it can refract that surface rather than the page behind it.
 * The tab bar's travelling selection pill is the one reader; see
 * [com.music.bitchord.ui.components.floatingtabbar.GlassSelectionPill].
 */
internal val LocalGlassExport = compositionLocalOf<LayerBackdrop?> { null }

/**
 * The backdrop blur pipeline requires [android.graphics.RenderEffect] on a
 * [android.graphics.RenderNode], available from Android 12 (API 31).
 */
fun isGlassSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.S

/** Clearer material with a curved rim, a quiet centre and directional lighting. */
private const val BLUR_RADIUS_DP = 7f
private const val LENS_HEIGHT_DP = 20f
private const val LENS_AMOUNT_DP = 28f
private const val DARK_SURFACE_OPACITY = 0.22f
private const val LIGHT_SURFACE_OPACITY = 0.18f
private val GlassRim = Highlight(
    width = 0.85.dp,
    blurRadius = 0.2.dp,
    style = HighlightStyle.Default(color = Color.White.copy(alpha = 0.72f), falloff = 1.6f),
)
private val GlassShadow = Shadow(
    radius = 18.dp,
    offset = DpOffset(0.dp, 6.dp),
    color = Color.Black.copy(alpha = 0.28f),
)
private val GlassInnerShadow = InnerShadow(
    radius = 3.dp,
    offset = DpOffset(0.dp, 1.dp),
    color = Color.Black.copy(alpha = 0.12f),
)

private fun glassSheen(light: Boolean) = Brush.linearGradient(
    0f to Color.White.copy(alpha = if (light) 0.38f else 0.16f),
    0.34f to Color.White.copy(alpha = 0.025f),
    0.7f to Color.Transparent,
    1f to Color.White.copy(alpha = if (light) 0.12f else 0.06f),
    start = Offset.Zero,
    end = Offset.Infinite,
)

/**
 * Resolution fraction the glass surface records and processes its backdrop at.
 *
 * Half resolution keeps the curved rim detailed without processing the entire
 * screen at full resolution. Only the captured background is scaled; the
 * highlights, glyphs and labels remain at the device's native resolution.
 */
private const val GLASS_RESOLUTION_SCALE = 0.5f

/**
 * The hairline along a bar's edge, and what stands in for the glass rim
 * wherever the glass itself is not drawn.
 *
 * A surface filled with the theme's own `surface` colour has no edge of its own
 * against a dark page — it is the same near-black the page is. The glass gets
 * its edge from [Highlight], and this is that edge for everything that does not.
 */
internal val GLASS_EDGE_WIDTH = 0.5.dp
internal val GLASS_EDGE_COLOR = Color.White.copy(alpha = 0.10f)

/**
 * Icon and label colour for content sitting on a glass surface.
 *
 * Glass shows whatever is behind it rather than the theme's surface colour, so
 * the usual onSurface greys have nothing dependable to sit against. Pure black
 * or white off the theme's luminance is the only tint that holds against
 * arbitrary artwork, and it is what Echo's own glass nav bar uses.
 */
@Composable
fun glassContentColor(): Color =
    if (LocalPinkCloud.current) MaterialTheme.colorScheme.onSurface
    else if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color.Black else Color.White

/**
 * A translucent illuminated selection above the navigation bar's glass.
 */
@Composable
fun glassIndicatorColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color.White.copy(alpha = 0.55f)
    else Color.White.copy(alpha = 0.14f)

/**
 * A lightweight visual match for liquid glass over a stable background.
 *
 * This keeps the same translucent tint, directional highlight and hairline as
 * [liquidGlass], but intentionally performs no backdrop capture, blur, lens
 * refraction or shadow rendering. When Liquid Glass is disabled or unsupported,
 * [fallbackColor] preserves the control's existing filled appearance.
 */
@Composable
fun Modifier.lightweightLiquidGlass(
    shape: CornerBasedShape,
    fallbackColor: Color,
): Modifier {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val useGlass = LocalLiquidGlassEnabled.current && isGlassSupported() && !reduceDynamicBlur
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val surfaceOpacity = if (light) LIGHT_SURFACE_OPACITY else DARK_SURFACE_OPACITY
    val glassTint = if (LocalPinkCloud.current) MaterialTheme.colorScheme.surface
        else if (light) Color(0xFFFAFAFA) else Color(0xFF121212)
    val shapeProvider = ShapeProvider { shape }

    return clip(shape)
        .background(
            color = if (useGlass) glassTint.copy(alpha = surfaceOpacity) else fallbackColor,
            shape = shape,
        )
        .then(
            if (useGlass) {
                HighlightElement(
                    shapeProvider = shapeProvider,
                    highlight = { GlassRim },
                )
                    .drawWithCache {
                        val sheen = glassSheen(light)
                        onDrawWithContent { drawRect(sheen); drawContent() }
                    }
            } else {
                Modifier
            },
        )
        .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
}

/**
 * Renders this composable as a liquid glass surface sampling [LocalAppBackdrop]:
 * vibrancy, blur and lens refraction, then a theme-adaptive surface tint (light
 * glass on light theme, dark on dark). Returns the receiver unchanged on devices
 * without RenderEffect support — callers should still gate on [isGlassSupported]
 * to fall back to the regular Haze treatment there.
 *
 * Under "reduce dynamic blur" the surface is filled solid instead, which is what
 * that setting promises everywhere else in the app. It is checked here rather
 * than at each call site so there is one answer to it: [MainActivity] also stops
 * recording the backdrop layer when it is on, and a surface that still tried to
 * sample would be sampling a layer nothing is drawing into.
 *
 * [shape] is restricted to [CornerBasedShape] because the backdrop's lens effect
 * throws for any other shape type.
 */
@Composable
fun Modifier.liquidGlass(shape: CornerBasedShape): Modifier {
    if (!isGlassSupported()) return this
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    if (reduceDynamicBlur) {
        return background(MaterialTheme.colorScheme.surface, shape)
            .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
    }
    val backdrop = LocalAppBackdrop.current
    val exportedBackdrop = LocalGlassExport.current
    val density = LocalDensity.current
    val blurPx = with(density) { BLUR_RADIUS_DP.dp.toPx() } * GLASS_RESOLUTION_SCALE
    val lensHeightPx = with(density) { LENS_HEIGHT_DP.dp.toPx() } * GLASS_RESOLUTION_SCALE
    val lensAmountPx = with(density) { LENS_AMOUNT_DP.dp.toPx() } * GLASS_RESOLUTION_SCALE
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val sheen = remember(light) { glassSheen(light) }
    val surfaceTintColor = if (LocalPinkCloud.current) MaterialTheme.colorScheme.surface
        else if (light) Color(0xFFFAFAFA) else Color(0xFF121212)

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(saturation = 1.3f)
            blur(blurPx)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(
                    refractionHeight = lensHeightPx.coerceAtMost(size.minDimension * 0.38f),
                    refractionAmount = lensAmountPx.coerceAtMost(size.minDimension * 0.55f),
                    depthEffect = true,
                    chromaticAberration = true,
                    chromaticAberrationStrength = 0.18f,
                )
            }
        },
        highlight = { GlassRim },
        shadow = { GlassShadow },
        innerShadow = { GlassInnerShadow },
        onDrawSurface = {
            drawRect(color = surfaceTintColor.copy(alpha = if (light) LIGHT_SURFACE_OPACITY else DARK_SURFACE_OPACITY))
            drawRect(brush = sheen)
        },
        exportedBackdrop = exportedBackdrop,
        backdropScale = GLASS_RESOLUTION_SCALE,
    )
}
