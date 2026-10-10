package com.music.bitchord.widget

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.music.bitchord.R

internal object RecordWidgetRenderer {
    fun views(context: Context, snapshot: MediaWidgetSnapshot, cover: Bitmap?, size: SizeF): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_media_record)
        cover?.let { views.setImageViewBitmap(R.id.widget_art, it) }
        val title = if (snapshot.hasTrack) snapshot.title.ifBlank { context.getString(R.string.widget_daylight_name) }
            else context.getString(R.string.widget_nothing_played)
        val artist = if (snapshot.hasTrack) snapshot.artist else context.getString(R.string.widget_choose_music)
        views.setTextViewText(R.id.widget_title, title)
        views.setTextViewText(R.id.widget_artist, artist)
        views.setViewVisibility(R.id.widget_artist, if (artist.isBlank() ||
            !RecordWidgetSizing.showArtist(size.height, context.resources.configuration.fontScale)) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_record_wave,
            if (RecordWidgetSizing.showWave(size.height, context.resources.configuration.fontScale)) View.VISIBLE else View.GONE)
        views.setImageViewResource(R.id.widget_toggle, if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
        val showSides = snapshot.hasTrack && RecordWidgetSizing.showSideControls(size.width)
        views.setViewVisibility(R.id.widget_previous, if (showSides) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_next, if (showSides) View.VISIBLE else View.GONE)

        val now = SystemClock.elapsedRealtime()
        val progress = snapshot.recordProgress(now, System.currentTimeMillis())
        val showTime = snapshot.hasTrack && progress.knownDuration &&
            RecordWidgetSizing.showTime(size.height, context.resources.configuration.fontScale)
        views.setViewVisibility(R.id.widget_record_time, if (showTime) View.VISIBLE else View.GONE)
        // The thin bar is an honest event-time position; the launcher clock alone advances.
        // No alarm, job, service polling or per-second bitmap IPC is needed.
        views.setProgressBar(R.id.widget_record_progress, 1_000, progress.progress, false)
        views.setViewVisibility(R.id.widget_record_progress, if (showTime) View.VISIBLE else View.GONE)
        views.setChronometer(R.id.widget_record_clock, now - progress.positionMs, null, showTime && progress.clockRunning)
        views.setTextViewText(R.id.widget_record_duration, " / ${recordWidgetTime(progress.durationMs)}")
        views.setContentDescription(R.id.widget_record_progress,
            "${recordWidgetTime(progress.positionMs)} / ${recordWidgetTime(progress.durationMs)}")
        val state = context.getString(when {
            snapshot.controlsLocked -> R.string.widget_state_locked
            snapshot.isLoading && snapshot.isPlaying -> R.string.widget_state_loading
            snapshot.isPlaying -> R.string.widget_state_playing
            snapshot.hasTrack -> R.string.widget_state_paused
            else -> R.string.widget_daylight_name
        })
        views.setContentDescription(R.id.widget_root, if (snapshot.hasTrack)
            context.getString(R.string.widget_accessibility_track, listOf(title, artist).filter(String::isNotBlank).joinToString(", "), state)
        else context.getString(R.string.widget_open_bitchord))
        return views
    }
}
