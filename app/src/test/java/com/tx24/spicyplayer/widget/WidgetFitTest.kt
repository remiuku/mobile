package com.tx24.spicyplayer.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WidgetFitTest {
    private val settings = WidgetSettings()

    @Test
    fun `a tall widget keeps everything`() {
        val fit = WidgetFit.of(settings, heightDp = 300, notes = true)
        assertEquals(settings, fit.settings)
        assertEquals(2, fit.currentLines)
    }

    @Test
    fun `an unknown height changes nothing`() {
        assertEquals(WidgetFit(settings, 3), WidgetFit.of(settings, heightDp = null, notes = true))
    }

    @Test
    fun `a short widget keeps only the current line on one line`() {
        val fit = WidgetFit.of(settings, heightDp = 90, notes = true)
        assertEquals(0, fit.settings.around)
        assertEquals(WidgetSettings.SongInfo.HIDDEN, fit.settings.songInfo)
        assertFalse(fit.settings.progress)
        assertFalse(fit.settings.romanization)
        assertEquals(1, fit.currentLines)
    }

    @Test
    fun `the lines around go before the song row`() {
        val fit = WidgetFit.of(settings.copy(around = 2), heightDp = 200, notes = false)
        assertEquals(WidgetSettings.SongInfo.BOTTOM, fit.settings.songInfo)
        assertEquals(true, fit.settings.around < 2)
    }
}
