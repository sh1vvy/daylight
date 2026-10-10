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
