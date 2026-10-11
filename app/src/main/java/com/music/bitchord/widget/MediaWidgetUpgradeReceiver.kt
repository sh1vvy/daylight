/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-09 through 2026-10-09.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

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
