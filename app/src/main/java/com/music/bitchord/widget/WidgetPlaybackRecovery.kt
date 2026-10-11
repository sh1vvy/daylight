/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-09 through 2026-10-10.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.widget

internal enum class WidgetToggleDecision { PAUSE, PLAY, RESTART }

/** An ended queue can keep playWhenReady=true; the first tap must restart it. */
internal fun widgetToggleDecision(playWhenReady: Boolean, ended: Boolean): WidgetToggleDecision = when {
    ended -> WidgetToggleDecision.RESTART
    playWhenReady -> WidgetToggleDecision.PAUSE
    else -> WidgetToggleDecision.PLAY
}

/** APK replacement stops the old process, but the last track and transport options remain useful. */
internal fun MediaWidgetSnapshot.afterAppUpgrade(): MediaWidgetSnapshot = copy(isPlaying = false, isLoading = false, clockRunning = false)
