package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.music.bitchord.data.model.artworkAt

/** Separate window consumes Back and gestures without touching the queue or playback. */
@Composable
fun ArtworkViewer(url: String, title: String, onDismiss: () -> Unit) {
    var zoom by remember(url) { mutableFloatStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    var bounds by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val context = LocalContext.current
    val model = remember(url, context) { ImageRequest.Builder(context).data(url.artworkAt(1600)).size(1600).build() }
    val transform = rememberTransformableState { scale, pan, _ ->
        zoom = (zoom * scale).coerceIn(1f, 3f)
        val x = bounds.width * (zoom - 1f) / 2f
        val y = bounds.height * (zoom - 1f) / 2f
        offset = if (zoom == 1f) Offset.Zero else Offset((offset.x + pan.x).coerceIn(-x, x), (offset.y + pan.y).coerceIn(-y, y))
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(model = model, contentDescription = "$title artwork", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().onSizeChanged { bounds = it }.transformable(transform)
                    .pointerInput(url) { detectTapGestures(onDoubleTap = { zoom = if (zoom > 1f) 1f else 2f; offset = Offset.Zero }) }
                    .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = offset.x; translationY = offset.y })
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)) {
                Icon(Icons.Rounded.Close, "Close artwork", tint = Color.White)
            }
        }
    }
}
