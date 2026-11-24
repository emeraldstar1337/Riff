package dev.lad.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.widget.RemoteViews
import coil.imageLoader
import coil.request.ImageRequest
import dev.lad.LadApplication
import dev.lad.MainActivity
import dev.lad.R
import dev.lad.i18n.AppLanguage
import dev.lad.playback.PlaybackState
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*

open class RiffWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refreshAsync(context, goAsync())
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        options: Bundle,
    ) {
        refreshAsync(context, goAsync())
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action =
            intent.action?.takeIf { it.startsWith("dev.lad.widget.") }?.substringAfterLast('.')
                ?: return
        if (action !in listOf("toggle", "next", "previous", "favorite")) return
        val app = context.applicationContext as? LadApplication ?: return
        val pending = goAsync()
        app.persistenceScope.launch {
            try {
                withTimeoutOrNull(4000) { app.playback.awaitCommand(action) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val update = AtomicReference<Job?>(null)
        @Volatile private var signature: String? = null

        fun changed(context: Context, state: PlaybackState) {
            val key =
                "${state.current?.id}:${state.current?.artwork}:${state.current?.title}:${state.current?.artist}:${state.current?.favorite}:${state.playing}"
            if (key == signature) return
            signature = key
            refresh(context)
        }

        private fun refreshAsync(context: Context, pending: PendingResult) {
            val app = context.applicationContext as? LadApplication
            if (app == null) {
                pending.finish()
                return
            }
            val job = refreshInternal(context, app)
            if (job == null) {
                pending.finish()
                return
            }
            app.persistenceScope.launch {
                try {
                    withTimeoutOrNull(8000) { job.join() }
                } finally {
                    pending.finish()
                }
            }
        }

        fun refresh(context: Context) {
            val app = context.applicationContext as? LadApplication ?: return
            refreshInternal(context, app)
        }

        private fun refreshInternal(context: Context, app: LadApplication): Job? {
            val manager = AppWidgetManager.getInstance(context)
            val ids =
                (manager.getAppWidgetIds(ComponentName(context, RiffWidget::class.java)).toList() +
                        manager
                            .getAppWidgetIds(ComponentName(context, RiffArtworkWidget::class.java))
                            .toList())
                    .distinct()
            if (ids.isEmpty()) return null
            val job =
                app.persistenceScope.launch {
                    val state = app.playback.state.value
                    val prefs = app.getSharedPreferences("widget_state", Context.MODE_PRIVATE)
                    state.current?.let {
                        prefs
                            .edit()
                            .putString("title", it.title)
                            .putString("artist", it.artist)
                            .putString("artwork", it.artwork)
                            .apply()
                    }
                    val local = AppLanguage.context(app)
                    val resources = dev.lad.i18n.TextResources(local)
                    val title =
                        state.current?.title
                            ?: prefs.getString("title", null)
                            ?: local.getString(R.string.lad)
                    val artist =
                        state.current?.artist
                            ?: prefs.getString("artist", null)
                            ?: local.getString(R.string.widget_open_music)
                    val art = state.current?.artwork ?: prefs.getString("artwork", null)
                    val bitmap =
                        try {
                            (app.imageLoader
                                    .execute(
                                        ImageRequest.Builder(app)
                                            .data(art)
                                            .size(480)
                                            .allowHardware(false)
                                            .build()
                                    )
                                    .drawable as? BitmapDrawable)
                                ?.bitmap
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                    ids.forEach { id ->
                        val options = manager.getAppWidgetOptions(id)
                        val large =
                            manager.getAppWidgetInfo(id)?.provider?.className ==
                                RiffArtworkWidget::class.java.name ||
                                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) >= 150
                        val views =
                            RemoteViews(
                                app.packageName,
                                if (large) R.layout.widget_artwork else R.layout.widget_compact,
                            )
                        views.setTextViewText(R.id.widget_title, resources.metadata(title))
                        views.setTextViewText(R.id.widget_artist, resources.metadata(artist))
                        if (bitmap != null) views.setImageViewBitmap(R.id.widget_cover, bitmap)
                        else
                            views.setImageViewResource(
                                R.id.widget_cover,
                                R.drawable.ic_riff_foreground,
                            )
                        views.setImageViewResource(
                            R.id.widget_play,
                            if (state.playing) R.drawable.widget_pause else R.drawable.widget_play,
                        )
                        views.setContentDescription(
                            R.id.widget_play,
                            local.getString(
                                if (state.playing) R.string.pause else R.string.library_screens_play
                            ),
                        )
                        views.setContentDescription(
                            R.id.widget_next,
                            local.getString(R.string.next),
                        )
                        views.setContentDescription(
                            R.id.widget_previous,
                            local.getString(R.string.previous),
                        )
                        views.setOnClickPendingIntent(
                            R.id.widget_root,
                            PendingIntent.getActivity(
                                app,
                                0,
                                Intent(app, MainActivity::class.java),
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                            ),
                        )
                        listOf(
                                R.id.widget_play to "toggle",
                                R.id.widget_next to "next",
                                R.id.widget_previous to "previous",
                            )
                            .forEach { (view, action) ->
                                views.setOnClickPendingIntent(
                                    view,
                                    PendingIntent.getBroadcast(
                                        app,
                                        view,
                                        Intent(app, RiffWidget::class.java)
                                            .setAction("dev.lad.widget.$action"),
                                        PendingIntent.FLAG_UPDATE_CURRENT or
                                            PendingIntent.FLAG_IMMUTABLE,
                                    ),
                                )
                            }
                        manager.updateAppWidget(id, views)
                    }
                }
            update.getAndSet(job)?.cancel()
            return job
        }
    }
}

class RiffArtworkWidget : RiffWidget()
