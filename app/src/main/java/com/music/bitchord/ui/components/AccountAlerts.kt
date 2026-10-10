package com.music.bitchord.ui.components

import com.music.bitchord.R

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.launch
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

@Composable
fun LastfmLoginAlert(
    hazeState: HazeState,
    configured: Boolean,
    awaitingApproval: Boolean,
    error: String?,
    loading: Boolean,
    onOpenBrowser: () -> Unit,
    onFinish: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertScaffold(hazeState = hazeState, onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Last.fm", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = error ?: stringResource(when {
                    !configured -> R.string.lastfm_setup_pending
                    awaitingApproval -> R.string.lastfm_browser_return
                    else -> R.string.lastfm_browser_description
                }),
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (configured) {
            AlertRule()
            AlertAction(
                label = stringResource(if (loading) R.string.signing_in else if (awaitingApproval) R.string.lastfm_finish_connecting else R.string.lastfm_open_browser),
                emphasised = true,
                enabled = !loading,
                onClick = if (awaitingApproval) onFinish else onOpenBrowser,
            )
            if (awaitingApproval) {
                AlertRule()
                AlertAction(label = stringResource(R.string.lastfm_open_browser), emphasised = false,
                    enabled = !loading, onClick = onOpenBrowser)
            }
        }
        AlertRule()
        AlertAction(label = stringResource(R.string.cancel), emphasised = false, onClick = onDismiss)
    }
}

/**
 * Manual token entry, for when the in-app login can't run — a WebView an OEM
 * has broken, or a token lifted from a desktop client.
 *
 * [error] carries back what the verification attempt said, because a token that
 * was mistyped or has expired is indistinguishable from one that works until
 * Discord is asked about it.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun DiscordTokenAlert(
    hazeState: HazeState,
    tokenInput: String,
    onTokenInputChange: (String) -> Unit,
    error: String?,
    loading: Boolean,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertScaffold(hazeState = hazeState, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 19.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.discord_token),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = error ?: stringResource(R.string.discord_token_description),
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            PillTextField(
                value = tokenInput,
                onValueChange = onTokenInputChange,
                placeholder = stringResource(R.string.token),
                enabled = !loading,
                isPassword = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (tokenInput.isNotBlank()) onSave() }),
            )
        }
        AlertRule()
        AlertAction(
            label = if (loading) stringResource(R.string.checking) else stringResource(R.string.save),
            emphasised = true,
            onClick = onSave,
            enabled = !loading && tokenInput.isNotBlank(),
        )
        AlertRule()
        AlertAction(
            label = stringResource(R.string.cancel),
            emphasised = false,
            onClick = onDismiss,
            enabled = !loading,
        )
    }
}

/**
 * One free-text presence field — an activity name, a button label.
 *
 * [message] is where the caller explains the field, including which `{...}`
 * variables it accepts, since that is the only place a user would find out.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun TextValueAlert(
    hazeState: HazeState,
    title: String,
    message: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    /** False greys Save out — for a field that isn't worth saving empty. */
    saveEnabled: Boolean = true,
    /** A third action between Save and Cancel, for a value that can be cleared. */
    onRemove: (() -> Unit)? = null,
) {
    AlertScaffold(hazeState = hazeState, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 19.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            PillTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = placeholder,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave() }),
            )
        }
        AlertRule()
        AlertAction(
            label = stringResource(R.string.save),
            emphasised = true,
            onClick = onSave,
            enabled = saveEnabled,
        )
        if (onRemove != null) {
            AlertRule()
            AlertAction(label = stringResource(R.string.remove), emphasised = false, onClick = onRemove)
        }
        AlertRule()
        AlertAction(label = stringResource(R.string.cancel), emphasised = false, onClick = onDismiss)
    }
}

/**
 * One field of a server editor: the value, its change handler, the hint, and
 * how the keyboard behaves on it. The last field always gets Done-to-save
 * while the rest advance with Next, which is what every editor below already
 * did by hand.
 */
data class EditorField(
    val value: String,
    val onChange: (String) -> Unit,
    val placeholder: String,
    val keyboardType: KeyboardType = KeyboardType.Text,
    val isPassword: Boolean = false,
)

/**
 * The shape every server editor in this app shares: a centered title, the
 * last test result in place of the description, the fields, then Test above
 * Save above Cancel — plus an optional destructive row between Save and
 * Cancel for an entry that can also be removed.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun ServerEditorAlert(
    hazeState: HazeState,
    title: String,
    description: String,
    fields: List<EditorField>,
    /** What the last test said, or null before one has been run. */
    status: String?,
    statusIsGood: Boolean,
    testing: Boolean,
    /** Whether there is enough typed in to be worth testing or saving. */
    canSubmit: Boolean,
    onTest: () -> Unit,
    onSave: () -> Unit,
    removeLabel: String? = null,
    /** Offered only when there is something stored to remove. */
    onRemove: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    AlertScaffold(hazeState = hazeState, onDismiss = { if (!testing) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 19.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = status ?: description,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = when {
                    status == null -> MaterialTheme.colorScheme.onSurface
                    statusIsGood -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                },
                textAlign = TextAlign.Center,
            )
            fields.forEachIndexed { index, field ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                PillTextField(
                    value = field.value,
                    onValueChange = field.onChange,
                    placeholder = field.placeholder,
                    enabled = !testing,
                    isPassword = field.isPassword,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = field.keyboardType,
                        imeAction = if (index == fields.lastIndex) ImeAction.Done else ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (canSubmit && !testing) onSave() },
                    ),
                )
            }
        }
        AlertRule()
        // Above Save rather than beside it: an address is worth checking before
        // it is stored, and a row of three cramped buttons is what the Material
        // dialog did badly.
        AlertAction(
            label = if (testing) stringResource(R.string.testing) else stringResource(R.string.test),
            emphasised = false,
            onClick = onTest,
            enabled = canSubmit && !testing,
        )
        AlertRule()
        AlertAction(
            label = stringResource(R.string.save),
            emphasised = true,
            onClick = onSave,
            enabled = canSubmit && !testing,
        )
        if (onRemove != null && removeLabel != null) {
            AlertRule()
            AlertAction(
                label = removeLabel,
                emphasised = false,
                destructive = true,
                onClick = onRemove,
                enabled = !testing,
            )
        }
        AlertRule()
        AlertAction(
            label = stringResource(R.string.cancel),
            emphasised = false,
            onClick = onDismiss,
            enabled = !testing,
        )
    }
}

@Composable
fun AddonEditorAlert(
    hazeState: HazeState,
    title: String,
    description: String,
    urlValue: String,
    onUrlChange: (String) -> Unit,
    urlPlaceholder: String,
    /** What the last test said, or null before one has been run. */
    status: String?,
    statusIsGood: Boolean,
    testing: Boolean,
    /** Whether there is enough typed in to be worth testing or saving. */
    canSubmit: Boolean,
    onTest: () -> Unit,
    onSave: () -> Unit,
    /** Offered only for a source already stored — there is nothing to remove otherwise. */
    onRemove: (() -> Unit)?,
    /**
     * What [onRemove] is called, for the callers that are not removing a source.
     *
     * The party server's address wears this same card — one field, an address
     * to test, four stacked actions — and "Remove source" would be the one line
     * on it still talking about addons.
     */
    removeLabel: String? = null,
    onDismiss: () -> Unit,
) {
    ServerEditorAlert(
        hazeState = hazeState,
        title = title,
        description = description,
        fields = listOf(
            EditorField(
                value = urlValue,
                onChange = onUrlChange,
                placeholder = urlPlaceholder,
                keyboardType = KeyboardType.Uri,
            ),
        ),
        status = status,
        statusIsGood = statusIsGood,
        testing = testing,
        canSubmit = canSubmit,
        onTest = onTest,
        onSave = onSave,
        removeLabel = removeLabel ?: stringResource(R.string.remove_source),
        onRemove = onRemove,
        onDismiss = onDismiss,
    )
}

/**
 * A two-action confirmation using the same frosted UIAlertController shell as
 * the addon editor. Kept here so warnings opened from settings do not fall back
 * to a visually unrelated Material dialog.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun ConfirmationAlert(
    hazeState: HazeState,
    title: String,
    description: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertScaffold(hazeState = hazeState, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 19.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = description,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        AlertRule()
        AlertAction(label = confirmLabel, emphasised = true, onClick = onConfirm)
        AlertRule()
        AlertAction(label = stringResource(R.string.cancel), emphasised = false, onClick = onDismiss)
    }
}

/**
 * Single-select list, ticked like [LyricsSourcesDialog] rather than with radio
 * buttons — same reasoning: a column of Material radios would be the one
 * Material thing left on an otherwise Apple-shaped alert.
 *
 * Picking commits immediately and closes, so there is no Save action to reach
 * for; Cancel is the only one, and it's the dismiss.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun <T> ChoiceAlert(
    hazeState: HazeState,
    title: String,
    message: String?,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    detail: (T) -> String? = { null },
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertScaffold(hazeState = hazeState, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 19.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (message != null) {
                Text(
                    text = message,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
        options.forEach { option ->
            AlertRule()
            ChoiceRow(
                label = label(option),
                detail = detail(option),
                checked = option == selected,
                onClick = { onSelect(option) },
            )
        }
        AlertRule()
        AlertAction(label = stringResource(R.string.cancel), emphasised = false, onClick = onDismiss)
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    detail: String?,
    checked: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ACTION_HEIGHT)
            // iOS washes the whole row instead of drawing a ripple inside it.
            .background(
                if (pressed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f) else Color.Transparent,
            )
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 15.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        if (checked) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.selected),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

/** The scrim + frosted card frame shared by every UIAlertController-style dialog. */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
internal fun AlertScaffold(
    hazeState: HazeState,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val shape = RoundedCornerShape(ALERT_CORNER)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SCRIM_COLOR)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(ALERT_WIDTH)
                .clip(shape)
                .then(
                    if (reduceDynamicBlur) {
                        Modifier.background(MaterialTheme.colorScheme.surface)
                    } else {
                        Modifier.optimizedHazeEffect(
                            state = hazeState,
                            style = HazeMaterials.regular(MaterialTheme.colorScheme.surface),
                        )
                    },
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                ),
            content = content,
        )
    }
}
