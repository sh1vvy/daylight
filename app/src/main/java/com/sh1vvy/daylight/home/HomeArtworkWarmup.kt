/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.music.bitchord.data.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Warm a small preview window on feed change, rather than launching requests on scroll. */
@Composable
fun HomeArtworkWarmup(state: UiState<List<HomeShelf>>) {
    val context = LocalContext.current
    val shelves = (state as? UiState.Success)?.data.orEmpty()
    LaunchedEffect(shelves) {
        if (shelves.isEmpty()) return@LaunchedEffect
        val tracks = homeQuickMix(shelves).take(3).map { it.thumbnailUrl to 720 }
        val collections = shelves.filterNot { it.isRecentHomeShelf() || it.isQuickPicksHomeShelf() }
            .take(7).flatMap { shelf -> shelf.items.take(2).map { it.thumbnailUrl to CARD_ART_PX } }
        val previews = (tracks + collections).filter { !it.first.isNullOrBlank() }.distinct().take(20)
        val loader = SingletonImageLoader.get(context)
        val gate = Semaphore(2)
        supervisorScope {
            previews.map { (url, pixels) -> async {
                gate.withPermit {
                    loader.execute(ImageRequest.Builder(context).data(url.artworkAt(pixels)).size(pixels).crossfade(false).build())
                }
            } }.awaitAll()
        }
    }
}
