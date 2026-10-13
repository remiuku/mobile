package com.tx24.spicyplayer.lyrics.spicy.canvas

import com.tx24.spicyplayer.lyrics.spicy.models.Line
import com.tx24.spicyplayer.lyrics.spicy.models.LineRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrollPolicyControllerTest {
    private fun lead(start: Long, end: Long) = Line(emptyList(), start, end)
    private fun bg(start: Long, end: Long) = Line(emptyList(), start, end, role = LineRole.BACKGROUND)
    private fun interlude(start: Long, end: Long) = Line(emptyList(), start, end, role = LineRole.INTERLUDE)

    private fun target(lines: List<Line>, at: Long) = ScrollPolicyController.selectTargetIndex(lines, at)

    @Test fun overlappingLinesKeepTheTopOneWhileItEndsBeforeTheLineTwoDown() {
        // Line 0 runs into line 1 but ends before line 2 starts: the view stays on line 0.
        val lines = listOf(lead(0, 1_500), lead(1_000, 2_500), lead(2_000, 3_000))
        assertEquals(0, target(lines, 1_200))
    }

    @Test fun overlappingLinesMoveOnWhenTheTopOneOutlastsTheLookahead() {
        // Line 0 is still going when line 2 starts; lines 0 and 1 are adjacent, so the first.
        val lines = listOf(lead(0, 2_500), lead(1_000, 2_600), lead(2_000, 3_000), lead(4_000, 5_000))
        assertEquals(0, target(lines, 1_200))
        // Three active at once with a gap between first and last: the last one.
        assertEquals(2, target(lines, 2_200))
    }

    @Test fun aBackgroundTailUnderALaterLineIsIgnored() {
        // Line 0's background vocal outlives it into line 2: the view must not go back up.
        val lines = listOf(lead(0, 1_000), bg(500, 3_000), lead(1_000, 2_000), lead(2_000, 3_000))
        assertEquals(3, target(lines, 2_500))
    }

    @Test fun aBackgroundTailAloneInAGapDoesNotMoveTheView() {
        // Line 1's background vocal outlives it, past line 0, into the gap before line 3: the
        // view stays where it was instead of stopping at line 1's finished group on the way.
        val lines = listOf(lead(0, 2_000), lead(500, 1_500), bg(1_000, 2_400), lead(2_500, 3_500))
        assertNull(target(lines, 2_200))
        assertEquals(3, target(lines, 2_600))
        val policy = ScrollPolicyController()
        assertEquals(0, policy.decide(lines, 1_800).targetIndex)
        assertEquals(ScrollDecision(0, ScrollMotion.NONE), policy.decide(lines, 2_200))
    }

    @Test fun aLineSungAfterTheOneBelowItHoldsTheView() {
        // In order sung: line 0, line 1's background vocal, line 3, then line 1 itself.
        val lines = listOf(lead(0, 1_000), lead(3_000, 4_000), bg(1_000, 2_500), lead(2_000, 3_000), lead(4_000, 5_000))
        assertEquals(1, target(lines, 1_500))
        // Line 3 sings, but line 1 above it is still to come: the view waits on line 1.
        assertEquals(1, target(lines, 2_200))
        assertEquals(1, target(lines, 2_700))
        assertEquals(1, target(lines, 3_500))
        assertEquals(4, target(lines, 4_500))
    }

    @Test fun aBackgroundLineLeadingIntoItsOwnLineCounts() {
        val lines = listOf(lead(0, 1_000), lead(2_000, 3_000), bg(1_500, 2_500))
        // The background vocal (belonging to line 1) starts first: it pulls toward line 1.
        assertEquals(1, target(lines, 1_600))
    }

    @Test fun nothingActiveMeansNoNewTarget() {
        assertNull(target(listOf(lead(0, 1_000), lead(2_000, 3_000)), 1_500))
    }

    @Test fun betweenLinesTheViewStaysOnTheLastLine() {
        val policy = ScrollPolicyController()
        val lines = listOf(lead(0, 1_000), lead(2_000, 3_000))
        policy.decide(lines, 500)
        assertEquals(0, policy.decide(lines, 1_200).targetIndex)
    }

    @Test fun aSeekIntoAGapLandsOnTheLastLineThatStarted() {
        val policy = ScrollPolicyController()
        val lines = listOf(lead(0, 1_000), lead(2_000, 3_000), lead(5_000, 6_000))
        policy.decide(lines, 500)
        val decision = policy.decide(lines, 4_000)
        assertEquals(1, decision.targetIndex)
        assertEquals(ScrollMotion.SNAP, decision.motion)
    }

    @Test fun closingInterludeDotsHandOverToTheNextLine() {
        val lines = listOf(lead(0, 1_000), interlude(1_000, 6_000), lead(6_000, 7_000))
        assertEquals(1, target(lines, 3_000))
        assertEquals(2, target(lines, 5_700))
    }

    @Test fun earlyScrollPicksTheNextLineAheadOfTime() {
        val lines = listOf(lead(0, 1_000), lead(1_000, 2_000))
        val policy = ScrollPolicyController()
        policy.decide(lines, 500, leadMs = 250)
        assertEquals(0, policy.decide(lines, 700, leadMs = 250).targetIndex)
        val early = policy.decide(lines, 800, leadMs = 250)
        assertEquals(1, early.targetIndex)
        assertEquals(ScrollMotion.SMOOTH, early.motion)
    }

    @Test fun earlyScrollDoesNotMakeASeekOutOfTheLead() {
        val lines = listOf(lead(0, 1_000), lead(1_000, 2_000))
        val policy = ScrollPolicyController()
        policy.decide(lines, 500)
        // Turning the lead on mid-song moves the scroll time by 800ms, under the 1s seek threshold.
        assertEquals(ScrollMotion.SMOOTH, policy.decide(lines, 516, leadMs = 800).motion)
    }
}
