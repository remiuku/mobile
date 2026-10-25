package com.tx24.spicyplayer.widget

import kotlin.math.floor

/**
 * What of a widget's [WidgetSettings] fits its height, so a short widget drops its extras instead
 * of clipping the line being sung. Only the fixed chrome is fitted here; the painter measures
 * the actual lyrics and fades surrounding lines at the edges. [currentLines] is how many
 * lines the current one may wrap to, 1 to 3.
 *
 * The heights are estimates in dp from the text sizes, a little generous so nothing clips.
 * Plain Kotlin, no Android.
 */
internal data class WidgetFit(val settings: WidgetSettings, val currentLines: Int) {
    companion object {
        /** The widget's padding, both sides together. */
        const val PADDING_DP = 32
        private const val SONG_ROW_DP = 54f
        private const val TIMELINE_DP = 34f
        /** A line of text's height per sp of its size. */
        private const val LINE_HEIGHT = 1.32f
        private const val MAX_CURRENT_LINES = 3

        /**
         * [settings] fitted to [heightDp] (none known: as they are). The result must not depend
         * on the current lyric: otherwise a translation or backing vocal hides the timeline.
         */
        fun of(settings: WidgetSettings, heightDp: Int?): WidgetFit {
            if (heightDp == null || heightDp <= 0) return WidgetFit(settings, MAX_CURRENT_LINES)
            val steps = listOf(
                settings,
                settings.copy(progress = false, credit = false),
                settings.copy(progress = false, credit = false, songInfo = WidgetSettings.SongInfo.HIDDEN, controls = WidgetSettings.Controls.NONE),
            ).distinct()
            for (step in steps) {
                val room = heightDp - fixedDp(step)
                val lines = floor(room / currentLineDp(step)).toInt()
                if (lines >= 1 || step === steps.last()) return WidgetFit(step, lines.coerceIn(1, MAX_CURRENT_LINES))
            }
            return WidgetFit(steps.last(), 1)
        }

        /** Without a known height, the lyrics area is drawn this tall. */
        private const val DEFAULT_LINES_DP = 180f

        /** How tall the lyrics area is in a widget [heightDp] tall laid out by [settings]. */
        fun linesAreaDp(settings: WidgetSettings, heightDp: Int?): Float {
            if (heightDp == null || heightDp <= 0) return DEFAULT_LINES_DP
            val songRow = if (settings.songInfo != WidgetSettings.SongInfo.HIDDEN || settings.controls != WidgetSettings.Controls.NONE) SONG_ROW_DP else 0f
            val timeline = if (settings.progress || settings.credit) TIMELINE_DP else 0f
            return (heightDp - PADDING_DP - songRow - timeline).coerceAtLeast(currentLineDp(settings))
        }

        private fun currentLineDp(settings: WidgetSettings) = settings.textSize.currentSp * LINE_HEIGHT

        /** Everything but the current line's own text. */
        private fun fixedDp(settings: WidgetSettings): Float {
            val songRow = if (settings.songInfo != WidgetSettings.SongInfo.HIDDEN || settings.controls != WidgetSettings.Controls.NONE) SONG_ROW_DP else 0f
            val timeline = if (settings.progress || settings.credit) TIMELINE_DP else 0f
            return PADDING_DP + songRow + timeline
        }
    }
}
