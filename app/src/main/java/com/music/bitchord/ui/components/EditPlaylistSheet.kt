package com.music.bitchord.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.library.PlaylistCoverStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlaylistSheet(
    target: BrowseTarget,
    onDismiss: () -> Unit,
    onSave: (String, String?, Boolean, (Result<Unit>) -> Unit) -> Unit,
) {
    var name by rememberSaveable(target.browseId) { mutableStateOf(target.title) }
    var draft by remember(target.browseId) { mutableStateOf<String?>(null) }
    var coverChanged by remember(target.browseId) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val imageError = stringResource(R.string.playlist_image_error)
    val saveError = stringResource(R.string.playlist_edit_error)
    val choose = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            busy = true; error = null
            try {
                val prepared = PlaylistCoverStore.prepare(uri)
                PlaylistCoverStore.discard(draft)
                draft = prepared; coverChanged = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = imageError }
            finally { busy = false }
        }
    }
    val latestDraft by rememberUpdatedState(draft)
    DisposableEffect(target.browseId) {
        onDispose { PlaylistCoverStore.discard(latestDraft) }
    }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.edit_playlist), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(18.dp))
            AsyncImage(model = if (coverChanged) draft else target.thumbnailUrl, contentDescription = stringResource(R.string.widget_artwork),
                contentScale = ContentScale.Crop, modifier = Modifier.size(140.dp).clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh))
            Row {
                TextButton(enabled = !busy, onClick = { choose.launch("image/*") }) { Text(stringResource(R.string.choose_photo)) }
                if (target.thumbnailUrl != null || draft != null) TextButton(enabled = !busy, onClick = {
                    PlaylistCoverStore.discard(draft); draft = null; coverChanged = true
                }) { Text(stringResource(R.string.reset_playlist_cover)) }
            }
            Text(stringResource(R.string.playlist_cover_local_note), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(name, { name = it.take(150) }, enabled = !busy, singleLine = true,
                label = { Text(stringResource(R.string.playlist_name)) }, shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                Button(enabled = !busy && name.isNotBlank() && (name.trim() != target.title || coverChanged),
                    modifier = Modifier.weight(1f), onClick = {
                        busy = true; error = null
                        onSave(name.trim(), draft, coverChanged) { result ->
                            busy = false
                            result.onSuccess { onDismiss() }.onFailure { error = saveError }
                        }
                    }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.save)) }
            }
        }
    }
}
