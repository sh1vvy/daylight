/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-12 through 2026-10-12.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.search

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

enum class SearchDockSurface { NAVIGATION, PLAYER, SEARCH }

/** Capture the actual resting dock geometry; the new scene owns its interpolated surfaces. */
class SearchDockSurfaceMorph {
    var suppressOriginalSurfaces by mutableStateOf(false)
    private var normalBounds = Rect.Zero
    private val origins = mutableMapOf<SearchDockSurface, Rect>()

    fun normalContainer() = Modifier.onGloballyPositioned {
        if (!suppressOriginalSurfaces) normalBounds = it.boundsInRoot()
    }

    fun record(kind: SearchDockSurface? = null, visible: Boolean = true) = Modifier.onGloballyPositioned {
        if (!suppressOriginalSurfaces && visible) {
            val bounds = it.boundsInRoot()
            // Inline navigation is also circular. Its position, rather than
            // aspect ratio alone, distinguishes it from the Search circle.
            val role = kind ?: if (bounds.width >= bounds.height * 1.3f ||
                (normalBounds.width > 0f && bounds.center.x < normalBounds.center.x))
                SearchDockSurface.NAVIGATION else SearchDockSurface.SEARCH
            origins[role] = bounds
        }
    }

    fun origin(kind: SearchDockSurface, width: Float, normalHeight: Float): Rect? {
        if (normalBounds.width <= 0f) return null
        val rect = origins[kind] ?: return null
        val scale = width / normalBounds.width
        return Rect((rect.left - normalBounds.left) * scale,
            normalHeight + rect.top - normalBounds.bottom,
            (rect.right - normalBounds.left) * scale,
            normalHeight + rect.bottom - normalBounds.bottom)
    }
}

val LocalSearchDockSurfaceMorph = staticCompositionLocalOf<SearchDockSurfaceMorph?> { null }
