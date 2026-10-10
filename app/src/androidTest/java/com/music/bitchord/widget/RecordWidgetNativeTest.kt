package com.music.bitchord.widget

import android.content.res.Configuration
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.R
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Inflate the actual native widget, including its PendingIntents, on a launcher-sized surface. */
@RunWith(AndroidJUnit4::class)
class RecordWidgetNativeTest {
    @Test fun actualRemoteViewsFitSmallAndLargeCardsAcrossSystemThemes() {
        render(110, 110, 1f, night = false, playing = true)
        render(110, 110, 2f, night = true, playing = false)
        render(130, 220, 1f, night = false, playing = true)
        render(160, 160, 1f, night = true, playing = true)
        render(260, 110, 1f, night = false, playing = true)
        render(180, 220, 1f, night = false, playing = true)
        render(240, 280, 1f, night = false, playing = true)
        render(300, 300, 1f, night = true, playing = false)
        render(180, 220, 2f, night = true, playing = false)
    }

    @Test fun launcherProviderIsFixedAtTwoCellsInBothDirections() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = AppWidgetManager.getInstance(context).installedProviders.single {
            it.provider.packageName == context.packageName && it.provider.className == MediaWidgetRecord::class.java.name
        }
        assertEquals(2, provider.targetCellWidth)
        assertEquals(2, provider.targetCellHeight)
        assertEquals(AppWidgetProviderInfo.RESIZE_NONE, provider.resizeMode)
    }

    @Test fun jamGuestsAndUnavailableSkipsCannotSendTransportCommands() {
        render(240, 280, 1f, night = false, playing = true, locked = true)
        render(180, 220, 1f, night = true, playing = false, hasPrevious = false, hasNext = false)
    }

    @Test fun loadedArtworkKeepsItsCircularCropAndStaysCenteredWhenTheCardShrinks() {
        // A deterministic cover exercises the production shader/crop without network or accounts.
        val source = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 512f, 512f,
                intArrayOf(Color.rgb(84, 90, 105), Color.rgb(168, 138, 155), Color.rgb(238, 198, 137)),
                null, Shader.TileMode.CLAMP)
        }
        Canvas(source).drawRect(0f, 0f, 512f, 512f, paint)
        val disc = RecordWidgetArt.cropDisc(source)
        source.recycle()
        assertEquals(512, disc.width)
        assertEquals(276, disc.height)
        assertEquals(0, Color.alpha(disc.getPixel(0, 275)))
        assertTrue(Color.alpha(disc.getPixel(256, 260)) > 0)
        render(180, 220, 1f, night = false, playing = true, cover = disc)
        render(180, 220, 2f, night = true, playing = false, cover = disc)
        render(300, 300, 1f, night = false, playing = true, cover = disc)
        disc.recycle()
    }

    private fun render(
        widthDp: Int, heightDp: Int, fontScale: Float, night: Boolean, playing: Boolean,
        locked: Boolean = false, hasPrevious: Boolean = true, hasNext: Boolean = true,
        cover: Bitmap? = null,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val base = instrumentation.targetContext
            val config = Configuration(base.resources.configuration).apply {
                this.fontScale = fontScale
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val context = base.createConfigurationContext(config)
            val now = SystemClock.elapsedRealtime()
            val snapshot = MediaWidgetSnapshot(
                mediaId = "widget-test", title = "Golden hour", artist = "Daylight mix", artworkUrl = null,
                isPlaying = playing, hasPrevious = hasPrevious, hasNext = hasNext, controlsLocked = locked,
                positionMs = 89_000L, durationMs = 206_000L, capturedAtElapsedMs = now,
                capturedAtEpochMs = System.currentTimeMillis(), clockRunning = playing,
            )
            val views = MaterialWidgetRenderer.recordViews(context, snapshot, cover, SizeF(widthDp.toFloat(), heightDp.toFloat()))
                .apply(context, null)
            val density = context.resources.displayMetrics.density
            val width = (widthDp * density).toInt()
            val height = (heightDp * density).toInt()
            views.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            views.layout(0, 0, width, height)
            val toggle = views.findViewById<ImageView>(R.id.widget_toggle)
            val previous = views.findViewById<ImageView>(R.id.widget_previous)
            val next = views.findViewById<ImageView>(R.id.widget_next)
            assertEquals(if (widthDp >= 160) View.VISIBLE else View.GONE, previous.visibility)
            assertEquals(previous.visibility, next.visibility)
            assertEquals((48 * density).toInt(), toggle.width)
            assertEquals((48 * density).toInt(), toggle.height)
            val bounds = Rect()
            toggle.getDrawingRect(bounds)
            (views as ViewGroup).offsetDescendantRectToMyCoords(toggle, bounds)
            assertTrue(bounds.top >= 0)
            assertTrue(bounds.bottom <= height)
            assertTrue(bounds.left >= 0 && bounds.right <= width)
            val title = views.findViewById<View>(R.id.widget_title)
            title.getDrawingRect(bounds)
            views.offsetDescendantRectToMyCoords(title, bounds)
            assertTrue("Title stays inside the two-cell card", bounds.top >= 0 && bounds.bottom <= height)
            if (cover != null) {
                val art = views.findViewById<ImageView>(R.id.widget_art)
                val drawable = art.drawable
                val drawableBounds = RectF(0f, 0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
                art.imageMatrix.mapRect(drawableBounds)
                assertTrue(kotlin.math.abs(drawableBounds.centerX() - art.width / 2f) < 2f)
                assertTrue(drawableBounds.left >= -1f && drawableBounds.right <= art.width + 1f)
                assertTrue(drawableBounds.top >= -1f && drawableBounds.bottom <= art.height + 1f)
            }
            assertTrue(toggle.hasOnClickListeners())
            assertEquals(!hasPrevious || locked, previous.imageAlpha != 255)
            assertEquals(!hasNext || locked, next.imageAlpha != 255)
            if (locked) {
                assertEquals(90, toggle.imageAlpha)
                assertTrue(toggle.contentDescription.toString().contains(context.getString(R.string.widget_control_locked, context.getString(R.string.widget_pause))))
            } else {
                assertEquals(255, toggle.imageAlpha)
                if (!hasPrevious) assertFalse(previous.isEnabled)
                if (!hasNext) assertFalse(next.isEnabled)
            }
            val progress = views.findViewById<ProgressBar>(R.id.widget_record_progress)
            assertTrue(progress.progress in 430..440)
            val clock = views.findViewById<Chronometer>(R.id.widget_record_clock)
            assertTrue(SystemClock.elapsedRealtime() - clock.base in 89_000L..90_000L)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            views.draw(Canvas(bitmap))
            val file = File(base.getExternalFilesDir("widget-qa"), "record-${widthDp}x${heightDp}-${if (night) "dark" else "light"}-${fontScale}-${if (locked) "jam" else "normal"}${if (cover != null) "-art" else ""}.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            clock.stop()
        }
    }
}
