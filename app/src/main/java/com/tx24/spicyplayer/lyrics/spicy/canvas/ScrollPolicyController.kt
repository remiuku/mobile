package com.tx24.spicyplayer.lyrics.spicy.canvas

import com.tx24.spicyplayer.lyrics.spicy.animation.LyricsAnimator
import com.tx24.spicyplayer.lyrics.spicy.models.Line
import com.tx24.spicyplayer.lyrics.spicy.models.LineRole
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

internal enum class ScrollMotion { NONE, SNAP, SMOOTH }

internal data class ScrollDecision(val targetIndex: Int?, val motion: ScrollMotion)

internal class ScrollPolicyController {
    private var initialized = false
    private var lastTimeMs = 0L
    private var lastTargetIndex: Int? = null

    fun reset() {
        initialized = false
        lastTimeMs = 0L
        lastTargetIndex = null
    }

    /**
     * @param leadMs Early Scroll: the line is picked as if the song were this far ahead, so the
     * view starts moving before the line lights up. Seeks are still judged on the real time.
     */
    fun decide(lines: List<Line>, timeMs: Long, explicitSeek: Boolean = false, leadMs: Long = 0L): ScrollDecision {
        val replayToZero = initialized && timeMs <= 100L && lastTimeMs > 1_000L
        val largeSeek = initialized && abs(timeMs - lastTimeMs) > 1_000L
        val jumped = !initialized || explicitSeek || replayToZero || largeSeek
        val scrollTimeMs = timeMs + leadMs.coerceAtLeast(0L)
        // Between lines the view stays on the last target; only a jump into
        // a gap needs somewhere to land.
        val target = selectTargetIndex(lines, scrollTimeMs)
            ?: if (jumped) lastStartedIndex(lines, scrollTimeMs) else lastTargetIndex
        val motion = when {
            target == null -> ScrollMotion.NONE
            jumped -> ScrollMotion.SNAP
            target != lastTargetIndex -> ScrollMotion.SMOOTH
            else -> ScrollMotion.NONE
        }
        initialized = true
        lastTimeMs = timeMs
        lastTargetIndex = target
        return ScrollDecision(target, motion)
    }

    companion object {
        /** Reference PIN_LOOKAHEAD: how many real lines ahead the anchor line is checked against. */
        private const val PIN_LOOKAHEAD = 2

        /**
         * The line to keep centred. Null when no line is active: the view then stays put.
         *
         * - Background lines belong to the lead line above them.
         * - A background line still active under a later active line is the tail of a line
         *   already passed, so it is ignored rather than dragging the view back up.
         * - So is one whose own lead line has already ended, even with nothing below it active
         *   yet: in the gap before the next line it would move the view to a group that's done,
         *   then on again a moment later. One whose lead is still to come leads into it, and counts.
         * - The top active line keeps the view as long as it (and its background lines) ends
         *   before the line [PIN_LOOKAHEAD] real lines further down starts; otherwise the first
         *   active line if the active ones are adjacent, else the last.
         *
         * Additions: once an interlude's dots start closing, the line after it is the target,
         * so the scroll and the closing gap move together. And the view never passes a line
         * above the target that is still to be sung (lyrics where a line comes in after the one
         * below it): it waits there, with the target just below in view, rather than going on
         * and coming back up.
         */
        fun selectTargetIndex(lines: List<Line>, timeMs: Long): Int? {
            if (lines.isEmpty()) return null
            val active = lines.indices.filter { timeMs in lines[it].startMs..lines[it].endMs }
            if (active.isEmpty()) return null

            active.firstOrNull { lines[it].role == LineRole.INTERLUDE }?.let { index ->
                val interlude = lines[index]
                if (timeMs > interlude.endMs - LyricsAnimator.PRE_HIDDEN_DOT_LINE_MS) {
                    val next = (index + 1 until lines.size).firstOrNull { lines[it].role == LineRole.LEAD }
                    if (next != null) return next
                }
            }

            fun isBg(i: Int) = lines[i].role == LineRole.BACKGROUND
            fun leadOf(i: Int): Int {
                var j = i
                while (j > 0 && isBg(j)) j--
                return j
            }

            val frontLead = active.maxOf(::leadOf)
            val activeLeads = active
                .filterNot { isBg(it) && (leadOf(it) < frontLead || timeMs > lines[leadOf(it)].endMs) }
                .map(::leadOf)
                .distinct()
            if (activeLeads.isEmpty()) return null

            val anchor = activeLeads.first()
            val lookahead = (anchor + 1 until lines.size).filterNot(::isBg).drop(PIN_LOOKAHEAD - 1).firstOrNull()
            var groupEnd = lines[anchor].endMs
            var k = anchor + 1
            while (k < lines.size && isBg(k)) groupEnd = maxOf(groupEnd, lines[k++].endMs)
            val picked = if (lookahead == null || groupEnd <= lines[lookahead].startMs) anchor else {
                val first = activeLeads.first()
                val last = activeLeads.last()
                if (last - first <= 1) first else last
            }
            return waitForLaterLinesAbove(lines, picked, timeMs, ::isBg)
        }

        /** [target], or the line just above it while that one is yet to start (sung after it). */
        private fun waitForLaterLinesAbove(lines: List<Line>, target: Int, timeMs: Long, isBg: (Int) -> Boolean): Int {
            var result = target
            repeat(PIN_LOOKAHEAD) {
                val above = (result - 1 downTo 0).firstOrNull { !isBg(it) && lines[it].role == LineRole.LEAD } ?: return result
                if (lines[above].startMs <= timeMs) return result
                result = above
            }
            return result
        }

        /** The last line that has started: where a seek into a gap between lines lands. */
        fun lastStartedIndex(lines: List<Line>, timeMs: Long): Int? =
            lines.indexOfLast { it.role != LineRole.BACKGROUND && it.startMs <= timeMs }.takeIf { it >= 0 }
                ?: lines.indexOfFirst { it.role != LineRole.BACKGROUND }.takeIf { it >= 0 }

        fun flingDecayMultiplier(deltaTimeSeconds: Float): Float =
            exp(ln(0.95f) * deltaTimeSeconds.coerceAtLeast(0f) * 60f)

        fun anchorY(viewportHeightPx: Float, focusFraction: Float): Float =
            viewportHeightPx * focusFraction.coerceIn(0f, 1f)
    }
}
