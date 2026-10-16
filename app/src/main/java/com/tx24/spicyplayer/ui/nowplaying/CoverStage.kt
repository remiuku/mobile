package com.tx24.spicyplayer.ui.nowplaying

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The cover stage: in landscape, beside the lyrics, the cover (its animated loop when [motionUrl]
 * has one, the still cover otherwise) fills the panel's side of the screen edge to edge, fading
 * into the page toward the lyrics and over its top. It sits behind the lyrics, translucent so the
 * background carries through it, dimmed by a veil that deepens while the controls show
 * ([controlsShown]), and darkened along the bottom for the [dock], which goes in a box as wide as
 * the cover at the page's outer edge.
 *
 * [shown] (0..1) fades the whole stage; [swapAlpha] and [swapX] carry it through a side swap.
 * [onRight] says which side it is on. [startInsetPx]/[endInsetPx] are the cutout's insets: the
 * picture runs under them, the dock keeps clear of them.
 */
@Composable
fun CoverStage(
    info: NowPlayingInfo,
    metrics: LandscapeMetrics,
    onRight: Boolean,
    startInsetPx: Float,
    endInsetPx: Float,
    motionUrl: String?,
    shown: () -> Float,
    controlsShown: () -> Float,
    swapAlpha: () -> Float,
    swapX: () -> Float,
    modifier: Modifier = Modifier,
    dock: @Composable BoxScope.() -> Unit,
) {
    val artwork = rememberSessionArtwork(info.artwork, info.artworkUri, maxDimension = STAGE_ARTWORK_MAX_PX, hasSession = info.hasSession)
    // The last cover stays up while the player has none to show.
    var resting by remember { mutableStateOf<SessionArtwork?>(null) }
    LaunchedEffect(artwork?.fingerprint) { if (artwork != null) resting = artwork }
    var failedMotionUrl by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val motionQuery = MotionCoverQuery(info.artists, info.album, info.title)
    val outerInset = if (onRight) endInsetPx else startInsetPx
    val widthPx = outerInset + metrics.stageWidthPx
    val featherPx = metrics.stageFeatherPx
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = shown() * swapAlpha()
                translationX = swapX()
            },
    ) {
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val width = widthPx.roundToInt().coerceAtMost(constraints.maxWidth)
                    val placeable = measurable.measure(Constraints.fixed(width, constraints.maxHeight))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(if (onRight) constraints.maxWidth - width else 0, 0)
                    }
                }
                // Feathered toward the lyrics and over the top, on eased stops: a straight ramp
                // leaves a visible line where it reaches full strength.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val inner = if (onRight) 0f else size.width
                    val toward = if (onRight) 1f else -1f
                    val stops = FEATHER_STOPS.map { (at, a) ->
                        ((inner + toward * at * featherPx) / size.width).coerceIn(0f, 1f) to Color.Black.copy(alpha = a)
                    }.sortedBy { it.first }
                    drawRect(Brush.horizontalGradient(*stops.toTypedArray()), blendMode = BlendMode.DstIn)
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            TOP_FEATHER * 0.5f to Color.Black.copy(alpha = 0.28f),
                            TOP_FEATHER to Color.Black,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            Box(Modifier.fillMaxSize().clipToBounds().graphicsLayer { alpha = STAGE_OPACITY }) {
                Crossfade(resting, animationSpec = tween(STAGE_FADE_MS), label = "stageCover") { cover ->
                    if (cover != null) {
                        val bitmap = remember(cover) { cover.bitmap.asImageBitmap() }
                        Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = CropAlignment)
                    }
                }
                if (motionUrl != null && motionUrl != failedMotionUrl) {
                    MotionCoverVideo(
                        url = motionUrl,
                        visible = true,
                        onFailed = {
                            failedMotionUrl = motionUrl
                            scope.launch { forgetMotionCover(context, motionQuery) }
                        },
                        // The loop is square: a square as big as the stage's longer side, cropped
                        // like the still cover.
                        modifier = Modifier.layout { measurable, constraints ->
                            val side = max(constraints.maxWidth, constraints.maxHeight)
                            val placeable = measurable.measure(Constraints.fixed(side, side))
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                val x = (constraints.maxWidth - side) / 2
                                val y = ((constraints.maxHeight - side) * CROP_FROM_TOP).roundToInt()
                                placeable.place(x, y)
                            }
                        },
                    )
                }
                // A black veil at 1 - b is the picture at brightness b, without a filter pass per frame.
                Box(
                    Modifier.fillMaxSize().drawBehind {
                        val brightness = 1f - (1f - CONTROLS_BRIGHTNESS) * controlsShown()
                        drawRect(Color.Black.copy(alpha = 1f - 0.8f * (0.45f + 0.55f * brightness)))
                    },
                )
            }
            // Darkens under the dock so its white text reads over bright frames, eased in over the
            // lower half: a straight ramp over a short span showed as a dark band.
            Box(
                Modifier.fillMaxSize().drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.35f to Color.Transparent,
                            0.50f to Color.Black.copy(alpha = 0.06f),
                            0.62f to Color.Black.copy(alpha = 0.16f),
                            0.75f to Color.Black.copy(alpha = 0.29f),
                            0.88f to Color.Black.copy(alpha = 0.39f),
                            1f to Color.Black.copy(alpha = 0.45f),
                        ),
                    )
                },
            )
        }
        Box(
            Modifier.layout { measurable, constraints ->
                val width = metrics.coverSizePx.roundToInt().coerceAtMost(constraints.maxWidth)
                val placeable = measurable.measure(Constraints.fixed(width, constraints.maxHeight))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val x = if (onRight) {
                        constraints.maxWidth - endInsetPx - metrics.dockInsetPx - width
                    } else {
                        startInsetPx + metrics.dockInsetPx
                    }
                    placeable.place(x.roundToInt(), 0)
                }
            },
            content = dock,
        )
    }
}

/**
 * The song for the stage's dock: name over artists, caption-sized, on the side the stage is on;
 * long lines scroll.
 */
@Composable
fun StageMetadata(info: NowPlayingInfo, metrics: LandscapeMetrics, onRight: Boolean, widthPx: Float) {
    val title = metrics.stageTitleSizeSp
    val artists = metrics.stageArtistsSizeSp
    HeaderMetadata(
        title = info.title,
        artists = info.artists,
        year = null,
        titleStyle = headerTextStyle(FontWeight.Bold, title, title * 1.2f),
        artistsStyle = headerTextStyle(FontWeight.Normal, artists, artists * 1.35f),
        titleWidthPx = widthPx,
        artistsWidthPx = widthPx,
        centered = false,
        end = onRight,
        modifier = Modifier,
    )
}

/** `object-position: 50% 40%`: faces sit in the upper half of most covers, so a centred crop cuts them. */
private const val CROP_FROM_TOP = 0.4f
private val CropAlignment = BiasAlignment(0f, CROP_FROM_TOP * 2f - 1f)

/** The stage's own opacity, so the background reads through it as one surface. */
private const val STAGE_OPACITY = 0.75f

/** How far the picture dims while the controls show. */
private const val CONTROLS_BRIGHTNESS = 0.55f

/** The top fades in over this much of the height. */
private const val TOP_FEATHER = 0.18f

/** The feather toward the lyrics: (distance from the inner edge as a share of the feather, opacity). */
private val FEATHER_STOPS = listOf(0f to 0f, 0.18f to 0.10f, 0.38f to 0.36f, 0.72f to 0.85f, 1f to 1f)

const val STAGE_FADE_MS = 900
private const val STAGE_ARTWORK_MAX_PX = 1024
