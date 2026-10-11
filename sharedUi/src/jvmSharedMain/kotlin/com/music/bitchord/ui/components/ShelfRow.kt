/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.ui.components

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * What a sideways shelf is wrapped in, on top of the row itself.
 *
 * Nothing on the phone, where a finger drags the row. A window's mouse can't
 * swipe sideways, so the desktop wraps each row in the arrows it pages with,
 * the drag that pulls it, and the horizontal wheel.
 */
interface ShelfRowChrome {
    @Composable
    fun Wrap(state: LazyListState, row: @Composable () -> Unit)
}

private object NoShelfRowChrome : ShelfRowChrome {
    @Composable
    override fun Wrap(state: LazyListState, row: @Composable () -> Unit) = row()
}

val LocalShelfRowChrome = staticCompositionLocalOf<ShelfRowChrome> { NoShelfRowChrome }

/** A sideways-scrolling shelf, in whatever the platform wraps one in — see [ShelfRowChrome]. */
@Composable
fun ShelfRow(
    contentPadding: PaddingValues,
    horizontalArrangement: Arrangement.Horizontal,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    content: LazyListScope.() -> Unit,
) {
    LocalShelfRowChrome.current.Wrap(state) {
        LazyRow(
            modifier = modifier,
            state = state,
            flingBehavior = flingBehavior,
            contentPadding = contentPadding,
            horizontalArrangement = horizontalArrangement,
            content = content,
        )
    }
}
