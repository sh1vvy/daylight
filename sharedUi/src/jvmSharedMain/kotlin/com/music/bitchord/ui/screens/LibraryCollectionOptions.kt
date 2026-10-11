/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.LibrarySort
import com.music.bitchord.data.settings.LibraryViewType
import com.music.bitchord.sharedui.resources.*
import com.music.bitchord.ui.AppUi
import org.jetbrains.compose.resources.stringResource

/** One anchored, themed control for the browser's display and order. */
@Composable
fun LibraryCollectionOptions(key: String, onSortChange: (LibrarySort) -> Unit) {
    var open by remember(key) { mutableStateOf(false) }
    val views by AppUi.host.libraryCollectionViews.collectAsStateWithLifecycle()
    val sort by AppUi.host.librarySort.collectAsStateWithLifecycle()
    val artists = key == "Artists"
    val view = views[key] ?: LibraryViewType.GRID
    Box {
        IconButton({ open = true }) { Icon(Icons.Rounded.Sort, stringResource(Res.string.library_collection_options),
            tint = MaterialTheme.colorScheme.onSurface) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(26.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 0.dp) {
            if (!artists) {
                LibraryViewType.entries.forEach { option ->
                    DropdownMenuItem(text = { Text(stringResource(if (option == LibraryViewType.GRID) Res.string.library_grid else Res.string.library_list)) },
                        leadingIcon = { Icon(if (option == LibraryViewType.GRID) Icons.Rounded.GridView else Icons.AutoMirrored.Rounded.ViewList, null, modifier = Modifier.size(21.dp)) },
                        trailingIcon = { if (option == view) Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp)) },
                        onClick = { AppUi.host.setLibraryCollectionView(key, option); open = false })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            }
            (if (artists) listOf(LibrarySort.TITLE_ASC, LibrarySort.TITLE_DESC) else LibrarySort.entries.toList()).forEach { option ->
                DropdownMenuItem(text = { Text(stringResource(when (option) {
                    LibrarySort.DEFAULT -> Res.string.library_order_recent
                    LibrarySort.TITLE_ASC -> Res.string.library_order_title
                    LibrarySort.TITLE_DESC -> Res.string.library_order_title_reverse
                })) }, trailingIcon = { if (option == sort || artists && sort == LibrarySort.DEFAULT && option == LibrarySort.TITLE_ASC)
                    Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp)) },
                    onClick = { onSortChange(option); open = false })
            }
        }
    }
}
