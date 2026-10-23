package com.tx24.spicyplayer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import com.tx24.spicyplayer.LyricsActivity
import com.tx24.spicyplayer.R
import com.tx24.spicyplayer.ui.background.CoverColors

/**
 * The home screen widget, laid out like the floating window: the line being sung with the lines
 * around it, the song under them, a progress bar and the lyrics' credit, over the cover. What
 * shows and how is set per widget ([WidgetSettings]). [NowPlayingHolder] keeps it current.
 */
class LyricsWidget : AppWidgetProvider() {
    internal data class Content(
        val title: String,
        val artist: String,
        val lines: WidgetLines,
        val playing: Boolean,
        /** The player's position, for the progress bar and the time (lyric delays aside). */
        val positionMs: Long,
        val durationMs: Long,
        val provider: String?,
        /** The cover, small and rounded, for the song row. */
        val cover: Bitmap?,
        /** The cover shrunk to a few pixels, drawn back up for the blurred background. */
        val sample: Bitmap?,
        val colors: CoverColors?,
    )

    override fun onEnabled(context: Context) = NowPlayingHolder.start(context)

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) = NowPlayingHolder.start(context)

    override fun onDeleted(context: Context, appWidgetIds: IntArray) = WidgetSettingsStore(context).forget(appWidgetIds)

    /** Resized: laid out again for its new height. */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) =
        NowPlayingHolder.start(context)

    override fun onDisabled(context: Context) = NowPlayingHolder.stop()

    override fun onReceive(context: Context, intent: Intent) {
        val action = when (intent.action) {
            ACTION_PLAY_PAUSE -> NowPlayingHolder::playPause
            ACTION_PREVIOUS -> NowPlayingHolder::skipPrevious
            ACTION_NEXT -> NowPlayingHolder::skipNext
            else -> return super.onReceive(context, intent)
        }
        NowPlayingHolder.start(context)
        action()
    }

    internal companion object {
        private const val ACTION_PLAY_PAUSE = "com.tx24.spicyplayer.widget.PLAY_PAUSE"
        private const val ACTION_PREVIOUS = "com.tx24.spicyplayer.widget.PREVIOUS"
        private const val ACTION_NEXT = "com.tx24.spicyplayer.widget.NEXT"
        /** The background without a cover: the app's near-black. */
        const val DEFAULT_ACCENT = 0xFF121212.toInt()
        private const val BACKDROP_PX = 96
        private const val PROGRESS_MAX = 1_000

        private val lineSlots = listOf(
            R.id.widget_line_b2 to R.id.widget_note_b2,
            R.id.widget_line_b1 to R.id.widget_note_b1,
            R.id.widget_line_now to R.id.widget_note_now,
            R.id.widget_line_a1 to R.id.widget_note_a1,
            R.id.widget_line_a2 to R.id.widget_note_a2,
        )

        fun placed(context: Context): Boolean = ids(context).isNotEmpty()

        fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, LyricsWidget::class.java))

        /** Sends [content] to every placed widget, each as it's set: only what moves unless [full]. */
        fun show(context: Context, content: Content, full: Boolean) {
            val ids = ids(context)
            if (ids.isEmpty()) return
            val manager = AppWidgetManager.getInstance(context)
            val store = WidgetSettingsStore(context)
            for (id in ids) {
                val views = views(context, content, store.read(id), full, heightDp(manager, id))
                if (full) manager.updateAppWidget(id, views) else manager.partiallyUpdateAppWidget(id, views)
            }
        }

        /** What placed widgets show between them: whether any wants romanization, and any the progress bar. */
        fun wants(context: Context): Pair<Boolean, Boolean> {
            val store = WidgetSettingsStore(context)
            val all = ids(context).map(store::read)
            return all.any { it.romanization } to all.any { it.progress }
        }

        /** How tall widget [id] is in portrait, where launchers report it as the largest height. */
        private fun heightDp(manager: AppWidgetManager, id: Int): Int? = manager.getAppWidgetOptions(id).let { options ->
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 }
                ?: options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).takeIf { it > 0 }
        }

        /**
         * [content] as [chosen] lay it out, less what doesn't fit [heightDp] ([WidgetFit]). With
         * [full] off, the pictures, colours and buttons' actions are left as they were.
         */
        fun views(context: Context, content: Content, chosen: WidgetSettings, full: Boolean, heightDp: Int?): RemoteViews {
            val lines = content.lines
            val notes = (lines.before + lines.current + lines.after).any { lead ->
                (chosen.romanization && lead.romanized != null) || (chosen.translation && lead.translation != null)
            }
            val fit = WidgetFit.of(chosen, heightDp, notes)
            val settings = fit.settings
            // By the chosen place, not the fitted one: a partial update must name the layout it updates.
            val layout = if (chosen.songInfo == WidgetSettings.SongInfo.TOP) R.layout.widget_lyrics_top else R.layout.widget_lyrics
            val views = RemoteViews(context.packageName, layout)
            showLines(views, lines, settings)
            views.setInt(R.id.widget_line_now, "setMaxLines", fit.currentLines)
            val timed = settings.progress && content.durationMs > 0L
            if (timed) {
                val permille = (content.positionMs * PROGRESS_MAX / content.durationMs).toInt().coerceIn(0, PROGRESS_MAX)
                views.setProgressBar(R.id.widget_progress, PROGRESS_MAX, permille, false)
                views.setChronometer(R.id.widget_elapsed, SystemClock.elapsedRealtime() - content.positionMs, null, content.playing)
            }
            val credit = content.provider?.takeIf { settings.credit && it.isNotBlank() }
            views.setViewVisibility(R.id.widget_progress, visibleIf(timed))
            views.setViewVisibility(R.id.widget_elapsed, visibleIf(timed))
            views.setViewVisibility(R.id.widget_duration, visibleIf(timed))
            views.setTextViewText(R.id.widget_duration, clockTime(content.durationMs))
            views.setViewVisibility(R.id.widget_credit, if (credit != null) View.VISIBLE else View.INVISIBLE)
            views.setTextViewText(R.id.widget_credit, credit?.let { "Provided by $it" }.orEmpty())
            views.setViewVisibility(R.id.widget_times, visibleIf(timed || credit != null))

            val hasSong = settings.songInfo != WidgetSettings.SongInfo.HIDDEN && content.title.isNotEmpty()
            views.setViewVisibility(R.id.widget_song_row, visibleIf(hasSong || settings.controls != WidgetSettings.Controls.NONE))
            views.setViewVisibility(R.id.widget_cover, visibleIf(hasSong))
            // Kept in place while hidden, so the buttons stay on the right.
            views.setViewVisibility(R.id.widget_song, if (hasSong) View.VISIBLE else View.INVISIBLE)
            views.setTextViewText(R.id.widget_title, content.title)
            views.setTextViewText(R.id.widget_artist, content.artist)

            views.setViewVisibility(R.id.widget_play_pause, visibleIf(settings.controls != WidgetSettings.Controls.NONE))
            views.setViewVisibility(R.id.widget_previous, visibleIf(settings.controls == WidgetSettings.Controls.ALL))
            views.setViewVisibility(R.id.widget_next, visibleIf(settings.controls == WidgetSettings.Controls.ALL))
            views.setImageViewResource(
                R.id.widget_play_pause,
                if (content.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            )
            views.setContentDescription(R.id.widget_play_pause, if (content.playing) "Pause" else "Play")
            if (!full) return views

            showBackground(views, content, settings)
            if (content.cover != null) views.setImageViewBitmap(R.id.widget_cover, content.cover)
            else views.setImageViewResource(R.id.widget_cover, R.drawable.widget_cover_placeholder)

            val open = Intent(context, LyricsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
            views.setOnClickPendingIntent(R.id.widget_play_pause, broadcast(context, ACTION_PLAY_PAUSE, 1))
            views.setOnClickPendingIntent(R.id.widget_previous, broadcast(context, ACTION_PREVIOUS, 2))
            views.setOnClickPendingIntent(R.id.widget_next, broadcast(context, ACTION_NEXT, 3))
            return views
        }

        private fun showLines(views: RemoteViews, lines: WidgetLines, settings: WidgetSettings) {
            val before = lines.before.takeLast(settings.around)
            val after = lines.after.take(settings.around)
            val shown = listOf(before.getOrNull(before.size - 2), before.lastOrNull(), lines.current, after.getOrNull(0), after.getOrNull(1))
            val gravity = if (settings.centered) Gravity.CENTER_HORIZONTAL else Gravity.START
            lineSlots.forEachIndexed { slot, (lineId, noteId) ->
                val lead = shown[slot]
                val current = slot == 2
                // A line the source wrote only romanized shows that as its text.
                val text = lead?.text?.ifEmpty { lead.romanized.orEmpty() }.orEmpty()
                val note = listOfNotNull(
                    lead?.romanized?.takeIf { settings.romanization && lead.text.isNotEmpty() },
                    lead?.translation?.takeIf { settings.translation },
                ).joinToString("\n")
                views.setViewVisibility(lineId, visibleIf(text.isNotEmpty()))
                views.setViewVisibility(noteId, visibleIf(text.isNotEmpty() && note.isNotEmpty()))
                views.setTextViewText(lineId, text)
                views.setTextViewText(noteId, note)
                views.setTextViewTextSize(lineId, TypedValue.COMPLEX_UNIT_SP, if (current) settings.textSize.currentSp else settings.textSize.aroundSp)
                views.setTextViewTextSize(noteId, TypedValue.COMPLEX_UNIT_SP, settings.textSize.noteSp + if (current) 1f else 0f)
                views.setInt(lineId, "setGravity", gravity)
                views.setInt(noteId, "setGravity", gravity)
            }
        }

        private fun showBackground(views: RemoteViews, content: Content, settings: WidgetSettings) {
            val clear = settings.background == WidgetSettings.Background.CLEAR
            views.setInt(R.id.widget_root, "setBackgroundResource", if (clear) android.R.color.transparent else R.drawable.widget_background)
            val dim = settings.dim / 100f
            val shade = content.colors?.higherContrast ?: DEFAULT_ACCENT
            val color = when (settings.background) {
                WidgetSettings.Background.BLURRED_COVER -> shade
                WidgetSettings.Background.COVER_COLOR -> ColorUtils.blendARGB(content.colors?.highContrast ?: DEFAULT_ACCENT, Color.BLACK, dim)
                WidgetSettings.Background.BLACK -> Color.BLACK
                WidgetSettings.Background.CLEAR -> Color.TRANSPARENT
            }
            views.setInt(R.id.widget_backdrop, "setBackgroundColor", color)
            val blurred = content.sample?.takeIf { settings.background == WidgetSettings.Background.BLURRED_COVER }
            if (blurred != null) views.setImageViewBitmap(R.id.widget_backdrop, blurredCover(blurred, shade, dim))
            else views.setImageViewResource(R.id.widget_backdrop, android.R.color.transparent)
        }

        /** [sample] drawn back up smoothed, which reads as a heavy blur, under a veil of [shade] [dim] strong. */
        private fun blurredCover(sample: Bitmap, shade: Int, dim: Float): Bitmap {
            val out = Bitmap.createBitmap(BACKDROP_PX, BACKDROP_PX, Bitmap.Config.ARGB_8888)
            Canvas(out).apply {
                drawBitmap(sample, null, Rect(0, 0, BACKDROP_PX, BACKDROP_PX), Paint(Paint.FILTER_BITMAP_FLAG))
                drawColor(ColorUtils.setAlphaComponent(shade, (dim * 255).toInt().coerceIn(0, 255)))
            }
            return out
        }

        private fun broadcast(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, LyricsWidget::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun visibleIf(shown: Boolean) = if (shown) View.VISIBLE else View.GONE

        private fun clockTime(ms: Long): String {
            val seconds = (ms / 1_000L).coerceAtLeast(0L)
            return "%d:%02d".format(seconds / 60, seconds % 60)
        }
    }
}
