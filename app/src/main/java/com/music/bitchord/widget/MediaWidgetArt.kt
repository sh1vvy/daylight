package com.music.bitchord.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.LruCache
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.NOTIFICATION_ART_PX
import com.music.bitchord.data.model.artworkAt
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * One small cover shared by every responsive widget layout. Surfaces and controls
 * are native views: no full-widget bitmap, blur, progress timer or animation work.
 * The app's Coil loader shares the existing memory/disk cache and artwork ladder.
 */
internal object MediaWidgetArt {
    const val COVER_SIZE_PX = 256

    private val covers = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val failures = ConcurrentHashMap<String, Long>()
    private val loadLock = Mutex()

    fun peek(url: String?): Bitmap? = url?.let { covers[it] }?.takeUnless { it.isRecycled }

    suspend fun cover(context: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        peek(url)?.let { return it }
        // Widgets placed at several sizes must not fetch/crop the same image repeatedly.
        return loadLock.withLock {
            peek(url)?.let { return@withLock it }
            val failedAt = failures[url]
            if (failedAt != null && SystemClock.elapsedRealtime() - failedAt in 0 until 30_000L) {
                return@withLock null
            }
            val source = load(context, url, CARD_ART_PX) ?: load(context, url, NOTIFICATION_ART_PX)
            if (source == null) {
                failures[url] = SystemClock.elapsedRealtime()
                return@withLock null
            }
            val bitmap = Bitmap.createBitmap(COVER_SIZE_PX, COVER_SIZE_PX, Bitmap.Config.ARGB_8888)
            val scale = COVER_SIZE_PX.toFloat() / minOf(source.width, source.height).coerceAtLeast(1)
            val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply {
                    setScale(scale, scale)
                    postTranslate((COVER_SIZE_PX - source.width * scale) / 2f, (COVER_SIZE_PX - source.height * scale) / 2f)
                })
            }
            Canvas(bitmap).drawRoundRect(
                RectF(0f, 0f, COVER_SIZE_PX.toFloat(), COVER_SIZE_PX.toFloat()),
                COVER_SIZE_PX * .19f, COVER_SIZE_PX * .19f,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader },
            )
            failures.remove(url)
            covers.put(url, bitmap)
            bitmap
        }
    }

    private suspend fun load(context: Context, url: String, px: Int): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(url.artworkAt(px) ?: url)
            .size(px)
            .allowHardware(false)
            .build()
        val result = runCatching { SingletonImageLoader.get(context).execute(request) }.getOrNull()
        return (result as? SuccessResult)?.image?.toBitmap()
    }

    fun clear() {
        covers.evictAll()
        failures.clear()
    }
}
