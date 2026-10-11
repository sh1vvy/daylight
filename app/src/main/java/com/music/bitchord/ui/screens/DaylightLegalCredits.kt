/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Legal information stays available offline and is loaded only when requested. */
@Composable
internal fun DaylightLegalCredits() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var document by remember { mutableStateOf<Pair<String, String>?>(null) }
    fun open(title: String, asset: String) {
        scope.launch {
            val text = withContext(Dispatchers.IO) { context.assets.open("legal/$asset").bufferedReader().use { it.readText() } }
                .replace(Regex("<!-- DAYLIGHT-ATTRIBUTION\\r?\\n.*?-->\\r?\\n\\r?\\n", RegexOption.DOT_MATCHES_ALL), "")
            document = title to text
        }
    }
    Column {
        Text("Daylight modifications by sh1vvy.\nCopyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text("GNU GPLv3. You may copy, modify and redistribute under its terms. No warranty. Original authors retain their rights.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { open("Attribution and origin", "DAYLIGHT-NOTICE.md") }) { Text("Attribution and origin") }
        TextButton(onClick = { open("GNU GPLv3", "LICENSE") }) { Text("Read GPLv3 license") }
        TextButton(onClick = { open("Copyright and contributions", "COPYRIGHT.md") }) { Text("Copyright and contributions") }
    }
    document?.let { (title, text) ->
        AlertDialog(onDismissRequest = { document = null }, title = { Text(title) },
            text = { Text(text, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { document = null }) { Text("Close") } })
    }
}
