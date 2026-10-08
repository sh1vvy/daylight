package com.music.bitchord.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/** Existing provider identities stay intact so an upgrade keeps placed widgets. */
abstract class MediaWidget : AppWidgetProvider() {
    protected abstract val fallbackWidthDp: Int

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        renderAsync(context, ids)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle?) {
        renderAsync(context, intArrayOf(id))
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        MaterialWidgetRenderer.forget(ids)
    }

    private fun renderAsync(context: Context, ids: IntArray) {
        val pending = goAsync()
        MaterialWidgetRenderer.renderAsync(context, ids, fallbackWidthDp.toFloat(), pill = false) {
            runCatching { pending.finish() }
        }
    }

    companion object {
        const val WIDE_LAYOUT_MIN_DP = 300

        /** Event-driven only: track, transport, favorite, shuffle, loading and Jam lock changes. */
        fun refresh(context: Context) = MaterialWidgetRenderer.refresh(context)
    }
}

class MediaWidgetSquare : MediaWidget() { override val fallbackWidthDp = 110 }
class MediaWidgetWide : MediaWidget() { override val fallbackWidthDp = 250 }
