package com.tx24.spicyplayer.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WidgetFitTest {
    private val settings = WidgetSettings()

    @Test
    fun `a tall widget keeps everything`() {
        val fit = WidgetFit.of(settings, heightDp = 300)
        assertEquals(settings, fit.settings)
        assertEquals(3, fit.currentLines)
    }

    @Test
    fun `an unknown height changes nothing`() {
        assertEquals(WidgetFit(settings, 3), WidgetFit.of(settings, heightDp = null))
    }

    @Test
    fun `a tiny widget drops chrome and lets the painter fit the lyrics`() {
        val fit = WidgetFit.of(settings, heightDp = 60)
        assertEquals(settings.around, fit.settings.around)
        assertEquals(WidgetSettings.SongInfo.HIDDEN, fit.settings.songInfo)
        assertFalse(fit.settings.progress)
        assertEquals(settings.romanization, fit.settings.romanization)
        assertEquals(1, fit.currentLines)
    }

    @Test
    fun `a normal short widget keeps timeline and surrounding lines`() {
        val fit = WidgetFit.of(settings.copy(around = 2), heightDp = 200)
        assertEquals(WidgetSettings.SongInfo.BOTTOM, fit.settings.songInfo)
        assertEquals(2, fit.settings.around)
        assertEquals(true, fit.settings.progress)
    }

    @Test
    fun `changing the number of surrounding lines never hides the timeline`() {
        for (height in listOf(160, 180, 200, 240, 300)) {
            for (around in 0..2) {
                assertEquals(true, WidgetFit.of(settings.copy(around = around), height).settings.progress)
            }
        }
    }
}
