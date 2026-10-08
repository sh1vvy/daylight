package com.music.bitchord.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** A single private receiver normalizes persisted state after an APK update. */
class MediaWidgetUpgradeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext
        // A fresh service may have published real state before this broadcast arrived.
        MediaWidgetSnapshot.resetAfterAppUpgrade(app)
        val pending = goAsync()
        MaterialWidgetRenderer.refresh(app) { runCatching { pending.finish() } }
    }
}
