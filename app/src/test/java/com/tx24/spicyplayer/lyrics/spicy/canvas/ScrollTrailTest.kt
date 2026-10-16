package com.tx24.spicyplayer.lyrics.spicy.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ScrollTrailTest {
    @Test
    fun `four lead lines follow the same scroll at the same timestamp in either direction`() {
        val trail = ScrollTrail()
        trail.reference = 5
        trail.record(0L, 0f)
        trail.record(170_000_000L, -39f)
        trail.record(350_000_000L, -70f)
        for (line in 4..9) {
            assertEquals(0L, trail.delayNanos(line))
            assertEquals(-70f, trail.at(350_000_000L - trail.delayNanos(line)), 0f)
        }
        assertFalse(trail.catchingUp(350_000_000L))
        trail.record(500_000_000L, 0f)
        for (line in 1..6) assertEquals(0L, trail.delayNanos(line))
    }
}
