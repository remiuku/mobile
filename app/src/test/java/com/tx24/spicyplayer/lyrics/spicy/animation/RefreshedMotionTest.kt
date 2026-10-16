package com.tx24.spicyplayer.lyrics.spicy.animation

import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.lyrics.spicy.models.Line
import com.tx24.spicyplayer.lyrics.spicy.models.Word
import com.tx24.spicyplayer.lyrics.spicy.parser.LetterSynthesizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test

class RefreshedMotionTest {
    @Test fun curveRisesInSequenceAndSettlesBehindThePeak() {
        assertEquals(0f, RefreshedMotion.curve(-150.0, 4000, 0, 7).rise, 0f)
        val half = RefreshedMotion.curve(350.0, 4000, 0, 7)
        assertEquals(0.5f, half.rise, 1e-6f)
        assertEquals(0f, RefreshedMotion.curve(350.0, 4000, 2, 7).rise, 0f)
        val peak = RefreshedMotion.curve(850.0, 4000, 0, 7)
        assertEquals(1f, peak.lift, 1e-6f)
        assertEquals(1f, peak.scale, 1e-6f)
        val settled = RefreshedMotion.curve(4000.0, 4000, 0, 7)
        assertEquals(0.780027f, settled.lift, 1e-5f)
        assertEquals(0.400074f, settled.scale, 1e-5f)
        // Short words use the 600ms minimum rise; long words use the 1000ms maximum.
        assertEquals(0.5f, RefreshedMotion.curve(150.0, 1050, 0, 4).rise, 1e-6f)
    }

    @Test fun releaseHoldsOvershootsAndEndsExactly() {
        assertEquals(0f, RefreshedMotion.release(100.0), 0f)
        assertTrue(RefreshedMotion.release(900.0) > 1f)
        assertEquals(1f, RefreshedMotion.release(1600.0), 0f)
        assertEquals(1f, RefreshedMotion.release(10000.0), 0f)
    }

    @Test fun flameHasAHeadSustainAndSharedFade() {
        val starts = listOf(0L, 1000L, 2000L, 3000L)
        val ends = listOf(1000L, 2000L, 3000L, 4000L)
        val flame = RefreshedMotion.flame(2500.0, starts, ends)
        assertEquals(2.5, flame.head, 0.0)
        assertEquals(1f, RefreshedMotion.glow(flame, 2), 0f)
        assertEquals(0.6f, RefreshedMotion.glow(flame, 0), 1e-5f)
        assertTrue(RefreshedMotion.glow(flame, 3) < 0.3f)
        assertEquals(0.185f, RefreshedMotion.glowRadius(1f), 1e-6f)
        assertEquals(0.1f, RefreshedMotion.glowRadius(0f), 0f)
        assertEquals(0.5, RefreshedMotion.flame(4350.0, starts, ends).heat, 1e-6)
        assertEquals(0.0, RefreshedMotion.flame(4700.0, starts, ends).heat, 0.0)
        assertEquals(0.75, RefreshedMotion.flame(400.0, starts, ends).heat, 0.0)
    }

    @Test fun durationStrengthAndLastWordBoost() {
        assertEquals(0.92f, RefreshedMotion.waveStrength(1000, false).motion, 0f)
        assertEquals(0.5f, RefreshedMotion.waveStrength(1000, false).glow, 0f)
        assertEquals(0.96f, RefreshedMotion.waveStrength(2000, false).motion, 1e-6f)
        assertEquals(0.75f, RefreshedMotion.waveStrength(2250, false).glow, 1e-6f)
        assertEquals(1.25f, RefreshedMotion.waveStrength(3500, true).glow, 0f)
        assertEquals(1.25f, RefreshedMotion.waveStrength(3500, true).motion, 0f)
    }

    @Test fun dotsFillInOrderAndBreatheWholeCyclesWithoutBounce() {
        assertEquals(0f, RefreshedMotion.dotAlpha(0.0, 9550, 0), 0f)
        assertEquals(0.57f, RefreshedMotion.dotAlpha(1500.0, 9550, 0), 1e-6f)
        assertEquals(0.22f, RefreshedMotion.dotAlpha(1500.0, 9550, 1), 1e-6f)
        assertEquals(0.92f, RefreshedMotion.dotAlpha(9000.0, 9550, 2), 1e-6f)
        assertEquals(0f, RefreshedMotion.dotAlpha(120.0, 9550, 1), 0f)
        assertEquals(1f, RefreshedMotion.dotBreath(0.0, 8900), 0f)
        assertEquals(1.16f, RefreshedMotion.dotBreath(2100.0, 8900), 1e-6f)
        assertEquals(1f, RefreshedMotion.dotBreath(8400.0, 8900), 1e-6f)
        assertEquals(1f, RefreshedMotion.dotBreath(1000.0, 3000), 0f)
    }

    @Test fun sevenGraphemeLimitOnlyAppliesToRefreshedSimple() {
        fun split(text: String, config: RenderConfig): Word = LetterSynthesizer.apply(
            listOf(Line(listOf(Word(text, 0, 2000)), 0, 2000)), config, false).single().words.single()
        val refreshed = RenderConfig.SIMPLE.copy(refreshedSimple = true)
        assertTrue(split("forever", refreshed).isLetterGroup)
        assertFalse(split("sunshine", refreshed).isLetterGroup)
        assertTrue(split("e\u0301".repeat(7), refreshed).isLetterGroup)
        assertFalse(split("e\u0301".repeat(8), refreshed).isLetterGroup)
        assertTrue(split("sunshine", RenderConfig.SIMPLE).isLetterGroup)
        assertTrue(split("sunshine", RenderConfig.FULL.copy(refreshedSimple = true)).isLetterGroup)
        val word = split("forever", refreshed)
        assertEquals(21L, word.startMs)
        assertEquals(2040L, word.endMs)
        assertEquals(1f, RefreshedMotion.release(1600.0), 0f)
    }

    @Test fun wordLiftIsCriticallyDampedAndIndependentOfFrameHistory() {
        assertEquals(0.01f, RefreshedMotion.wordOffset(0.0, 0, 2000), 0f)
        val samples = (0..80).map { RefreshedMotion.wordOffset(it * 100.0, 0, 2000) }
        assertTrue(samples.zipWithNext().all { (a, b) -> a >= b - 1e-6f })
        assertEquals(-0.033f, samples.last(), 1e-6f)
        assertEquals(samples[10], RefreshedMotion.wordOffset(1000.0, 0, 2000), 0f)
    }
    @Test fun animatorKeepsReleaseTailAndReturnsToRestAfterSeekingBack() {
        val config = RenderConfig(true, false, refreshedSimple = true)
        val lines = LetterSynthesizer.apply(listOf(Line(listOf(Word("love", 1000, 3000)), 1000, 3000)), config, false)
        fun animator() = LyricsAnimator(CoroutineScope(Job().apply { cancel() }), config)
        val anim = animator()
        fun word(time: Long) = anim.animate(lines, time, 0.016f).single().wordStates.single()
        val before = word(0)
        assertTrue(before.letterStates.all { it.scale == 1f && it.yOffset == 0f && it.glow == 0f })
        val active = word(2500)
        assertTrue(active.letterStates.any { it.scale > 1f && it.glow > 0f })
        val tail = word(3200)
        assertEquals(ElementState.Sung, tail.state)
        assertTrue(tail.letterStates.any { it.scale > 1f && it.glow > 0f })
        val done = word(5000)
        assertTrue(done.letterStates.all { it.scale == 1f && it.yOffset == 0f && it.glow == 0f })
        assertEquals(before, word(0))
        assertEquals(active, word(2500))
        assertEquals(active, animator().animate(lines, 2500, 0.016f).single().wordStates.single())
    }

}
