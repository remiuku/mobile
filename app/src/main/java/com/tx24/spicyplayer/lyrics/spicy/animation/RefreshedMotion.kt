package com.tx24.spicyplayer.lyrics.spicy.animation

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Seek-safe envelopes. Times are milliseconds, lengths are lyric font sizes (em). */
object RefreshedMotion {
    const val SCALE = 0.055f
    const val LIFT = 0.0738f
    const val ALPHA = 0.8f
    const val GAP_EM = 0.62f
    const val WORD_EDGE_EM = 0.55f
    const val LETTER_EDGE_EM = 0.4f

    private fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
    fun smoothstep(x: Double): Double = clamp(x).let { it * it * (3 - 2 * it) }
    private fun ease(x: Double) = 0.5 - 0.5 * cos(PI * clamp(x))

    data class Strength(val motion: Float, val glow: Float)
    fun waveStrength(duration: Long, lastWord: Boolean): Strength {
        val boost = if (lastWord) 1.25 else 1.0
        return Strength(((0.92 + 0.08 * smoothstep((duration - 1000) / 2000.0)) * boost).toFloat(),
            ((0.5 + 0.5 * smoothstep((duration - 1000) / 2500.0)) * boost).toFloat())
    }

    data class Curve(val rise: Float, val lift: Float, val scale: Float)
    fun curve(elapsed: Double, duration: Long, index: Int, count: Int): Curve {
        if (count <= 0) return Curve(0f, 0f, 0f)
        val spacing = duration.coerceAtLeast(1) * 0.5 / count
        val riseMs = (duration * 0.27).coerceIn(600.0, 1000.0)
        val at = elapsed + 150
        val x = clamp((at - index * spacing) / riseMs)
        val up = 0.5 * x + 0.5 * ease(x)
        val peak = minOf((at - riseMs) / spacing, count - 1.0)
        val behind = maxOf(0.0, peak - index) / 2
        val near = exp(-behind * behind)
        return Curve(up.toFloat(), (up * (0.78 + 0.22 * near)).toFloat(),
            (up * (0.4 + 0.6 * near)).toFloat())
    }

    /** Underdamped return, with a short hold and an exact resting endpoint. */
    fun release(sinceEnd: Double): Float {
        val t = sinceEnd - 100
        if (t <= 0) return 0f
        if (t >= 1500) return 1f
        val w = 2 * PI * 0.9
        val z = 0.7
        val wd = w * sqrt(1 - z * z)
        val s = t / 1000
        return (1 - exp(-z * w * s) * (cos(wd * s) + z * w / wd * sin(wd * s))).toFloat()
    }

    data class Flame(val head: Double, val heat: Double)
    /** The head follows actual letter timing, without the curve's lead. */
    fun flame(t: Double, starts: List<Long>, ends: List<Long>): Flame {
        if (starts.isEmpty() || t <= starts.first()) return Flame(0.0, 0.0)
        if (t >= ends.last()) return Flame(starts.size.toDouble(), 1 - ease((t - ends.last()) / 700))
        val index = ends.indexOfFirst { t < it }.coerceAtLeast(0)
        val head = index + maxOf(0.0, t - starts[index]) / (ends[index] - starts[index]).coerceAtLeast(1)
        val ignite = clamp((t - starts.first()) / 800)
        return Flame(head, 1 - (1 - ignite) * (1 - ignite))
    }

    fun glow(flame: Flame, index: Int): Float {
        val d = index + 0.5 - flame.head
        val value = if (d > 0) exp(-((d / 0.82) * (d / 0.82)))
            else 0.6 + 0.4 * exp(-((d / 0.5) * (d / 0.5)))
        return (flame.heat * value).toFloat()
    }
    fun glowRadius(glow: Float): Float = 0.1f + (0.185f - 0.1f) * glow.coerceAtMost(1f)

    /** Critically damped response to the Simple word's linear lift target (1.45 Hz). */
    fun wordOffset(t: Double, start: Long, end: Long): Float {
        val elapsed = (t - start) / 1000
        val duration = (end - start).coerceAtLeast(1) / 1000.0
        val w = 2 * PI * 1.45
        fun ramp(s: Double): Double = if (s <= 0) 0.0 else s - 2 / w + (s + 2 / w) * exp(-w * s)
        return (0.01 - 0.043 * (ramp(elapsed) - ramp(elapsed - duration)) / duration).toFloat()
    }

    /** The dot's own third fills smoothly; its staggered entrance fades in from zero. */
    fun dotAlpha(elapsed: Double, duration: Long, index: Int): Float {
        val span = (duration - 550).coerceAtLeast(90) / 3.0
        val fill = ease((elapsed - index * span) / span)
        val enter = smoothstep((elapsed - index * 120) / 650)
        return ((0.22 + (0.92 - 0.22) * fill) * enter).toFloat()
    }
    fun dotBreath(elapsed: Double, duration: Long): Float {
        val room = duration - 500
        if (room < 2600) return 1f
        val cycles = kotlin.math.floor(room / 4200.0 + 0.5).coerceAtLeast(1.0)
        val phase = (elapsed / (room / cycles)).coerceIn(0.0, cycles)
        return (1 + 0.16 * (0.5 - 0.5 * cos(2 * PI * phase))).toFloat()
    }
}
