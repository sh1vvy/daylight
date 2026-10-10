package com.music.bitchord.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Upgrade
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import com.music.bitchord.R
import com.music.bitchord.data.AppUpdateChecker
import com.music.bitchord.data.AppUpdateChecker.DownloadState
import com.music.bitchord.data.settings.AppSettings
import kotlin.math.roundToInt

/** Theme-colored update notice; download/install behavior remains with the updater. */
@Composable
fun UpdateAvailableDialog(
    version: String,
    notes: String?,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenReleasePage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Observe progress here so every download chunk cannot recompose the
    // activity/navigation tree behind the notice.
    val state by AppUpdateChecker.download.collectAsStateWithLifecycle()
    UpdateAvailableDialogContent(version, notes, state, onDismiss, onDownload,
        onCancelDownload, onInstall, onOpenReleasePage, modifier)
}

@Composable
internal fun UpdateAvailableDialogContent(
    version: String,
    notes: String?,
    downloadState: DownloadState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenReleasePage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val entrance = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) entrance.snapTo(1f)
        else entrance.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
    }
    val title = stringResource(R.string.software_update)
    Box(
        modifier.fillMaxSize()
            .graphicsLayer { alpha = entrance.value }
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .graphicsLayer {
                        val scale = 0.96f + 0.04f * entrance.value
                        scaleX = scale
                        scaleY = scale
                    }
                    .semantics { paneTitle = title }
                    .testTag("update-notice")
                    // The card consumes taps; only its controls or scrim dismiss it.
                    .clickable(indication = null,
                        interactionSource = remember { MutableInteractionSource() }, onClick = {}),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 10.dp,
            ) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(52.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(18.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(R.drawable.ic_logo), null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(32.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(title, style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(when (downloadState) {
                                is DownloadState.Downloading -> R.string.update_downloading_body
                                is DownloadState.Ready -> R.string.update_ready_body
                                is DownloadState.Failed -> R.string.update_failed_body
                                else -> R.string.update_available_body
                            }, version),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    when (downloadState) {
                        is DownloadState.Downloading -> {
                            val fraction = downloadState.fraction.coerceIn(0f, 1f)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (fraction > 0f) {
                                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                                    Text((fraction * 100).roundToInt().toString() + "%",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.End))
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                        is DownloadState.Failed -> Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(downloadState.message, Modifier.fillMaxWidth().padding(14.dp),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        else -> Unit
                    }
                    if (!notes.isNullOrBlank()) UpdateReleaseNotes(notes)
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        when (downloadState) {
                            is DownloadState.Downloading -> {
                                TextButton(onClick = onCancelDownload, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                            is DownloadState.Ready -> {
                                UpdatePrimaryAction(stringResource(R.string.install_now), true, onInstall)
                                UpdateSecondaryAction(stringResource(R.string.later), onDismiss)
                            }
                            is DownloadState.Failed -> {
                                UpdatePrimaryAction(stringResource(R.string.try_again), false, onDownload)
                                UpdateSecondaryAction(stringResource(R.string.open_releases_page), onOpenReleasePage)
                            }
                            else -> {
                                UpdatePrimaryAction(stringResource(R.string.download_now), false, onDownload)
                                UpdateSecondaryAction(stringResource(R.string.remind_me_later), onDismiss)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Stable notes are skipped when only download progress changes. */
@Composable
private fun UpdateReleaseNotes(notes: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.whats_new),
            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(18.dp)) {
            Box(Modifier.fillMaxWidth().heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState()).padding(16.dp)) {
                RichText(style = RichTextStyle.Default) { Markdown(notes) }
            }
        }
    }
}

@Composable
private fun UpdatePrimaryAction(label: String, install: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(18.dp)) {
        Icon(if (install) Icons.Rounded.Upgrade else Icons.Rounded.Download, null, Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun UpdateSecondaryAction(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
