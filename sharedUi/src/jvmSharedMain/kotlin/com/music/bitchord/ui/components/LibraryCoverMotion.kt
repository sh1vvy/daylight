@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.music.bitchord.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** One tapped occurrence and one navigation visit; repeated covers never share an identity. */
data class LibraryCoverSelection(
    val browseId: String,
    val cardKey: String,
    val sourceRoute: String,
    val detailRoute: String? = null,
    val cornerRadiusDp: Float = 12f,
    val cornerPercent: Float = 8f,
)

/** Stable before the tap; a destination visit must not replace the source's identity. */
private data class CoverKey(val route: String, val browseId: String, val cardKey: String)

class LibraryCoverMotion(
    val sharedScope: SharedTransitionScope,
    val visibilityScope: AnimatedVisibilityScope,
    val selection: LibraryCoverSelection?,
    val source: Boolean,
    val route: String,
    val opening: Boolean,
    val onSelectCard: ((String, String, Float) -> Unit)? = null,
) {
    val isTransitioning: Boolean get() = sharedScope.isTransitionActive ||
        visibilityScope.transition.currentState != EnterExitState.Visible ||
        visibilityScope.transition.targetState != EnterExitState.Visible
}

val LocalLibraryCoverMotion = compositionLocalOf<LibraryCoverMotion?> { null }

/** Header effects and foreground travel above the shared cover instead of popping in afterwards. */
@Composable
fun Modifier.libraryCoverForeground(zIndex: Float): Modifier {
    val motion = LocalLibraryCoverMotion.current ?: return this
    if (motion.source || motion.selection == null || !motion.opening) return this
    val alpha = motion.visibilityScope.transition.animateFloat(
        transitionSpec = { if (motion.opening) tween(300, delayMillis = 100) else tween(250) },
        label = "cover-foreground-alpha",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    return with(motion.sharedScope) {
        this@libraryCoverForeground.renderInSharedTransitionScopeOverlay(
            zIndexInOverlay = zIndex,
            renderInOverlay = { motion.isTransitioning },
        ).graphicsLayer { this.alpha = if (motion.isTransitioning) alpha.value else 1f }
    }
}

const val LIBRARY_OPEN_DURATION_MS = 480
const val LIBRARY_CLOSE_DURATION_MS = 420
val LibraryOpenEasing = CubicBezierEasing(0.32f, 0f, 0.18f, 1f)
val LibraryCloseEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private class CoverSourceSnapshot<T>(var value: T)

/** Retained by navigation so the return target survives disposal of its source page. */
class LibraryCoverSnapshots {
    private val values = mutableMapOf<Pair<String, String>, Any>()
    private val sourceSizes = mutableMapOf<Pair<String, String>, Float>()

    fun recordSourceSize(route: String, cardKey: String, shortEdgeDp: Float) {
        if (shortEdgeDp > 0f) sourceSizes[route to cardKey] = shortEdgeDp
    }

    fun removeSourceSize(route: String, cardKey: String) { sourceSizes.remove(route to cardKey) }

    fun cornerPercent(selection: LibraryCoverSelection): Float =
        sourceSizes[selection.sourceRoute to selection.cardKey]?.let {
            (selection.cornerRadiusDp / it * 100f).coerceIn(0f, 50f)
        } ?: selection.cornerPercent

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> retain(route: String, name: String, value: T, frozen: Boolean): T {
        val key = route to name
        if (!frozen || key !in values) values[key] = value
        return values.getValue(key) as T
    }
}

val LocalLibraryCoverSnapshots = compositionLocalOf<LibraryCoverSnapshots?> { null }

/** Late results wait until the cover has finished opening or returning to its card. */
@Composable
fun <T : Any> rememberCoverSourceValue(name: String, value: T): T {
    val motion = LocalLibraryCoverMotion.current
    val snapshot = remember { CoverSourceSnapshot(value) }
    val frozen = motion?.source == true && motion.isTransitioning
    LocalLibraryCoverSnapshots.current?.let { registry ->
        if (motion?.source == true) return registry.retain(motion.route, name, value, frozen)
    }
    if (!frozen) snapshot.value = value
    return snapshot.value
}

/** Visible covers register their bounds before navigation; only the matched pair animates. */
@Composable
fun Modifier.libraryCoverMotion(browseId: String?, cardKey: String? = null, cornerRadiusDp: Float = 12f): Modifier {
    val motion = LocalLibraryCoverMotion.current ?: return this
    browseId ?: return this
    val key = if (motion.source) {
        cardKey ?: return this
        CoverKey(motion.route, browseId, cardKey)
    } else {
        val selection = motion.selection ?: return this
        if (browseId != selection.browseId || cardKey != null) return this
        CoverKey(selection.sourceRoute, browseId, selection.cardKey)
    }
    val cardRadius = (if (motion.source) cornerRadiusDp else motion.selection?.cornerRadiusDp ?: cornerRadiusDp).dp
    val selected = !motion.source || motion.selection?.let { it.browseId == browseId && it.cardKey == cardKey } == true
    val percent = motion.selection?.cornerPercent ?: 8f
    val corner = remember(key) { Animatable(if (selected && !motion.opening) 0f else percent) }
    LaunchedEffect(selected, motion.opening, percent) {
        if (selected) {
            if (motion.opening) {
                corner.snapTo(percent)
                corner.animateTo(0f, tween(LIBRARY_OPEN_DURATION_MS, easing = LibraryOpenEasing))
            } else corner.animateTo(percent, tween(LIBRARY_CLOSE_DURATION_MS, easing = LibraryCloseEasing))
        } else corner.snapTo(percent)
    }
    val currentPercent = corner.value
    // Match the source's radius, then continuously soften all four corners
    // against the moving image's own bounds instead of a separate overlay mask.
    val shape: Shape = if (selected && motion.isTransitioning) object : Shape {
        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
            val radius = size.minDimension * currentPercent / 100f
            val curve = CornerRadius(radius, radius)
            return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, curve, curve, curve, curve))
        }
    } else RoundedCornerShape(if (motion.source) cardRadius else 0.dp)
    val density = LocalDensity.current
    val snapshots = LocalLibraryCoverSnapshots.current
    DisposableEffect(snapshots, key) {
        onDispose { if (motion.source && cardKey != null) snapshots?.removeSourceSize(motion.route, cardKey) }
    }
    val measured = if (motion.source && cardKey != null) this.onSizeChanged {
        snapshots?.recordSourceSize(motion.route, cardKey, minOf(it.width, it.height) / density.density)
    } else this
    // Corner updates must not replace the bounds spec every frame and restart its movement.
    val bounds = remember(motion.opening) {
        BoundsTransform { _, _ ->
            if (motion.opening) tween(LIBRARY_OPEN_DURATION_MS, easing = LibraryOpenEasing)
            else tween(LIBRARY_CLOSE_DURATION_MS, easing = LibraryCloseEasing)
        }
    }
    return with(motion.sharedScope) {
        measured.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = motion.visibilityScope,
            boundsTransform = bounds,
        ).clip(shape)
    }
}
