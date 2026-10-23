package com.tx24.spicyplayer.widget

import kotlin.math.floor

/**
 * What of a widget's [WidgetSettings] fits its height, so a short widget drops its extras instead
 * of clipping the line being sung. They go in this order: the second line on each side, the
 * progress bar and credit, the last lines around, the song row, then romanization and
 * translations. [currentLines] is how many lines the current one may wrap to, 1 to 3.
 *
 * The heights are estimates in dp from the text sizes, a little generous so nothing clips.
 * Plain Kotlin, no Android.
 */
internal data class WidgetFit(val settings: WidgetSettings, val currentLines: Int) {
    companion object {
        private const val PADDING_DP = 32f
        private const val SONG_ROW_DP = 54f
        private const val TIMELINE_DP = 34f
        private const val LINE_GAP_DP = 4f
        /** A line of text's height per sp of its size. */
        private const val LINE_HEIGHT = 1.32f
        private const val MAX_CURRENT_LINES = 3

        /**
         * [settings] fitted to [heightDp] (none known: as they are). [notes]: the lines carry a
         * romanization or translation these settings would show.
         */
        fun of(settings: WidgetSettings, heightDp: Int?, notes: Boolean): WidgetFit {
            if (heightDp == null || heightDp <= 0) return WidgetFit(settings, MAX_CURRENT_LINES)
            val steps = listOf(
                settings,
                settings.copy(around = settings.around.coerceAtMost(1)),
                settings.copy(around = settings.around.coerceAtMost(1), progress = false, credit = false),
                settings.copy(around = 0, progress = false, credit = false),
                settings.copy(around = 0, progress = false, credit = false, songInfo = WidgetSettings.SongInfo.HIDDEN, controls = WidgetSettings.Controls.NONE),
                settings.copy(
                    around = 0, progress = false, credit = false, songInfo = WidgetSettings.SongInfo.HIDDEN,
                    controls = WidgetSettings.Controls.NONE, romanization = false, translation = false,
                ),
            ).distinct()
            for (step in steps) {
                val room = heightDp - fixedDp(step, notes && (step.romanization || step.translation))
                val lines = floor(room / currentLineDp(step)).toInt()
                // Two lines for the current one before anything else goes; the last step takes what's left.
                if (lines >= 2 || step === steps.last()) return WidgetFit(step, lines.coerceIn(1, MAX_CURRENT_LINES))
            }
            return WidgetFit(steps.last(), 1)
        }

        private fun currentLineDp(settings: WidgetSettings) = settings.textSize.currentSp * LINE_HEIGHT

        /** Everything but the current line's own text. */
        private fun fixedDp(settings: WidgetSettings, notes: Boolean): Float {
            val size = settings.textSize
            val note = if (notes) size.noteSp * LINE_HEIGHT else 0f
            val around = settings.around * 2 * (size.aroundSp * LINE_HEIGHT + LINE_GAP_DP + note)
            val songRow = if (settings.songInfo != WidgetSettings.SongInfo.HIDDEN || settings.controls != WidgetSettings.Controls.NONE) SONG_ROW_DP else 0f
            val timeline = if (settings.progress || settings.credit) TIMELINE_DP else 0f
            return PADDING_DP + LINE_GAP_DP + note + around + songRow + timeline
        }
    }
}
