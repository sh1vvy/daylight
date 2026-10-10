package com.music.bitchord.ui.components

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/** One motion rhythm for tabs and pushed pages; cover transitions retain their own path. */
internal val AppMotionEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

internal fun AnimatedContentTransitionScope<String>.appPageTransition(): ContentTransform {
    val fromTab = initialState.removePrefix("tab:").toIntOrNull()
    val toTab = targetState.removePrefix("tab:").toIntOrNull()
    if (fromTab != null && toTab != null) {
        val direction = if (toTab > fromTab) 1 else -1
        // Translation keeps both transparent pages fully drawn over the shared
        // background, avoiding the dim middle frame of a two-sided crossfade.
        return (slideInHorizontally(tween(240, easing = AppMotionEasing)) { it * direction }
            togetherWith slideOutHorizontally(tween(240, easing = AppMotionEasing)) { -it * direction })
            .using(null)
    }
    val direction = if (pageDepth(targetState) >= pageDepth(initialState)) 1 else -1
    return ((slideInHorizontally(tween(260, easing = AppMotionEasing)) { it / 8 * direction } +
        fadeIn(tween(200, delayMillis = 40))) togetherWith
        (slideOutHorizontally(tween(200, easing = AppMotionEasing)) { -it / 16 * direction } +
            fadeOut(tween(140)))).using(null)
}

private fun pageDepth(route: String): Int = when {
    route.startsWith("tab:") -> 0
    route in setOf("account_scrobbling", "listen_together", "equalizer", "spotify", "discord") -> 2
    else -> 1
}
