package com.music.bitchord.ui.components

import android.provider.Settings as SystemSettings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.auth.GoogleAccountSession
import com.music.bitchord.auth.YouTubeProfile
import com.music.bitchord.data.settings.AppSettings

/** Keep this composed while hidden so a dismiss can finish its transition. */
@Composable
fun AccountProfileSelector(
    visible: Boolean,
    accounts: List<GoogleAccountSession>,
    activeAccountId: String?,
    activeProfileId: String?,
    onSelect: (GoogleAccountSession, YouTubeProfile) -> Unit,
    onAddAccount: () -> Unit,
    onManageAccounts: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val systemAnimationsOff = remember(context) {
        runCatching {
            SystemSettings.Global.getFloat(context.contentResolver, SystemSettings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    val reduceMotion = reduceAnimation || systemAnimationsOff
    val duration = if (reduceMotion) 100 else 180
    BackHandler(enabled = visible, onBack = onDismiss)

    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(tween(duration)),
        exit = fadeOut(tween(if (reduceMotion) 90 else 140)),
    ) {
        Box(Modifier.fillMaxSize()) {
            // A light dimmer and an opaque themed card avoid the blue acrylic
            // cast, while leaving the underlying page recognizable.
            Box(
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = .30f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { if (visible) onDismiss() },
                    ),
            )
            BoxWithConstraints(
                Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                val cardMotion = if (reduceMotion) Modifier else Modifier.animateEnterExit(
                    enter = slideInVertically(tween(duration, easing = FastOutSlowInEasing)) { -it / 24 } +
                        scaleIn(tween(duration, easing = FastOutSlowInEasing), initialScale = .97f,
                            transformOrigin = TransformOrigin(.9f, 0f)),
                    exit = slideOutVertically(tween(140, easing = FastOutSlowInEasing)) { -it / 32 } +
                        scaleOut(tween(140, easing = FastOutSlowInEasing), targetScale = .98f,
                            transformOrigin = TransformOrigin(.9f, 0f)),
                )
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 0.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .widthIn(max = 460.dp)
                        .fillMaxWidth()
                        .heightIn(max = maxHeight)
                        .then(cardMotion)
                        // Empty space on the card is not an outside tap.
                        .pointerInput(Unit) { detectTapGestures(onTap = {}) },
                ) {
                    LazyColumn(contentPadding = PaddingValues(8.dp)) {
                        item(key = "header") {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.switch_account),
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(enabled = visible, onClick = onDismiss) {
                                    Icon(Icons.Rounded.Close, stringResource(R.string.close),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        accounts.forEach { account ->
                            item(key = "account:${account.accountId}") {
                                Text(
                                    account.email.ifBlank { account.name.ifBlank { stringResource(R.string.accounts) } },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 6.dp),
                                )
                            }
                            items(account.profiles.size, key = { "profile:${account.accountId}:${account.profiles[it].profileId}" }) { index ->
                                val profile = account.profiles[index]
                                ProfileRow(
                                    profile = profile,
                                    selected = account.accountId == activeAccountId && profile.profileId == activeProfileId,
                                    enabled = visible,
                                    onClick = { onSelect(account, profile); onDismiss() },
                                )
                            }
                        }
                        item(key = "actions") {
                            Spacer(Modifier.height(10.dp))
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f))
                                    .padding(vertical = 4.dp),
                            ) {
                                SelectorAction(Icons.Rounded.Add, stringResource(R.string.add_account), visible) {
                                    onDismiss(); onAddAccount()
                                }
                                SelectorAction(Icons.Rounded.ManageAccounts, stringResource(R.string.manage_accounts), visible) {
                                    onDismiss(); onManageAccounts()
                                }
                                SelectorAction(Icons.Rounded.Settings, stringResource(R.string.settings), visible) {
                                    onDismiss(); onOpenSettings()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(profile: YouTubeProfile, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val description = stringResource(if (selected) R.string.selected_account else R.string.switch_account)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .10f) else Color.Transparent)
            .heightIn(min = 64.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (profile.avatar != null) {
            AsyncImage(profile.avatar, null, Modifier.size(40.dp).clip(CircleShape))
        } else {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Person, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(profile.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                profile.handle.ifBlank { stringResource(if (profile.isBrandAccount) R.string.brand_account else R.string.personal) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Check, stringResource(R.string.selected_account),
                Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SelectorAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
