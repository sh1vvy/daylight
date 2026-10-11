/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.ui.screens

import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.snapFlingBehavior
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.math.abs
import kotlin.math.pow

private const val HOME_FLING_ITEMS = 3
internal fun homeSettlingSpring() = spring<Float>(dampingRatio = .9f, stiffness = 400f)

/** Preserve ordinary release speeds; smoothly resist extreme flicks, in item-relative units. */
internal fun homeFlingVelocity(velocity: Float, stride: Float): Float {
    if (stride <= 0f || !stride.isFinite() || !velocity.isFinite()) return 0f
    val limit = stride * 12f
    val ratio = abs(velocity.toDouble()) / limit
    return (velocity / (1.0 + ratio.pow(4)).pow(.25)).toFloat()
}

private class HomeFlingSpeedLimit(
    private val native: TargetedFlingBehavior,
    private val stride: () -> Float,
) : TargetedFlingBehavior {
    override suspend fun ScrollScope.performFling(
        initialVelocity: Float,
        onRemainingDistanceUpdated: (Float) -> Unit,
    ): Float = with(native) {
        performFling(homeFlingVelocity(initialVelocity, stride()), onRemainingDistanceUpdated)
    }
}

/** Native decay carries a flick across a few covers, then one spring settles the final cover. */
@Composable
internal fun rememberHomePagerFling(state: PagerState): TargetedFlingBehavior {
    val snap = remember { homeSettlingSpring() }
    val native = PagerDefaults.flingBehavior(state,
        pagerSnapDistance = remember { PagerSnapDistance.atMost(HOME_FLING_ITEMS) },
        snapAnimationSpec = snap)
    return remember(native, state) { HomeFlingSpeedLimit(native) {
        (state.layoutInfo.pageSize + state.layoutInfo.pageSpacing).toFloat()
    } }
}

/** Same decay/settling for Home's three-song columns, without an unbounded row fling. */
@Composable
internal fun rememberHomeRowFling(state: LazyListState): TargetedFlingBehavior {
    val decay = rememberSplineBasedDecay<Float>()
    val snap = remember { homeSettlingSpring() }
    return remember(state, decay, snap) {
        val nativeLayout = SnapLayoutInfoProvider(state, SnapPosition.Start)
        fun stride(): Float = state.layoutInfo.let { layout ->
            (layout.visibleItemsInfo.firstOrNull()?.size ?: 0).toFloat() + layout.mainAxisItemSpacing
        }
        val boundedLayout = object : SnapLayoutInfoProvider by nativeLayout {
            override fun calculateApproachOffset(velocity: Float, decayOffset: Float): Float {
                // Leave the final column to the native snap. No intermediate page snaps.
                val limit = stride() * (HOME_FLING_ITEMS - 1)
                return nativeLayout.calculateApproachOffset(velocity, decayOffset).coerceIn(-limit, limit)
            }
        }
        HomeFlingSpeedLimit(snapFlingBehavior(boundedLayout, decay, snap), ::stride)
    }
}
