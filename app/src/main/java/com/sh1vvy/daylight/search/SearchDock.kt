/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-12 through 2026-10-12.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.search

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.editableText
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.*
import com.music.bitchord.ui.theme.daylightFloatingColors
import kotlin.math.roundToInt

/** One bottom lane: normal transport/navigation folds into a keyboard-following editor. */
@Composable
fun SearchDock(active: Boolean, query: String, placeholder: String, searchLabel: String, closeLabel: String,
    sources: List<String>, selectedSource: Int, onSource: (Int) -> Unit,
    clearLabel: String, focusRequested: Boolean, onFocusHandled: () -> Unit,
    onQuery: (String) -> Unit, onSubmit: () -> Unit, onClose: () -> Unit,
    reduceMotion: Boolean, modifier: Modifier = Modifier, normal: @Composable () -> Unit) {
    val view = LocalView.current
    val activity = remember(view) {
        var context = view.context
        while (context is android.content.ContextWrapper && context !is android.app.Activity) context = context.baseContext
        context as? android.app.Activity
    }
    DisposableEffect(active, activity) {
        val window = activity?.window
        val previous = window?.attributes?.softInputMode
        if (active && previous != null) window.setSoftInputMode(
            (previous and android.view.WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose { if (active && previous != null) window?.setSoftInputMode(previous) }
    }
    var retainSurfaceOwner by remember { mutableStateOf(active) }
    val progress = animateFloatAsState(if (active) 1f else 0f,
        if (reduceMotion) snap() else spring(.92f, 340f), label = "search-dock",
        finishedListener = { if (it == 0f) retainSurfaceOwner = false })
    val morph = remember { SearchDockSurfaceMorph() }
    val ownsSurfaces = active || retainSurfaceOwner
    SideEffect { if (active) retainSurfaceOwner = true; morph.suppressOriginalSurfaces = ownsSurfaces }
    val density = LocalDensity.current
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val ime = WindowInsets.ime
    val navigation = WindowInsets.navigationBars
    val chromeInsets = floatingBarInsets
    val layoutDirection = LocalLayoutDirection.current
    val tonal = daylightFloatingColors(MaterialTheme.colorScheme)
    val reduceBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val glass = LocalLiquidGlassEnabled.current && isGlassSupported()
    LaunchedEffect(active, focusRequested) {
        if (active && focusRequested) {
            withFrameNanos { }
            focus.requestFocus(); keyboard?.show(); onFocusHandled()
        }
    }
    val submit: () -> Unit = { onSubmit(); focusManager.clearFocus(); keyboard?.hide() }
    Layout(modifier = modifier.fillMaxWidth().testTag("daylight-search-dock"), content = {
        CompositionLocalProvider(LocalSearchDockSurfaceMorph provides morph) {
            Box(morph.normalContainer().then(if (active) Modifier.clearAndSetSemantics { } else Modifier)) { normal() }
        }
        Row(Modifier.clip(CircleShape).then(if (active) Modifier else Modifier.clearAndSetSemantics { })
            .padding(4.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                sources.forEachIndexed { index, label ->
                    Box(Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                        .background(if (selectedSource == index) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .8f) else androidx.compose.ui.graphics.Color.Transparent)
                        .selectable(selected = selectedSource == index, enabled = active, role = Role.Tab, onClick = { onSource(index) })
                        .testTag("daylight-search-source-$index"), contentAlignment = Alignment.Center) {
                        Text(label, color = if (selectedSource == index) MaterialTheme.colorScheme.onSecondaryContainer else tonal.secondaryContent,
                            style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        Row(Modifier.clip(CircleShape).then(if (active) Modifier else Modifier.clearAndSetSemantics { })
            .padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = submit, enabled = query.isNotBlank(), modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Search, searchLabel, tint = tonal.content, modifier = Modifier.size(22.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = tonal.secondaryContent, style = MaterialTheme.typography.bodyLarge)
                    BasicTextField(value = query, onValueChange = onQuery, singleLine = true, enabled = active,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = tonal.content),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                            .semantics { editableText = AnnotatedString(query) }.testTag("daylight-search-editor"))
                }
                if (query.isNotEmpty()) IconButton(onClick = { onQuery(""); focus.requestFocus(); keyboard?.show() }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Close, clearLabel, tint = tonal.secondaryContent, modifier = Modifier.size(18.dp))
                } else Spacer(Modifier.width(12.dp))
            }
            IconButton(onClick = { focusManager.clearFocus(); keyboard?.hide(); onClose() }, enabled = active,
                modifier = (if (active) Modifier else Modifier.clearAndSetSemantics { }).testTag("daylight-search-close")) {
                Icon(Icons.Rounded.Close, closeLabel, tint = tonal.content, modifier = Modifier.size(24.dp))
            }
        // The shared surfaces exist only during editing/folding; normal navigation
        // keeps its original effects without three dormant glass layers.
        if (ownsSurfaces) repeat(3) {
            val material = if (glass) Modifier.liquidGlass(CircleShape) else Modifier.clip(CircleShape)
                .background(if (reduceBlur) tonal.surface.copy(alpha = 1f) else tonal.surface)
            Box(Modifier.clearAndSetSemantics {}.then(material))
        }
    }) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0)
        val nav = measurables[0].measure(loose)
        val editorHeight = (128.dp.toPx() + chromeInsets.getBottom(density)).roundToInt()
        val t = progress.value.coerceIn(0f, 1f)
        val keyboardHeight = (ime.getBottom(density) - navigation.getBottom(density)).coerceAtLeast(0)
        val bodyHeight = (nav.height + (editorHeight - nav.height) * t).roundToInt()
        val height = (bodyHeight + keyboardHeight * t).roundToInt().coerceAtMost(constraints.maxHeight)
        val left = 16.dp.toPx() + chromeInsets.getLeft(density, layoutDirection)
        val right = constraints.maxWidth - 16.dp.toPx() - chromeInsets.getRight(density, layoutDirection)
        val fieldTop = 68.dp.toPx()
        val width = constraints.maxWidth.toFloat()
        val targets = listOf(
            Rect(left, 4.dp.toPx(), right, 60.dp.toPx()),
            Rect(left, fieldTop, right - 56.dp.toPx(), fieldTop + 56.dp.toPx()),
            Rect(right - 48.dp.toPx(), fieldTop + 4.dp.toPx(), right, fieldTop + 52.dp.toPx()),
        )
        val roles = listOf(SearchDockSurface.PLAYER, SearchDockSurface.NAVIGATION, SearchDockSurface.SEARCH)
        val rects = targets.mapIndexed { index, target ->
            val origin = morph.origin(roles[index], width, nav.height.toFloat())
                ?: if (index == 0) Rect(target.left, nav.height.toFloat() - editorHeight + target.center.y,
                    target.right, nav.height.toFloat() - editorHeight + target.center.y)
                else target.translate(Offset(0f, nav.height.toFloat() - editorHeight))
            val from = origin.translate(Offset(0f, bodyHeight - nav.height.toFloat()))
            val to = target.translate(Offset(0f, bodyHeight - editorHeight.toFloat()))
            androidx.compose.ui.geometry.lerp(from, to, t)
        }
        fun size(rect: Rect) = Constraints.fixed(rect.width.roundToInt().coerceAtLeast(0), rect.height.roundToInt().coerceAtLeast(0))
        // Foregrounds follow the same rectangles as their surfaces, including a
        // collapsed mini-player. Labels never arrive ahead of the moving pill.
        val foregrounds = rects.mapIndexed { index, rect -> measurables[index + 1].measure(size(rect)) }
        val surfaces = if (ownsSurfaces) rects.mapIndexed { index, rect -> measurables[index + 4].measure(size(rect)) } else emptyList()
        layout(constraints.maxWidth, height) {
            surfaces.forEachIndexed { index, surface ->
                val rect = rects[index]
                surface.place(rect.left.roundToInt(), rect.top.roundToInt())
            }
            if (t < 1f) nav.placeWithLayer(0, height - nav.height) {
                alpha = (1f - t / .6f).coerceIn(0f, 1f)
                translationY = (bodyHeight - height).toFloat()
            }
            if (t > 0f) foregrounds.forEachIndexed { index, foreground ->
                val rect = rects[index]
                foreground.placeWithLayer(rect.left.roundToInt(), rect.top.roundToInt()) {
                    alpha = ((t - .2f) / .6f).coerceIn(0f, 1f)
                }
            }
        }
    }
}
