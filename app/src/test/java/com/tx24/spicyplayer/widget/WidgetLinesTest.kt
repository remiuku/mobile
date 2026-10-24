package com.tx24.spicyplayer.widget

import com.tx24.spicyplayer.lyrics.TimedLine
import com.tx24.spicyplayer.lyrics.TimedWord
import com.tx24.spicyplayer.lyrics.spicy.models.LineRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLinesTest {
    private fun line(startMs: Long, endMs: Long, vararg words: String, role: LineRole = LineRole.LEAD) =
        TimedLine(startMs, endMs, words.map { TimedWord(it, startMs, endMs, attached = false) }, role = role)

    private val leads = WidgetLines.leads(
        listOf(
            line(1_000, 3_000, "Hello", "world"),
            line(2_000, 3_000, "(echo)", role = LineRole.BACKGROUND),
            line(3_000, 5_000, "Second"),
            line(5_000, 6_000, "Third"),
            line(12_000, 14_000, "After", "the", "break"),
        ),
    )

    private fun WidgetLines.texts() = Triple(before.map { it.text }, current.text, after.map { it.text })

    @Test
    fun `background vocals go with their lead line and words are spaced`() {
        assertEquals(listOf("Hello world", "Second", "Third", "After the break"), leads.map { it.text })
        assertEquals(listOf("echo", null, null, null), leads.map { it.background })
    }

    @Test
    fun `background vocals find their lead by group and get brackets`() {
        val grouped = WidgetLines.leads(
            listOf(
                TimedLine(0, 1_000, listOf(TimedWord("One", 0, 1_000, attached = false)), groupId = 1),
                TimedLine(1_000, 2_000, listOf(TimedWord("Two", 1_000, 2_000, attached = false)), groupId = 2, oppositeAligned = true),
                TimedLine(500, 900, listOf(TimedWord("ooh", 500, 900, attached = false)), role = LineRole.BACKGROUND, groupId = 1),
            ),
        )
        assertEquals(listOf("ooh", null), grouped.map { it.background })
        assertEquals(listOf(false, true), grouped.map { it.oppositeAligned })
    }

    @Test
    fun `syllables of one word stay together`() {
        val words = listOf(
            TimedWord("What's", 0, 300, attached = false),
            TimedWord("your", 300, 600, attached = false),
            TimedWord("al", 600, 800, attached = false),
            TimedWord("i", 800, 900, attached = true),
            TimedWord("bi?", 900, 1_200, attached = true),
        )
        assertEquals("What's your alibi?", WidgetLines.leads(listOf(TimedLine(0, 1_200, words))).single().text)
    }

    @Test
    fun `romanization and translation come with their line`() {
        val words = listOf(
            TimedWord("毎", 0, 250, false, romanized = "mai"),
            TimedWord("日", 250, 500, true, romanized = "nichi"),
            TimedWord("同", 500, 750, true, romanized = " ona"),
            TimedWord("じ", 750, 1_000, true, romanized = "ji"),
        )
        val lead = WidgetLines.leads(listOf(TimedLine(0, 1_000, words, translation = " Every  day "))).single()
        assertEquals("毎日同じ", lead.text)
        assertEquals("mainichi onaji", lead.romanized)
        assertEquals("Every day", lead.translation)
        assertNull(leads.first().romanized)
    }

    @Test
    fun `before the first line the break shows with the first lines after it`() {
        val shown = WidgetLines.at(leads, 0)
        assertEquals(Triple(emptyList<String>(), WidgetLines.BREAK, listOf("Hello world", "Second")), shown.texts())
        assertTrue(shown.current.interlude)
        // No dot is lit as the break starts; the next change is the first one finishing.
        assertEquals(0, shown.dots)
        assertTrue(shown.changesAtMs!! in 1..999)
    }

    @Test
    fun `a line shows with the lines around it until the next one starts`() {
        val shown = WidgetLines.at(leads, 3_500)
        assertEquals(Triple(listOf("Hello world"), "Second", listOf("Third", "After the break")), shown.texts())
        assertEquals(5_000L, shown.changesAtMs)
    }

    @Test
    fun `a long gap shows the break once the line ends`() {
        assertEquals(6_000L, WidgetLines.at(leads, 5_500).changesAtMs)
        val shown = WidgetLines.at(leads, 7_000)
        assertEquals(Triple(listOf("Second", "Third"), WidgetLines.BREAK, listOf("After the break")), shown.texts())
        assertEquals(0, shown.dots)
        // Each dot lights as it finishes, one by one through the break, then the next line.
        val lit = (6_000L until 12_000L step 100).map { WidgetLines.at(leads, it).dots }
        assertEquals(listOf(0, 1, 2, 3), lit.distinct())
        assertEquals(12_000L, WidgetLines.at(leads, 11_900).changesAtMs)
    }

    @Test
    fun `the last line stays with nothing after it`() {
        val shown = WidgetLines.at(leads, 20_000)
        assertEquals(Triple(listOf("Second", "Third"), "After the break", emptyList<String>()), shown.texts())
        assertNull(shown.changesAtMs)
    }

    @Test
    fun `unsynced lyrics start at the first line`() {
        val shown = WidgetLines.unsynced(leads)
        assertEquals(Triple(emptyList<String>(), "Hello world", listOf("Second", "Third", "After the break")), shown.texts())
    }
}
