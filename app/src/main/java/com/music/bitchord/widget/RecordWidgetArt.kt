package com.music.bitchord.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import android.util.LruCache
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.music.bitchord.data.model.artworkAt
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** Only the small cropped disc is rasterized, once per cover. All text/controls are native. */
internal object RecordWidgetArt {
    private const val WIDTH = 512
    private const val HEIGHT = 276
    private val discs = object : LruCache<String, Bitmap>(3 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val failures = ConcurrentHashMap<String, Long>()
    private val loadLock = Mutex()

    fun peek(url: String?): Bitmap? = url?.let(discs::get)?.takeUnless { it.isRecycled }

    suspend fun cover(context: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        peek(url)?.let { return it }
        return loadLock.withLock {
            peek(url)?.let { return@withLock it }
            failures[url]?.let { failedAt ->
                if (SystemClock.elapsedRealtime() - failedAt in 0 until 30_000L) return@withLock null
            }
            val request = ImageRequest.Builder(context)
                .data(url.artworkAt(640) ?: url)
                .size(512)
                .allowHardware(false)
                .build()
            val result = runCatching { SingletonImageLoader.get(context).execute(request) }.getOrNull()
            val source = (result as? SuccessResult)?.image?.toBitmap()
            if (source == null) {
                failures[url] = SystemClock.elapsedRealtime()
                return@withLock null
            }
            val disc = cropDisc(source)
            failures.remove(url)
            discs.put(url, disc)
            disc
        }
    }

    internal fun cropDisc(source: Bitmap): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centerX = WIDTH / 2f
        val centerY = 20f
        val radius = WIDTH / 2f
        val scale = WIDTH.toFloat() / minOf(source.width, source.height).coerceAtLeast(1)
        val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply {
                setScale(scale, scale)
                postTranslate(centerX - source.width * scale / 2f, centerY - source.height * scale / 2f)
            })
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.shader = shader
        canvas.drawCircle(centerX, centerY, radius, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.25f
        paint.color = 0x24FFFFFF
        for (fraction in listOf(.53f, .69f, .83f, .97f)) canvas.drawCircle(centerX, centerY, radius * fraction, paint)
        paint.style = Paint.Style.FILL
        paint.color = 0xDDE4E2E6.toInt()
        canvas.drawCircle(centerX, centerY, 50f, paint)
        paint.color = 0xFFBCBABE.toInt()
        canvas.drawCircle(centerX, centerY, 35f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.WHITE
        canvas.drawCircle(centerX, centerY, 48f, paint)
        paint.style = Paint.Style.FILL
        paint.color = 0xFF29262C.toInt()
        canvas.drawCircle(centerX, centerY, 9f, paint)
        return bitmap
    }

    fun clear() {
        discs.evictAll()
        failures.clear()
    }
}
