package com.music.bitchord.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.music.bitchord.MainActivity
import com.music.bitchord.R
import com.music.bitchord.playback.PlayerDeepLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal object MaterialWidgetRenderer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val generations = ConcurrentHashMap<Int, Long>()
    private val sequence = AtomicLong()

    fun forget(ids: IntArray) { ids.forEach(generations::remove) }

    fun renderAsync(context: Context, ids: IntArray, fallbackWidth: Float, pill: Boolean, record: Boolean = false, finish: () -> Unit = {}): Job {
        val app = context.applicationContext
        // Reserve versions before dispatch, so a slow old fetch cannot overwrite a new pause/track/resize.
        val version = sequence.incrementAndGet()
        ids.forEach { generations[it] = version }
        return scope.launch {
            try {
                val manager = AppWidgetManager.getInstance(app)
                val snapshot = MediaWidgetSnapshot.load(app)
                val cached = if (record) RecordWidgetArt.peek(snapshot.artworkUrl) else MediaWidgetArt.peek(snapshot.artworkUrl)
                push(app, manager, ids, version, snapshot, cached, fallbackWidth, pill, record)
                if (cached == null && !snapshot.artworkUrl.isNullOrBlank()) {
                    val cover = withTimeoutOrNull(6_000L) {
                        if (record) RecordWidgetArt.cover(app, snapshot.artworkUrl) else MediaWidgetArt.cover(app, snapshot.artworkUrl)
                    }
                    if (cover != null) push(app, manager, ids, version, snapshot, cover, fallbackWidth, pill, record)
                }
            } catch (e: Exception) {
                Log.w("DaylightWidget", "Unable to update widgets", e)
            } finally {
                finish()
            }
        }
    }

    fun refresh(context: Context, finish: () -> Unit = {}) {
        val app = context.applicationContext
        scope.launch {
            try {
                val manager = AppWidgetManager.getInstance(app)
                val renders = mutableListOf<Job>()
                for ((provider, fallback, pill) in listOf(
                    Triple(MediaWidgetSquare::class.java, 110f, false),
                    Triple(MediaWidgetWide::class.java, 250f, false),
                    Triple(MediaWidgetPill::class.java, 250f, true),
                )) {
                    val ids = manager.getAppWidgetIds(ComponentName(app, provider))
                    if (ids.isNotEmpty()) renders += renderAsync(app, ids, fallback, pill)
                }
                val recordIds = manager.getAppWidgetIds(ComponentName(app, MediaWidgetRecord::class.java))
                if (recordIds.isNotEmpty()) renders += renderAsync(app, recordIds, 160f, pill = false, record = true)
                renders.joinAll()
            } catch (e: Exception) {
                Log.w("DaylightWidget", "Unable to refresh widgets", e)
            } finally {
                finish()
            }
        }
    }

    private fun push(
        context: Context, manager: AppWidgetManager, ids: IntArray, version: Long,
        snapshot: MediaWidgetSnapshot, cover: Bitmap?, fallbackWidth: Float, pill: Boolean, record: Boolean,
    ) {
        for (id in ids) {
            if (generations[id] != version) continue
            val remoteViews = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // The launcher switches these in portrait/landscape and while resizing without
                // waking the app. One small artwork bitmap is shared by every branch of the map.
                val sizes = if (record) listOf(
                    SizeF(110f, 110f), SizeF(130f, 160f), SizeF(160f, 160f),
                    SizeF(180f, 200f), SizeF(260f, 110f), SizeF(260f, 200f),
                ) else if (pill) listOf(
                    SizeF(180f, 56f), SizeF(240f, 56f), SizeF(340f, 56f),
                    SizeF(460f, 56f), SizeF(260f, 96f),
                    SizeF(180f, 190f), SizeF(260f, 190f), SizeF(300f, 190f),
                ) else listOf(
                    SizeF(110f, 110f), SizeF(164f, 110f), SizeF(260f, 110f),
                    SizeF(110f, 190f), SizeF(164f, 190f), SizeF(260f, 190f), SizeF(300f, 190f),
                )
                RemoteViews(sizes.associateWith { size -> if (record) recordViews(context, snapshot, cover, size) else views(context, snapshot, cover, size, pill) })
            } else {
                val size = measuredSize(context, manager, id, fallbackWidth, pill, record)
                if (record) recordViews(context, snapshot, cover, size) else views(context, snapshot, cover, size, pill)
            }
            // The artwork wait above can overlap a newer state; validate immediately before IPC.
            if (generations[id] != version) continue
            runCatching { manager.updateAppWidget(id, remoteViews) }
                .onFailure { Log.w("DaylightWidget", "Launcher rejected widget update", it) }
        }
    }

    private fun measuredSize(context: Context, manager: AppWidgetManager, id: Int, fallback: Float, pill: Boolean, record: Boolean): SizeF {
        val options = manager.getAppWidgetOptions(id)
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val width = options.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val height = options.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        return SizeF(width.takeIf { it > 0 }?.toFloat() ?: fallback,
            height.takeIf { it > 0 }?.toFloat() ?: if (record) 160f else if (pill) 96f else 190f)
    }

    internal fun recordViews(context: Context, snapshot: MediaWidgetSnapshot, cover: Bitmap?, size: SizeF): RemoteViews {
        val views = RecordWidgetRenderer.views(context, snapshot, cover, size)
        val open = openPlayer(context)
        views.setOnClickPendingIntent(android.R.id.background, open)
        views.setOnClickPendingIntent(R.id.widget_root, open)
        bindControl(views, context, R.id.widget_toggle, MediaWidgetActions.ACTION_TOGGLE, snapshot, true, open,
            context.getString(if (!snapshot.hasTrack) R.string.widget_open_bitchord else if (snapshot.isPlaying) R.string.widget_pause else R.string.widget_play))
        bindControl(views, context, R.id.widget_previous, MediaWidgetActions.ACTION_PREVIOUS, snapshot, snapshot.hasPrevious, open,
            context.getString(R.string.widget_previous))
        bindControl(views, context, R.id.widget_next, MediaWidgetActions.ACTION_NEXT, snapshot, snapshot.hasNext, open,
            context.getString(R.string.widget_next))
        return views
    }

    private fun views(context: Context, snapshot: MediaWidgetSnapshot, cover: Bitmap?, size: SizeF, pill: Boolean): RemoteViews {
        val layout = MaterialWidgetSizing.layout(size.width, size.height, pill)
        val resource = when (layout) {
            MaterialWidgetLayout.COMPACT -> R.layout.widget_media_compact
            MaterialWidgetLayout.TALL -> R.layout.widget_media_tall
            MaterialWidgetLayout.WIDE -> R.layout.widget_media_wide
            MaterialWidgetLayout.PILL -> R.layout.widget_media_pill
            MaterialWidgetLayout.PILL_SLIM -> R.layout.widget_media_pill_slim
        }
        val views = RemoteViews(context.packageName, resource)
        cover?.let { views.setImageViewBitmap(R.id.widget_art, it) }
        val title = if (snapshot.hasTrack) snapshot.title.ifBlank { context.getString(R.string.widget_daylight_name) } else context.getString(R.string.widget_nothing_played)
        val artist = if (snapshot.hasTrack) snapshot.artist else context.getString(R.string.widget_choose_music)
        views.setTextViewText(R.id.widget_title, title)
        views.setTextViewText(R.id.widget_artist, artist)
        val largeTextInShortRow = context.resources.configuration.fontScale > 1.3f &&
            (layout == MaterialWidgetLayout.PILL_SLIM || (layout == MaterialWidgetLayout.PILL && size.height < 124f) ||
                (layout == MaterialWidgetLayout.COMPACT && size.height < 130f))
        views.setViewVisibility(R.id.widget_artist, if (artist.isBlank() || largeTextInShortRow) View.GONE else View.VISIBLE)
        val stateLabel = context.getString(when {
            snapshot.controlsLocked -> R.string.widget_state_locked
            snapshot.isLoading && snapshot.isPlaying -> R.string.widget_state_loading
            snapshot.isPlaying -> R.string.widget_state_playing
            snapshot.hasTrack -> R.string.widget_state_paused
            else -> R.string.widget_daylight_name
        })
        views.setTextViewText(R.id.widget_status, stateLabel)
        views.setViewVisibility(R.id.widget_status, if (MaterialWidgetSizing.showStatus(size.height, layout)) View.VISIBLE else View.GONE)
        if (layout == MaterialWidgetLayout.PILL_SLIM) {
            views.setViewVisibility(R.id.widget_art, if (size.width >= 240f) View.VISIBLE else View.GONE)
        }
        views.setViewVisibility(R.id.widget_previous, if (snapshot.hasTrack && MaterialWidgetSizing.showPrevious(size.width, layout)) View.VISIBLE else View.GONE)
        val secondary = snapshot.hasTrack && MaterialWidgetSizing.showSecondaryControls(size.width, layout)
        views.setViewVisibility(R.id.widget_like, if (secondary) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_shuffle, if (secondary) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_next, if (snapshot.hasTrack) View.VISIBLE else View.GONE)

        views.setImageViewResource(R.id.widget_toggle, if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
        views.setInt(R.id.widget_toggle, "setBackgroundResource", if (snapshot.isPlaying) R.drawable.widget_material_playing else R.drawable.widget_material_play)
        views.setImageViewResource(R.id.widget_like, if (snapshot.isLiked) R.drawable.ic_widget_heart_filled else R.drawable.ic_widget_heart)
        views.setInt(R.id.widget_like, "setBackgroundResource", if (snapshot.isLiked) R.drawable.widget_toggle_on else R.drawable.widget_transport_button)
        views.setContentDescription(R.id.widget_like, context.getString(if (snapshot.isLiked) R.string.remove_from_liked else R.string.like))
        views.setInt(R.id.widget_shuffle, "setBackgroundResource", if (snapshot.shuffleEnabled) R.drawable.widget_toggle_on else R.drawable.widget_transport_button)
        views.setContentDescription(R.id.widget_shuffle, context.getString(if (snapshot.shuffleEnabled) R.string.shuffle_on else R.string.shuffle_off))

        val open = openPlayer(context)
        views.setOnClickPendingIntent(android.R.id.background, open)
        val rootId = if (layout == MaterialWidgetLayout.PILL || layout == MaterialWidgetLayout.PILL_SLIM) R.id.widget_pill else R.id.widget_root
        views.setOnClickPendingIntent(rootId, open)
        views.setContentDescription(rootId, if (snapshot.hasTrack) {
            context.getString(R.string.widget_accessibility_track, listOf(title, artist).filter(String::isNotBlank).joinToString(", "), stateLabel)
        } else context.getString(R.string.widget_open_bitchord))
        bindControl(views, context, R.id.widget_toggle, MediaWidgetActions.ACTION_TOGGLE, snapshot, true, open,
            context.getString(if (!snapshot.hasTrack) R.string.widget_open_bitchord else if (snapshot.isPlaying) R.string.widget_pause else R.string.widget_play))
        bindControl(views, context, R.id.widget_previous, MediaWidgetActions.ACTION_PREVIOUS, snapshot, snapshot.hasPrevious, open, context.getString(R.string.widget_previous))
        bindControl(views, context, R.id.widget_next, MediaWidgetActions.ACTION_NEXT, snapshot, snapshot.hasNext, open, context.getString(R.string.widget_next))
        bindControl(views, context, R.id.widget_shuffle, MediaWidgetActions.ACTION_SHUFFLE, snapshot, true, open,
            context.getString(if (snapshot.shuffleEnabled) R.string.shuffle_on else R.string.shuffle_off))
        // Liking is personal and stays available when a Jam host owns the transport.
        views.setOnClickPendingIntent(R.id.widget_like, if (snapshot.hasTrack) MediaWidgetActions.pendingIntent(context, MediaWidgetActions.ACTION_LIKE) else open)
        return views
    }

    private fun bindControl(
        views: RemoteViews, context: Context, id: Int, action: String, snapshot: MediaWidgetSnapshot,
        available: Boolean, open: PendingIntent, description: String,
    ) {
        val locked = snapshot.hasTrack && snapshot.controlsLocked
        views.setInt(id, "setImageAlpha", if (locked || (snapshot.hasTrack && !available)) 90 else 255)
        views.setBoolean(id, "setEnabled", !snapshot.hasTrack || locked || available)
        views.setContentDescription(id, if (locked) context.getString(R.string.widget_control_locked, description) else description)
        views.setOnClickPendingIntent(id, when {
            !snapshot.hasTrack || locked -> open
            !available -> null
            else -> MediaWidgetActions.pendingIntent(context, action)
        })
    }

    private fun openPlayer(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(PlayerDeepLink.EXTRA_OPEN_PLAYER, true),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
