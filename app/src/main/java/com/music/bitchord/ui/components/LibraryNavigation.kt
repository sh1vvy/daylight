@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.music.bitchord.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin

/** Keeps Home/Library covers and their destination in one overlay, below the app bars. */
@Composable
fun LibraryNavigation(
    targetState: String,
    libraryRoutes: Set<String>,
    selection: LibraryCoverSelection?,
    reduceMotion: Boolean,
    snapshotScope: String? = null,
    onSelectCard: (sourceRoute: String, browseId: String, cardKey: String, cornerRadiusDp: Float) -> Unit,
    transitionSpec: AnimatedContentTransitionScope<String>.() -> ContentTransform,
    modifier: Modifier = Modifier,
    label: String,
    content: @Composable AnimatedContentScope.(String) -> Unit,
) {
    val selectCard = rememberUpdatedState(onSelectCard)
    val libraryStateHolder = rememberSaveableStateHolder()
    // Account/profile changes drop retained feed data immediately.
    val coverSnapshots = remember(snapshotScope) { LibraryCoverSnapshots() }
    val resolvedSelection = remember(selection, coverSnapshots) {
        selection?.copy(cornerPercent = coverSnapshots.cornerPercent(selection))
    }
    SharedTransitionLayout {
        val sharedScope = this
        AnimatedContent(
            targetState = targetState,
            transitionSpec = {
                val opening = selection != null && initialState == selection.sourceRoute && targetState == selection.detailRoute
                val closing = selection != null && targetState == selection.sourceRoute && initialState == selection.detailRoute
                if (reduceMotion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else if (opening) {
                    // The cover supplies the movement; scaling the whole destination
                    // also transforms its shared bounds and makes the first frame jump.
                    (fadeIn(tween(300, delayMillis = 100, easing = LibraryOpenEasing))
                        togetherWith fadeOut(tween(260, delayMillis = 80))).using(null)
                } else if (closing) {
                    (fadeIn(tween(1)) togetherWith
                        (fadeOut(tween(250)) + scaleOut(tween(LIBRARY_CLOSE_DURATION_MS, easing = LibraryCloseEasing),
                            targetScale = 0.97f, transformOrigin = TransformOrigin(0.5f, 0.15f)))).using(null)
                } else transitionSpec()
            },
            modifier = modifier,
            label = label,
        ) { route ->
            val source = route in libraryRoutes
            val destination = route == selection?.detailRoute
            val visibilityScope = this
            val opening = targetState == selection?.detailRoute
            val motion = remember(sharedScope, visibilityScope, route, source, destination, selection, reduceMotion, opening) {
                if (!reduceMotion && (source || destination)) {
                    LibraryCoverMotion(
                        sharedScope = sharedScope,
                        visibilityScope = visibilityScope,
                        selection = resolvedSelection?.takeIf { destination || route == it.sourceRoute },
                        source = source,
                        route = route,
                        opening = opening,
                        onSelectCard = if (source) {
                            { browseId, cardKey, radius -> selectCard.value(route, browseId, cardKey, radius) }
                        } else null,
                    )
                } else null
            }
            if (source) {
                libraryStateHolder.SaveableStateProvider(route) {
                    CompositionLocalProvider(LocalLibraryCoverMotion provides motion,
                        LocalLibraryCoverSnapshots provides coverSnapshots) { content(route) }
                }
            } else {
                CompositionLocalProvider(LocalLibraryCoverMotion provides motion) { content(route) }
            }
        }
    }
}
