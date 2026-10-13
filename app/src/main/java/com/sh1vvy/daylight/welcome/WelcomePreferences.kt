/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-13 through 2026-10-13.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.welcome

import android.content.Context

/** A choice is recorded only after guest confirmation or successful account authorization. */
class WelcomePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("daylight_entry", Context.MODE_PRIVATE)
    val completed: Boolean get() = preferences.getBoolean("completed", false)
    fun complete() { preferences.edit().putBoolean("completed", true).apply() }
}
