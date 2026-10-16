package com.tx24.spicyplayer.lyrics.spicy.parser

import android.os.Build
import androidx.annotation.RequiresApi

object GraphemeSegmenter {
    fun segment(text: String, sdkInt: Int = Build.VERSION.SDK_INT): List<String> {
        if (text.isEmpty()) return emptyList()
        val graphemes = if (sdkInt >= 24 && Build.VERSION.SDK_INT >= 24) segmentWithIcu(text) else fallback(text)
        return joinConjuncts(graphemes)
    }

    /**
     * Joins a grapheme ending in a virama (or ZWJ) to the one after it, so a conjunct such as
     * Tamil "ஸ்ரீ" stays one unit and keeps its ligature when each unit is drawn on its own.
     * A virama before whitespace stays put rather than swallowing the space.
     */
    internal fun joinConjuncts(graphemes: List<String>): List<String> {
        if (graphemes.size < 2) return graphemes
        val result = ArrayList<String>(graphemes.size)
        for (grapheme in graphemes) {
            val last = result.lastOrNull()
            if (last != null && endsWithJoiner(last) && grapheme.isNotBlank()) {
                result[result.lastIndex] = last + grapheme
            } else {
                result += grapheme
            }
        }
        return result
    }

    private fun endsWithJoiner(text: String): Boolean =
        text.isNotEmpty() && text.codePointBefore(text.length) in JOINERS

    private val JOINERS = setOf(
        0x094D, 0x09CD, 0x0A4D, 0x0ACD, 0x0B4D, 0x0BCD, 0x0C4D, 0x0CCD, 0x0D4D, 0x0DCA,
        0x1039, 0x17D2, 0x200D,
    )

    @RequiresApi(24)
    private fun segmentWithIcu(text: String): List<String> {
        val iterator = android.icu.text.BreakIterator.getCharacterInstance()
        iterator.setText(text)
        val result = ArrayList<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != android.icu.text.BreakIterator.DONE) {
            result += text.substring(start, end)
            start = end
            end = iterator.next()
        }
        return result
    }

    internal fun fallback(text: String): List<String> {
        val result = ArrayList<String>()
        var start = 0
        var index = 0
        var regionalCount = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val width = Character.charCount(codePoint)
            val joinsPrevious = index > start && (
                isCombining(codePoint) || isVariationSelector(codePoint) || isEmojiModifier(codePoint) ||
                    codePoint == 0x20E3 || text.codePointBefore(index) == 0x200D || codePoint == 0x200D ||
                    (isRegionalIndicator(codePoint) && regionalCount % 2 == 1)
                )
            if (!joinsPrevious && index > start) {
                result += text.substring(start, index)
                start = index
                regionalCount = 0
            }
            if (isRegionalIndicator(codePoint)) regionalCount++ else if (codePoint != 0x200D) regionalCount = 0
            index += width
        }
        result += text.substring(start)
        return result
    }

    private fun isCombining(codePoint: Int): Boolean = when (Character.getType(codePoint)) {
        Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt() -> true
        else -> false
    }

    private fun isVariationSelector(codePoint: Int) = codePoint in 0xFE00..0xFE0F || codePoint in 0xE0100..0xE01EF
    private fun isEmojiModifier(codePoint: Int) = codePoint in 0x1F3FB..0x1F3FF
    private fun isRegionalIndicator(codePoint: Int) = codePoint in 0x1F1E6..0x1F1FF
}
