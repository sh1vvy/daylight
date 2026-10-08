package com.music.bitchord.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/** The existing 4×1 provider; short launcher rows get a native, usable compact layout. */
class MediaWidgetPill : AppWidgetProvider() {
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
        MaterialWidgetRenderer.renderAsync(context, ids, 250f, pill = true) {
            runCatching { pending.finish() }
        }
    }
}
