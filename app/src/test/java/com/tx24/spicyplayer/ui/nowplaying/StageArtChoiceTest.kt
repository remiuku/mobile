package com.tx24.spicyplayer.ui.nowplaying

import com.tx24.spicyplayer.ui.nowplaying.StageArtChoice.pick
import com.tx24.spicyplayer.ui.nowplaying.StageArtChoice.wantsAnimated
import com.tx24.spicyplayer.ui.nowplaying.StageArtChoice.wantsCanvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StageArtChoiceTest {
    private val canvas = "https://canvaz.scdn.co/a.mp4"
    private val motion = "https://mvod.itunes.apple.com/a.m3u8"
    private val found = CanvasLookup.Found(canvas)

    @Test
    fun `canvas first, then the animated cover, then the still cover`() {
        assertEquals(StagePicture.Canvas(canvas), pick(StageArt.Canvas, true, false, found, motion))
        assertEquals(StagePicture.Animated(motion), pick(StageArt.Canvas, true, false, CanvasLookup.None, motion))
        assertEquals(StagePicture.Still, pick(StageArt.Canvas, true, false, CanvasLookup.None, null))
    }

    @Test
    fun `the still cover waits while the canvas is looked up`() {
        assertEquals(StagePicture.Still, pick(StageArt.Canvas, true, false, CanvasLookup.Pending, motion))
    }

    @Test
    fun `animated cover is today's stage`() {
        assertEquals(StagePicture.Animated(motion), pick(StageArt.AnimatedCover, true, false, found, motion))
        assertEquals(StagePicture.Animated(motion), pick(StageArt.AnimatedCover, true, false, CanvasLookup.Pending, motion))
        assertEquals(StagePicture.Still, pick(StageArt.AnimatedCover, true, false, CanvasLookup.None, null))
    }

    @Test
    fun `cover art only ignores both videos`() {
        assertEquals(StagePicture.Still, pick(StageArt.CoverArt, true, false, found, motion))
        assertEquals(StagePicture.Still, pick(StageArt.CoverArt, true, false, CanvasLookup.Pending, motion))
    }

    @Test
    fun `the animated cover needs Animated Cover on, the canvas doesn't`() {
        assertEquals(StagePicture.Canvas(canvas), pick(StageArt.Canvas, false, false, found, motion))
        assertEquals(StagePicture.Still, pick(StageArt.Canvas, false, false, CanvasLookup.None, motion))
        assertEquals(StagePicture.Still, pick(StageArt.AnimatedCover, false, false, found, motion))
    }

    @Test
    fun `low performance mode keeps both videos off`() {
        for (art in StageArt.entries) {
            for (lookup in listOf(found, CanvasLookup.None, CanvasLookup.Pending)) {
                assertEquals("$art $lookup", StagePicture.Still, pick(art, true, true, lookup, motion))
            }
            assertFalse(wantsCanvas(art, lowPerformance = true))
            assertFalse(wantsAnimated(art, animatedCover = true, lowPerformance = true))
        }
    }

    @Test
    fun `lookups run only for the choices that reach them`() {
        assertTrue(wantsCanvas(StageArt.Canvas, false))
        assertFalse(wantsCanvas(StageArt.AnimatedCover, false))
        assertFalse(wantsCanvas(StageArt.CoverArt, false))
        assertTrue(wantsAnimated(StageArt.Canvas, true, false))
        assertTrue(wantsAnimated(StageArt.AnimatedCover, true, false))
        assertFalse(wantsAnimated(StageArt.CoverArt, true, false))
        assertFalse(wantsAnimated(StageArt.AnimatedCover, false, false))
    }

    @Test
    fun `a video covers its box at its own shape`() {
        // A portrait canvas on a wide stage: as wide as the stage, taller than it.
        assertEquals(800 to 1423, coverSize(800, 900, 9f / 16f))
        // On a narrow, tall one: as tall as the stage.
        assertEquals(563 to 1000, coverSize(300, 1000, 9f / 16f))
        // A square loop: a square as big as the longer side.
        assertEquals(900 to 900, coverSize(600, 900, 1f))
        assertEquals(1200 to 1200, coverSize(1200, 700, 1f))
    }
}
