/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.ui.feedback

import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.delay

/** A non-interactive window keeps feedback above player sheets without taking focus or taps. */
@Composable
fun AppFeedbackHost() {
    val owner = LocalLifecycleOwner.current
    val token = remember { Any() }
    DisposableEffect(owner, token) {
        fun update() = AppFeedback.setForeground(token, owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        val observer = LifecycleEventObserver { _, _ -> update() }
        owner.lifecycle.addObserver(observer)
        update()
        onDispose { owner.lifecycle.removeObserver(observer); AppFeedback.setForeground(token, false) }
    }
    val message by AppFeedback.messages.collectAsStateWithLifecycle()
    val reduced by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val accessibility = LocalAccessibilityManager.current
    message?.let { result -> key(result.id) {
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(result.id) {
            visible = true
            val timeout = accessibility?.calculateRecommendedTimeoutMillis(if (result.long) 5_000L else 3_000L,
                containsIcons = true, containsText = true, containsControls = false) ?: if (result.long) 5_000L else 3_000L
            delay(timeout)
            visible = false
            if (!reduced) delay(180)
            AppFeedback.dismiss(result.id)
        }
        Dialog(onDismissRequest = {}, properties = DialogProperties(
            dismissOnBackPress = false, dismissOnClickOutside = false,
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
        )) {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            SideEffect {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            }
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.TopCenter) {
                AnimatedVisibility(visible, enter = if (reduced) EnterTransition.None else
                    fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.96f) + slideInVertically(tween(200)) { -it / 4 },
                    exit = if (reduced) ExitTransition.None else fadeOut(tween(160)) +
                        scaleOut(tween(160), targetScale = 0.96f) + slideOutVertically(tween(160)) { -it / 4 }) {
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 10.dp,
                        modifier = Modifier.widthIn(max = 420.dp).testTag("app-feedback")
                            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                        Row(Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text(result.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    } }
}
