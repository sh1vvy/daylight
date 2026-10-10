package com.music.bitchord.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.settings.artworkFraction
import kotlinx.coroutines.flow.collectLatest

/** Motion artwork gives up its layer only for the cover handoff, not the whole audio mix. */
@Composable
internal fun rememberAutomixArtworkHandoff(artwork: Any?): State<Boolean> {
    val blend = PlayerSettings.smartMixBlend.collectAsStateWithLifecycle()
    val reduced by PlayerSettings.reduceAnimation.collectAsStateWithLifecycle()
    return remember(artwork, reduced) {
        val currentArtwork = (artwork as? String)?.artworkAt(ART_PX) ?: artwork
        derivedStateOf {
        if (reduced) false else blend.value?.let { mix ->
            val from = mix.outgoingArtwork?.artworkAt(ART_PX)
            val to = mix.incomingArtwork?.artworkAt(ART_PX)
            val fraction = mix.artworkFraction()
            from != null && to != null && from != to && (currentArtwork == from || currentArtwork == to) && fraction > 0f && fraction < 1f
        } == true
    } }
}

/** Two retained cover layers; progress is read in drawing, never in the player's composition. */
@Composable
internal fun AutomixArtwork(
    model: ImageRequest,
    onState: (AsyncImagePainter.State) -> Unit,
    modifier: Modifier = Modifier,
    drawBase: () -> Boolean = { true },
) {
    val blend = PlayerSettings.smartMixBlend.collectAsStateWithLifecycle()
    val reduced by PlayerSettings.reduceAnimation.collectAsStateWithLifecycle()
    val currentModel by rememberUpdatedState(model.data)
    // Only a change of covers mounts painters. The engine's progress ticks cause layer redraws.
    val covers by remember(reduced) { derivedStateOf {
        if (reduced) null else blend.value?.let { mix ->
            val from = mix.outgoingArtwork?.artworkAt(ART_PX)
            val to = mix.incomingArtwork?.artworkAt(ART_PX)
            if (from != null && to != null && from != to && (currentModel == from || currentModel == to)) from to to else null
        }
    } }
    Box(modifier.testTag("automix-cover")) {
        AsyncImage(model, null, contentScale = ContentScale.Crop, onState = onState,
            modifier = Modifier.fillMaxSize().drawWithContent { if (drawBase()) drawContent() })
        covers?.let { (from, to) ->
            val outgoing = rememberPlayerArtwork(from)
            val incoming = rememberPlayerArtwork(to)
            val fraction = remember(from, to) { Animatable(blend.value?.artworkFraction() ?: 0f) }
            LaunchedEffect(from, to) {
                snapshotFlow { blend.value?.artworkFraction() ?: 1f }.collectLatest {
                    // Bridge engine ticks on the frame clock without inventing mix timing.
                    fraction.animateTo(it, tween(60, easing = LinearEasing))
                }
            }
            // Keep the outgoing image opaque beneath the dissolve: two inverse alpha layers
            // would briefly expose a dark background. Failed/missing art never fades to blank.
            AsyncImage(outgoing.request, null, contentScale = ContentScale.Crop, onState = outgoing::onState,
                modifier = Modifier.fillMaxSize().drawWithContent { if (outgoing.loaded) drawContent() })
            AsyncImage(incoming.request, null, contentScale = ContentScale.Crop, onState = incoming::onState,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    alpha = if (outgoing.loaded && incoming.loaded) fraction.value else 0f
                })
        }
    }
}
