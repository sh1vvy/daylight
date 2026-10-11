/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.ui.feedback

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One bounded channel for app feedback. A new result replaces the previous one. */
object AppFeedback {
    const val LENGTH_SHORT = 0
    const val LENGTH_LONG = 1
    data class Message(val id: Long, val text: String, val long: Boolean)

    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val hosts = mutableSetOf<Any>() // Only touched on the main thread.
    private var sequence = 0L
    private val current = MutableStateFlow<Message?>(null)
    val messages = current.asStateFlow()

    class Request internal constructor(private val app: Context, private val text: String, private val duration: Int) {
        fun show() = publish(app, text, duration)
    }

    fun makeText(context: Context, text: CharSequence, duration: Int = LENGTH_SHORT) =
        Request(context.applicationContext, text.toString(), duration)

    fun makeText(context: Context, @StringRes text: Int, duration: Int = LENGTH_SHORT) =
        makeText(context, context.getString(text), duration)

    private fun publish(app: Context, text: String, duration: Int) = onMain {
        if (text.isBlank()) return@onMain
        if (hosts.isNotEmpty()) current.value = Message(++sequence, text, duration == LENGTH_LONG)
        else Toast.makeText(app, text, if (duration == LENGTH_LONG) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    internal fun setForeground(token: Any, foreground: Boolean) = onMain {
        if (foreground) hosts.add(token) else {
            hosts.remove(token)
            if (hosts.isEmpty()) current.value = null
        }
    }

    internal fun dismiss(id: Long) = onMain { if (current.value?.id == id) current.value = null }
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { block() }
    }
}
