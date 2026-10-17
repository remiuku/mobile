package com.tx24.spicyplayer.ui.nowplaying

/**
 * What fills the cover stage. Each choice falls back down the list when the song doesn't have it:
 * a Spotify Canvas, then the record's animated cover, then the still cover art.
 */
enum class StageArt(val label: String) {
    Canvas("Spotify Canvas"),
    AnimatedCover("Animated cover"),
    CoverArt("Cover art only"),
}

/** Where the song's canvas lookup stands. */
sealed interface CanvasLookup {
    /** Not answered yet for this song. */
    data object Pending : CanvasLookup
    /** The song has no canvas, or none could be found. */
    data object None : CanvasLookup
    data class Found(val url: String) : CanvasLookup
}

/** What the cover stage shows. The still cover is always beneath, so [Still] adds nothing. */
sealed interface StagePicture {
    data class Canvas(val url: String) : StagePicture
    data class Animated(val url: String) : StagePicture
    data object Still : StagePicture
}

/** Picks the cover stage's picture from the setting and what the song has. */
object StageArtChoice {
    /** Whether the song's canvas should be looked up. Off in low performance mode, like the stage. */
    fun wantsCanvas(art: StageArt, lowPerformance: Boolean): Boolean =
        art == StageArt.Canvas && !lowPerformance

    /**
     * Whether the record's animated cover should be looked up for the stage: the choice reaches
     * it, and Animated Cover (Now Playing) is on, as the stage shares the header's loop.
     */
    fun wantsAnimated(art: StageArt, animatedCover: Boolean, lowPerformance: Boolean): Boolean =
        art != StageArt.CoverArt && animatedCover && !lowPerformance

    /**
     * The stage's picture. While a wanted canvas is still being looked up, the still cover stays
     * rather than the animated cover showing first and giving way to the canvas a moment later.
     * [motionUrl] is the animated cover's stream when there is one.
     */
    fun pick(
        art: StageArt,
        animatedCover: Boolean,
        lowPerformance: Boolean,
        canvas: CanvasLookup,
        motionUrl: String?,
    ): StagePicture {
        if (wantsCanvas(art, lowPerformance)) {
            when (canvas) {
                is CanvasLookup.Found -> return StagePicture.Canvas(canvas.url)
                CanvasLookup.Pending -> return StagePicture.Still
                CanvasLookup.None -> Unit
            }
        }
        if (motionUrl != null && wantsAnimated(art, animatedCover, lowPerformance)) return StagePicture.Animated(motionUrl)
        return StagePicture.Still
    }
}
