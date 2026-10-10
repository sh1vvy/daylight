package com.music.bitchord.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.RecommendationLanguages
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecommendationLanguageSheet(selected: Set<String>, onDismiss: () -> Unit, onSave: (Set<String>) -> Unit) {
    var draft by remember(selected) { mutableStateOf(selected) }
    var query by remember { mutableStateOf("") }
    val locale = Locale.getDefault()
    val languages = remember(query, locale) {
        RecommendationLanguages.catalog.filter {
            it.name.contains(query, true) || it.nativeName.contains(query, true) ||
                Locale.forLanguageTag(it.code).getDisplayLanguage(locale).contains(query, true)
        }.sortedBy { Locale.forLanguageTag(it.code).getDisplayLanguage(locale) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = 24.dp).imePadding()) {
            Text(stringResource(R.string.recommendation_languages), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.recommendation_languages_hint), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(24.dp), placeholder = { Text(stringResource(R.string.search_languages)) })
            TextButton(onClick = { draft = emptySet() }) { Text(stringResource(R.string.recommendation_languages_reset)) }
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 340.dp)) {
                items(languages, key = { it.code }) { language ->
                    val toggle = { draft = if (language.code in draft) draft - language.code else draft + language.code }
                    Row(Modifier.fillMaxWidth().clickable(onClick = toggle).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Locale.forLanguageTag(language.code).getDisplayLanguage(locale))
                            if (language.nativeName != language.name) Text(language.nativeName,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Checkbox(language.code in draft, { toggle() })
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSave(draft) }) { Text(stringResource(R.string.save)) }
            }
        }
    }
}
