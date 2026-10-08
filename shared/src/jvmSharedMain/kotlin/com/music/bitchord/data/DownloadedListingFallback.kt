package com.music.bitchord.data

import com.music.bitchord.data.model.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Verify saved tracks alongside the network, retaining them for an empty/failed response. */
class DownloadedListingFallback<T>(
    scope: CoroutineScope,
    read: suspend () -> List<T>,
    publish: (List<T>) -> Unit,
) {
    @Volatile private var rows = emptyList<T>()
    private val work = scope.launch {
        rows = read()
        if (rows.isNotEmpty()) publish(rows)
    }

    suspend fun finish(online: UiState<List<T>>): List<T> {
        if (online is UiState.Success && online.data.isNotEmpty()) work.cancel() else work.join()
        return rows
    }
}
