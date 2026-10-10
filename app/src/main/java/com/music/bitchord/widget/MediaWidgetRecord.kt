package com.music.bitchord.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/** A separate provider leaves every existing placed widget intact. */
class MediaWidgetRecord : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = render(context, ids)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle?) =
        render(context, intArrayOf(id))

    override fun onDeleted(context: Context, ids: IntArray) = MaterialWidgetRenderer.forget(ids)

    private fun render(context: Context, ids: IntArray) {
        val pending = goAsync()
        MaterialWidgetRenderer.renderAsync(context, ids, 160f, pill = false, record = true) {
            runCatching { pending.finish() }
        }
    }
}
