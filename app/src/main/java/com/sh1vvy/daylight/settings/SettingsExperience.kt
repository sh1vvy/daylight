/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-13 through 2026-10-13.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.R

/** Lightweight Daylight settings shell. Provider models and preference storage stay in adapters. */
@Composable
fun SettingsExperience(
    selectedSection: SettingsSection?, onSection: (SettingsSection?) -> Unit,
    accountName: String?, accountPhoto: String?, signedIn: Boolean,
    onAccount: () -> Unit, onSignIn: () -> Unit, onJam: () -> Unit,
    selectedTheme: Int, onTheme: (Int) -> Unit, onWelcomePreview: () -> Unit,
    version: String, reduceMotion: Boolean, contentPadding: PaddingValues,
    controls: @Composable (SettingsSection, String, PaddingValues) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val openSection: (SettingsSection) -> Unit = {
        focusManager.clearFocus(); keyboard?.hide(); onSection(it)
    }
    AnimatedContent(targetState = selectedSection, label = "daylight-settings-page",
        transitionSpec = {
            val forward = targetState != null
            val duration = if (reduceMotion) 0 else 220
            (fadeIn(tween(duration)) + slideInHorizontally(tween(duration)) { if (forward) it / 14 else -it / 14 })
                .togetherWith(fadeOut(tween(if (reduceMotion) 0 else 120)) +
                    slideOutHorizontally(tween(duration)) { if (forward) -it / 18 else it / 18 })
                .using(null)
        }) { section ->
        if (section != null) {
            Column(Modifier.fillMaxSize().testTag("daylight-settings-section-${section.id}")) {
                Text(section.title(), style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = contentPadding.calculateTopPadding() + 8.dp, bottom = 16.dp)
                        .semantics { heading() })
                if (query.isNotBlank()) Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_filtered_by, query),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = { query = "" }) { Text(stringResource(R.string.settings_show_all)) }
                }
                Box(Modifier.weight(1f)) {
                    controls(section, query, PaddingValues(bottom = contentPadding.calculateBottomPadding()))
                }
            }
        } else {
            val categories = SettingsSection.entries.filter { it.matches(query, it.title(), it.summary()) }
            LazyColumn(Modifier.fillMaxSize().imePadding().testTag("daylight-settings-hub"),
                contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(key = "heading") {
                    Text(stringResource(R.string.settings), style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 2.dp).semantics { heading() })
                }
                item(key = "search") {
                    OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                        placeholder = { Text(stringResource(R.string.settings_search_hint)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.clear))
                        } },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent, focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = .5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .7f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .7f)),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("daylight-settings-search"))
                }
                if (query.isBlank()) {
                    item(key = "account") {
                        SettingsSurface(Modifier.padding(horizontal = 16.dp)) {
                            Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = if (signedIn) onAccount else onSignIn)
                                .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                                    if (accountPhoto != null) AsyncImage(accountPhoto, null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                    else Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                    Text(accountName?.takeIf(String::isNotBlank) ?: stringResource(R.string.welcome_guest),
                                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(stringResource(if (signedIn) R.string.account_integrations else R.string.welcome_youtube_login),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item(key = "themes") {
                        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val swatches = listOf(
                                listOf(Color(0xFF222023), Color(0xFF522A6F), Color(0xFFF29CB7)),
                                listOf(Color(0xFFF8F4ED), Color(0xFFD9F1F4), Color(0xFFE3FF00)),
                                listOf(Color(0xFFFF7BBF), Color(0xFFFFE9F2), Color(0xFFCEFF32)))
                            listOf(R.string.dark, R.string.light, R.string.pink_cloud).forEachIndexed { index, label ->
                                val selected = selectedTheme == index
                                Surface(onClick = { onTheme(index) }, shape = RoundedCornerShape(22.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .7f),
                                    border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = .65f)) else null,
                                    modifier = Modifier.weight(1f).testTag("daylight-settings-theme-$index")) {
                                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp))
                                            .background(Brush.linearGradient(swatches[index])), contentAlignment = Alignment.CenterEnd) {
                                            if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = if (index == 0) Color(0xFFFAEADD) else Color(0xFF322E39), modifier = Modifier.padding(end = 8.dp).size(20.dp))
                                        }
                                        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2, modifier = Modifier.padding(start = 2.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                items(categories, key = { it.id }, contentType = { "settings-category" }) { category ->
                    SettingsNavigationRow(category.title(), category.summary(), category.icon(), { openSection(category) },
                        Modifier.testTag("daylight-settings-category-${category.id}"))
                }
                if (categories.isEmpty()) item(key = "empty") {
                    Text(stringResource(R.string.settings_search_empty, query), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge)
                }
                if (query.isBlank()) {
                    item(key = "connections") {
                        SettingsNavigationRow(stringResource(R.string.listen_together), stringResource(R.string.listen_together_subtitle),
                            Icons.Rounded.Groups, onJam)
                    }
                    item(key = "accounts") {
                        SettingsNavigationRow(stringResource(R.string.account_integrations), null, Icons.Rounded.ManageAccounts, onAccount)
                    }
                    item(key = "welcome-preview") {
                        SettingsNavigationRow(stringResource(R.string.welcome_screen), null, Icons.Rounded.WavingHand, onWelcomePreview)
                    }
                    item(key = "version") {
                        Text(buildAnnotatedString {
                            append("Daylight $version\nGPLv3 · sh1vvy\n")
                            val links = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary))
                            withLink(LinkAnnotation.Url("https://github.com/sh1vvy/daylight", links)) { append("GitHub") }
                            append(" · ")
                            withLink(LinkAnnotation.Url("https://github.com/sh1vvy", links)) { append("Developer") }
                        }, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
        .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .72f)), content = content)
}

@Composable
private fun SettingsNavigationRow(title: String, summary: String?, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SettingsSurface(Modifier.padding(horizontal = 16.dp)) {
        Row(modifier.fillMaxWidth().heightIn(min = 76.dp).clickable(role = Role.Button, onClick = onClick).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun SettingsSection.title(): String = stringResource(when (this) {
    SettingsSection.APPEARANCE -> R.string.appearance
    SettingsSection.PLAYBACK -> R.string.playback
    SettingsSection.LYRICS -> R.string.settings_category_lyrics_accessibility
    SettingsSection.STORAGE -> R.string.settings_category_downloads_storage
    SettingsSection.PERFORMANCE -> R.string.performance
    SettingsSection.DATA -> R.string.your_data
})

@Composable
private fun SettingsSection.summary(): String = stringResource(when (this) {
    SettingsSection.APPEARANCE -> R.string.settings_category_appearance_subtitle
    SettingsSection.PLAYBACK -> R.string.settings_category_playback_subtitle
    SettingsSection.LYRICS -> R.string.settings_category_lyrics_subtitle
    SettingsSection.STORAGE -> R.string.settings_category_downloads_subtitle
    SettingsSection.PERFORMANCE -> R.string.settings_category_performance_subtitle
    SettingsSection.DATA -> R.string.settings_category_data_subtitle
})

private fun SettingsSection.icon(): ImageVector = when (this) {
    SettingsSection.APPEARANCE -> Icons.Rounded.Palette
    SettingsSection.PLAYBACK -> Icons.Rounded.Headphones
    SettingsSection.LYRICS -> Icons.Rounded.Lyrics
    SettingsSection.STORAGE -> Icons.Rounded.Download
    SettingsSection.PERFORMANCE -> Icons.Rounded.Speed
    SettingsSection.DATA -> Icons.Rounded.Backup
}
