package com.music.bitchord.data.web

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient

/** Owns one native WebView so renderer recovery and normal disposal cannot destroy it twice. */
internal class RecoverableWebView(context: Context) : WebView(context) {
    var isDisposed: Boolean = false
        private set

    fun dispose(rendererGone: Boolean = false) {
        if (isDisposed) return
        isDisposed = true
        // Android requires removing a WebView from its parent before destroying it.
        (parent as? ViewGroup)?.removeView(this)
        if (!rendererGone) {
            stopLoading()
            onPause()
            webChromeClient = null
            webViewClient = WebViewClient()
        }
        // A dead renderer cannot be used again, including by the later onRelease callback.
        destroy()
    }
}
