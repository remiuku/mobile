package com.tx24.spicyplayer.widget

import com.tx24.spicyplayer.lyrics.TimedLine
import com.tx24.spicyplayer.lyrics.TimedWord
import com.tx24.spicyplayer.lyrics.spicy.models.LineRole

/**
 * What the home screen widget shows at a moment: the line being sung ([current]), up to
 * [MAX_AROUND] lines on either side of it, and when that next changes ([changesAtMs], a lyric
 * position; null when it won't). Each widget shows as many of [before] and [after] as it's set to.
 *
 * Plain Kotlin, no Android.
 */
internal data class WidgetLines(
    val before: List<Lead>,
    val current: Lead,
    val after: List<Lead>,
    val changesAtMs: Long?,
) {
    /** One lead line as the widget writes it, with its romanization and the source's translation. */
    data class Lead(
        val startMs: Long,
        val endMs: Long,
        val text: String,
        val romanized: String? = null,
        val translation: String? = null,
    )

    companion object {
        /** The most lines a widget shows on each side of the current one. */
        const val MAX_AROUND = 2
        /** Between two lines at least this far apart the widget shows [BREAK] instead of the line just sung. */
        const val BREAK_GAP_MS = 4_000L
        const val BREAK = "♪"

        /** A notice in place of lyrics. */
        fun notice(text: String): WidgetLines = WidgetLines(emptyList(), Lead(0L, 0L, text), emptyList(), null)

        /**
         * The lead lines of [lines] as text: background vocals and interludes left out, and the
         * words spaced as the lyrics view spaces them (a space before each word not [TimedWord.attached]).
         */
        fun leads(lines: List<TimedLine>): List<Lead> = lines
            .filter { it.role == LineRole.LEAD }
            .map { line ->
                val romanized = line.words.takeIf { words -> words.any { !it.romanized.isNullOrBlank() } }
                    ?.let { words -> joined(words) { it.romanized ?: it.text } }?.takeIf { it.isNotEmpty() }
                Lead(
                    line.startMs,
                    line.endMs,
                    joined(line.words) { it.text },
                    romanized,
                    line.translation?.tidy()?.takeIf { it.isNotEmpty() },
                )
            }
            .filter { it.text.isNotEmpty() || it.romanized != null }

        /**
         * [words] as one line of their [part]s. Empty parts (the text half of a word split from its
         * romanization) are skipped; a part that starts with a space starts a word of its own.
         */
        private fun joined(words: List<TimedWord>, part: (TimedWord) -> String): String {
            val out = StringBuilder()
            for (word in words) {
                val text = part(word)
                if (text.isEmpty()) continue
                if (out.isNotEmpty() && !word.attached && !out.last().isWhitespace() && !text.first().isWhitespace()) out.append(' ')
                out.append(text)
            }
            return out.toString().tidy()
        }

        /** Lyrics without timing: the first line as the current one, the next ones after it. */
        fun unsynced(leads: List<Lead>): WidgetLines {
            if (leads.isEmpty()) return notice("")
            return WidgetLines(emptyList(), leads[0], leads.drop(1).take(MAX_AROUND * 2), null)
        }

        /** What shows at [positionMs] of synced [leads] (in time order). */
        fun at(leads: List<Lead>, positionMs: Long): WidgetLines {
            if (leads.isEmpty()) return notice("")
            val index = leads.indexOfLast { it.startMs <= positionMs }
            if (index < 0) return WidgetLines(emptyList(), breakBefore(leads[0]), leads.take(MAX_AROUND), leads[0].startMs)
            val line = leads[index]
            val before = leads.subList((index - MAX_AROUND).coerceAtLeast(0), index)
            val following = leads.getOrNull(index + 1)
                ?: return WidgetLines(before, line, emptyList(), null)
            val after = leads.subList(index + 1, (index + 1 + MAX_AROUND).coerceAtMost(leads.size))
            val breakAhead = following.startMs - line.endMs >= BREAK_GAP_MS
            return when {
                // In a break the line just sung joins the ones above it.
                breakAhead && positionMs >= line.endMs ->
                    WidgetLines((before + line).takeLast(MAX_AROUND), breakBefore(following), after, following.startMs)
                breakAhead -> WidgetLines(before, line, after, line.endMs)
                else -> WidgetLines(before, line, after, following.startMs)
            }
        }

        private fun breakBefore(next: Lead) = Lead(next.startMs, next.startMs, BREAK)

        private val WHITESPACE = Regex("\\s+")

        private fun String.tidy() = replace(WHITESPACE, " ").trim()
    }
}
