package com.tx24.spicyplayer.ui.controls

import androidx.compose.runtime.CompositionLocalProvider
import com.tx24.spicyplayer.ui.components.LocalGlyphPressed
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.Path
import androidx.compose.ui.graphics.vector.Group
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import com.tx24.spicyplayer.ui.theme.SpicyType
import com.tx24.spicyplayer.ui.theme.SpicyRadii
import com.tx24.spicyplayer.ui.components.SelectMenuPosition
import com.tx24.spicyplayer.ui.components.MENU_FILL
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import com.tx24.spicyplayer.ui.theme.SpicyPalette
import androidx.compose.material.icons.automirrored.rounded.QueueMusic

import com.tx24.spicyplayer.ui.components.LocalUiAnimations

import androidx.compose.animation.core.animateFloatAsState

import androidx.compose.animation.core.snap

import androidx.compose.animation.core.FastOutSlowInEasing

import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.Icons
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsLayoutCalculator
import com.tx24.spicyplayer.ui.components.GlassButton
import com.tx24.spicyplayer.ui.components.LocalBackdrop
import com.tx24.spicyplayer.ui.nowplaying.SessionCustomAction
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicyMotion
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.launch

/**
 * Transport state and actions for [LyricsControls].
 *
 * @param customActions the player's own buttons (shuffle, repeat, like, ...) as published in its
 * PlaybackState. Shuffle and repeat take the slots beside the skips; the rest become floating
 * buttons.
 */
class PlaybackControlsState(
    val isPlaying: Boolean,
    val canSeek: Boolean,
    val durationMs: Long,
    /** The player's position (not the lyric clock, which carries the output delay). */
    val positionMs: () -> Long,
    val onPlayPause: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onSeek: (Long) -> Unit,
    val customActions: List<SessionCustomAction> = emptyList(),
    val onCustomAction: (String) -> Unit = {},
    /** Re-reads the player's position, for when the lyrics have drifted. */
    val onResync: () -> Unit = {},
    val onTranslate: (() -> Unit)? = null,
    val translated: Boolean = false,
    /** A translation is on its way: the button pulses until it lands. */
    val translating: Boolean = false,
    /** Turns the screen to landscape, or back once [rotated]; null hides the button. */
    val onRotate: (() -> Unit)? = null,
    val rotated: Boolean = false,
)

/**
 * The lyrics screen's bottom controls, top to bottom: the timeline with its times underneath,
 * the playback row (shuffle, previous, play/pause, next, repeat) as plain glyphs, and the
 * floating glass buttons (romanize, the player's other actions, settings). Behind them, the lyrics
 * blur and darken towards the bottom, starting [SHADE_REACH] above the controls. While not
 * [interactive] (hidden), touches on the controls are swallowed.
 *
 * [shown] (0 hidden .. 1 shown) is their opacity, for the show/hide fade.
 */
@Composable
fun LyricsControls(
    controls: PlaybackControlsState,
    romanizeAvailable: Boolean,
    romanized: Boolean,
    onToggleRomanize: () -> Unit,
    onOpenSettings: () -> Unit,
    /** Shows the Lyrics Manager button when set. */
    onOpenLyricsManager: (() -> Unit)? = null,
    onOpenQueue: (() -> Unit)? = null,
    onOpenSource: (() -> Unit)? = null,
    /** Shows the quick settings button when set. */
    onOpenQuickSettings: (() -> Unit)? = null,
    /** Shows the picture-in-picture button when set. */
    onEnterPip: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onToggleExpanded: () -> Unit = {},
    interactive: Boolean = true,
    shown: () -> Float = { 1f },
    /** Height of the controls themselves, without the shade above them, in px. */
    onControlsHeight: (Int) -> Unit = {},
    /** How much of the shade shows (1 = all), on top of [shown]. */
    shade: () -> Float = { 1f },
    /** A short landscape screen: everything in one low bar ([ControlsBar]) instead of three rows. */
    wide: Boolean = false,
    /** The cutout's insets: the controls keep clear of them, the shade runs under them to the screen's edges. */
    insets: PaddingValues = PaddingValues(0.dp),
    showResync: Boolean = true,
    showExpand: Boolean = true,
) {
    val backdrop = LocalBackdrop.current
    Box(modifier.fillMaxWidth()) {
        // The shade: blur that grows from nothing at the top of this area to full strength at the
        // controls, plus a light darkening, so white controls read over anything.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = shown() * shade() }
                .then(
                    backdrop?.let {
                        Modifier.hazeEffect(it) {
                            backgroundColor = Color.Black
                            blurRadius = SHADE_BLUR
                            // Half-size: the shade spans the screen's width under moving lyrics.
                            inputScale = HazeInputScale.Auto
                            noiseFactor = 0f
                            tints = emptyList()
                            fallbackTint = HazeTint(Color.Black.copy(alpha = 0.25f))
                            progressive = HazeProgressive.verticalGradient(
                                easing = ShadeEasing,
                                startIntensity = 0f,
                                endIntensity = 1f,
                            )
                        }
                    } ?: Modifier,
                )
                .background(ShadeGradient),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = if (wide) BAR_SHADE_REACH else SHADE_REACH)
                .padding(insets)
                .graphicsLayer { alpha = shown() }
                .onSizeChanged { onControlsHeight(it.height) }
                // The controls own their whole area, gaps and margins included: without a pointer
                // handler here, a touch between two buttons falls through to the lyrics and seeks.
                .pointerInput(Unit) {
                    awaitPointerEventScope { while (true) awaitPointerEvent() }
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            if (wide) {
                ControlsBar(controls, romanizeAvailable, romanized, onToggleRomanize, onOpenSettings, onOpenLyricsManager, onOpenQuickSettings, expanded, onToggleExpanded, shown, showResync, showExpand, onOpenQueue = onOpenQueue, onOpenSource = onOpenSource, onEnterPip = onEnterPip)
            } else {
                ControlsColumn(controls, romanizeAvailable, romanized, onToggleRomanize, onOpenSettings, onOpenLyricsManager, onOpenQuickSettings, expanded, onToggleExpanded, shown, showResync, showExpand, onOpenQueue = onOpenQueue, onOpenSource = onOpenSource, onEnterPip = onEnterPip)
            }
            // Hidden controls swallow touches too: a touch there only brings them back, rather
            // than pressing a button nobody can see.
            if (!interactive) {
                Box(
                    Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown().consume()
                                do {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })
                            }
                        },
                )
            }
        }
    }
}

@Composable
private fun ControlsColumn(
    controls: PlaybackControlsState,
    romanizeAvailable: Boolean,
    romanized: Boolean,
    onToggleRomanize: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLyricsManager: (() -> Unit)?,
    onOpenQuickSettings: (() -> Unit)?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    shown: () -> Float,
    showResync: Boolean,
    showExpand: Boolean,
    onOpenQueue: (() -> Unit)? = null,
    onOpenSource: (() -> Unit)? = null,
    onEnterPip: (() -> Unit)? = null,
) {
    val shuffle = controls.customActions.firstOrNull { it.kind == ActionKind.Shuffle }
    val repeat = controls.customActions.firstOrNull { it.kind == ActionKind.Repeat }
    val others = controls.customActions.filter { it.kind == ActionKind.Other }

    Column(
        modifier = Modifier
            .widthIn(max = CONTROLS_MAX_WIDTH)
            .fillMaxWidth()
            .padding(bottom = BOTTOM_MARGIN),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Timeline(controls, shown, Modifier.padding(horizontal = SIDE_MARGIN))
        // The transport sits in its own air, the same gap above and below it (plus the glyphs'
        // touch padding), so the three rows read as separate.
        Spacer(Modifier.height(ROW_GAP))
        PlaybackRow(controls, shuffle, repeat, Modifier.padding(horizontal = SIDE_MARGIN - SpicySpacing.S2))
        Spacer(Modifier.height(ROW_GAP))
        // A player that adds enough buttons to run off the screen gets the rest in a More menu.
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = SpicySpacing.S2), contentAlignment = Alignment.Center) {
            FloatingButtons(
                floatingActions(controls, others, romanizeAvailable, romanized, onToggleRomanize, onOpenSettings, onOpenLyricsManager, onOpenQuickSettings, expanded, onToggleExpanded, onSwapSide = null, showResync = showResync, showExpand = showExpand, onOpenQueue = onOpenQueue, onOpenSource = onOpenSource, onEnterPip = onEnterPip),
                maxWidth, minSize = FLOATING_SIZE, maxSize = FLOATING_SIZE, minGap = SpicySpacing.S2, maxGap = SpicySpacing.S4,
            )
        }
    }
}

/**
 * The controls as one low bar for a short landscape screen, laid out like a desktop player bar:
 * the playback row over the timeline in the middle, the floating glass buttons on the right,
 * and the left side kept empty so the middle stays centred on the page.
 */
@Composable
private fun ControlsBar(
    controls: PlaybackControlsState,
    romanizeAvailable: Boolean,
    romanized: Boolean,
    onToggleRomanize: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLyricsManager: (() -> Unit)?,
    onOpenQuickSettings: (() -> Unit)?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    shown: () -> Float,
    showResync: Boolean,
    showExpand: Boolean,
    onOpenQueue: (() -> Unit)? = null,
    onOpenSource: (() -> Unit)? = null,
    onEnterPip: (() -> Unit)? = null,
) {
    val shuffle = controls.customActions.firstOrNull { it.kind == ActionKind.Shuffle }
    val repeat = controls.customActions.firstOrNull { it.kind == ActionKind.Repeat }
    val others = controls.customActions.filter { it.kind == ActionKind.Other }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = SIDE_MARGIN, end = SIDE_MARGIN, bottom = BAR_BOTTOM_MARGIN),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        Column(Modifier.width(BAR_MIDDLE_WIDTH), horizontalAlignment = Alignment.CenterHorizontally) {
            PlaybackRow(controls, shuffle, repeat, Modifier, skipWidth = BAR_SKIP_WIDTH)
            Timeline(controls, shown, Modifier)
        }
        BoxWithConstraints(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            FloatingButtons(
                floatingActions(controls, others, romanizeAvailable, romanized, onToggleRomanize, onOpenSettings, onOpenLyricsManager, onOpenQuickSettings, expanded, onToggleExpanded, onSwapSide = null, showResync = showResync, showExpand = showExpand, onOpenQueue = onOpenQueue, onOpenSource = onOpenSource, onEnterPip = onEnterPip),
                maxWidth - SpicySpacing.S4, minSize = COVER_BUTTON_MIN, maxSize = COVER_BUTTON_MAX, minGap = COVER_BUTTON_GAP, maxGap = COVER_BUTTON_GAP,
            )
        }
    }
}

/** One floating glass button. Those with the lowest [keep] go into the More menu first. */
private class FloatingAction(
    val label: String,
    val keep: Int,
    val onClick: () -> Unit,
    /** Draws the glyph; takes the icon's size as drawn for a 48dp button and scales it. */
    val icon: @Composable (scaled: (Float) -> Dp) -> Unit,
)

/** How many buttons a row holds ([slots], the More button among them), and how big and far apart. */
internal data class ButtonFit(val slots: Int, val size: Dp, val gap: Dp)

/**
 * Fits [count] buttons into [width]: as big as fits up to [maxSize]. When they don't all fit at
 * [minSize] and [minGap], the row keeps as many as do, the last of them being the More button.
 */
internal fun fitButtons(width: Dp, count: Int, minSize: Dp, maxSize: Dp, minGap: Dp, maxGap: Dp): ButtonFit {
    val all = (width - minGap * (count - 1)) / count
    val slots = if (all >= minSize) count else ((width + minGap) / (minSize + minGap)).toInt().coerceIn(2, count)
    val size = ((width - minGap * (slots - 1)) / slots).coerceIn(minSize, maxSize)
    val gap = if (slots > 1) ((width - size * slots) / (slots - 1)).coerceIn(minGap, maxGap) else 0.dp
    return ButtonFit(slots, size, gap)
}

/** The buttons in [width], with the ones that don't fit behind a More button at the row's end. */
@Composable
private fun FloatingButtons(actions: List<FloatingAction>, width: Dp, minSize: Dp, maxSize: Dp, minGap: Dp, maxGap: Dp) {
    val fit = fitButtons(width, actions.size, minSize, maxSize, minGap, maxGap)
    val kept = if (fit.slots >= actions.size) actions.toSet()
    else actions.sortedByDescending { it.keep }.take(fit.slots - 1).toSet()
    val overflow = actions.filter { it !in kept }
    fun icon(dp: Float) = fit.size * (dp / FLOATING_SIZE.value)
    Row(horizontalArrangement = Arrangement.spacedBy(fit.gap)) {
        actions.filter { it in kept }.forEach { action ->
            GlassButton(onClick = action.onClick, contentDescription = action.label, size = fit.size) { action.icon(::icon) }
        }
        if (overflow.isNotEmpty()) MoreButton(overflow, fit.size)
    }
}

/** A glass button that opens a menu of [actions], each with its glyph and name. */
@Composable
private fun MoreButton(actions: List<FloatingAction>, size: Dp) {
    // Drives the open and close animation; the popup stays up until the close finishes.
    val menu = remember { MutableTransitionState(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        GlassButton(onClick = { menu.targetState = !menu.targetState }, contentDescription = "More", size = size) {
            Image(rememberVectorPainter(Icons.Rounded.MoreHoriz), null, Modifier.size(size * (24f / FLOATING_SIZE.value)), colorFilter = ColorFilter.tint(Color.White))
        }
        if (menu.currentState || menu.targetState) {
            val margin = with(LocalDensity.current) { SpicySpacing.S2.roundToPx() }
            val position = remember(margin) { SelectMenuPosition(margin) }
            Popup(popupPositionProvider = position, onDismissRequest = { menu.targetState = false }, properties = PopupProperties(focusable = true)) {
                val origin = TransformOrigin(1f, if (position.above) 1f else 0f)
                AnimatedVisibility(
                    visibleState = menu,
                    enter = fadeIn(tween(SpicyMotion.FAST_MS)) +
                        scaleIn(tween(SpicyMotion.FAST_MS, easing = SpicyMotion.Modal), initialScale = 0.94f, transformOrigin = origin),
                    exit = fadeOut(tween(SpicyMotion.FAST_MS)) +
                        scaleOut(tween(SpicyMotion.FAST_MS), targetScale = 0.94f, transformOrigin = origin),
                ) {
                    val plate = RoundedCornerShape(SpicyRadii.Md)
                    Column(
                        Modifier
                            .widthIn(min = 180.dp, max = 280.dp)
                            .width(IntrinsicSize.Max)
                            .heightIn(max = 360.dp)
                            .clip(plate)
                            .background(MENU_FILL)
                            .border(1.dp, SpicyColors.HairlineStrong, plate)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = SpicySpacing.S1),
                    ) {
                        actions.forEach { action ->
                            MoreItem(action) {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                menu.targetState = false
                                action.onClick()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreItem(action: FloatingAction, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = SpicySpacing.S1)
            .clip(RoundedCornerShape(SpicyRadii.Sm))
            .background(if (pressed) SpicyColors.TintBgPressed else Color.Transparent)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = SpicySpacing.S3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S3),
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalGlyphPressed provides pressed) { action.icon { it.dp } }
        }
        Text(action.label, style = SpicyType.Body.copy(color = SpicyColors.TextPrimary))
    }
}

private fun floatingActions(
    controls: PlaybackControlsState,
    others: List<SessionCustomAction>,
    romanizeAvailable: Boolean,
    romanized: Boolean,
    onToggleRomanize: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLyricsManager: (() -> Unit)?,
    onOpenQuickSettings: (() -> Unit)?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    /** Shows the button that moves the landscape panel to the other side when set. */
    onSwapSide: (() -> Unit)?,
    /** Where the landscape cover is now, so the move button points the other way. */
    coverOnRight: Boolean = false,
    showResync: Boolean,
    showExpand: Boolean,
    onOpenQueue: (() -> Unit)? = null,
    onOpenSource: (() -> Unit)? = null,
    onEnterPip: (() -> Unit)? = null,
): List<FloatingAction> = buildList {
    // Material icons are black until tinted; the others here are drawn white.
    val white = ColorFilter.tint(Color.White)
    if (romanizeAvailable) {
        // Shows what a tap switches to: "A" to go back, the kana mark to romanize.
        add(FloatingAction(if (romanized) "Show original lyrics" else "Romanize lyrics", keep = 80, onToggleRomanize) { icon ->
            if (romanized) {
                Image(rememberVectorPainter(SpicyIcons.DisableRomanization), null, Modifier.size(icon(23f)))
            } else {
                Image(rememberVectorPainter(SpicyIcons.EnableRomanization), null, Modifier.size(icon(23f)))
            }
        })
    }
    // In groups: the lyric text (romanize, translate), the view, the app's tools, the player's own
    // buttons, then settings.
    controls.onTranslate?.let { toggle ->
        add(FloatingAction(if (controls.translated) "Turn translation off" else "Translate", keep = 78, toggle) { icon ->
            if (controls.translating) {
                // A spinning ring in the glyph's place until the translation lands.
                val turn by rememberInfiniteTransition(label = "translating").animateFloat(
                    0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "translatingTurn",
                )
                Canvas(Modifier.size(icon(22f))) {
                    drawArc(Color.White, turn, 270f, useCenter = false, style = Stroke(size.minDimension * 0.1f, cap = StrokeCap.Round))
                }
            } else {
                Image(rememberVectorPainter(Icons.Rounded.Translate), null, Modifier.size(icon(23f)), colorFilter = white)
            }
        })
    }
    if (showResync) add(FloatingAction("Resync lyrics", keep = 30, controls.onResync) { icon ->
        Image(rememberVectorPainter(SpicyIcons.Resync), null, Modifier.size(icon(23f)))
    })
    if (showExpand) add(FloatingAction(if (expanded) "Show lyrics" else "Show the cover", keep = 70, onToggleExpanded) { icon ->
        ExpandGlyph(expanded, Modifier.size(icon(23f)))
    })
    if (onSwapSide != null) add(FloatingAction("Move the cover to the other side", keep = 60, onSwapSide) { icon ->
        SwapSideGlyph(coverOnRight, Modifier.size(icon(23f)))
    })
    if (onOpenLyricsManager != null) add(FloatingAction("Lyrics Manager", keep = 50, onOpenLyricsManager) { icon ->
        Image(rememberVectorPainter(SpicyIcons.LyricsManager), null, Modifier.size(icon(23f)))
    })
    if (onOpenSource != null) add(FloatingAction("Lyrics source", keep = 45, onOpenSource) { icon ->
        Image(rememberVectorPainter(Icons.Rounded.Source), null, Modifier.size(icon(23f)), colorFilter = white)
    })
    if (onOpenQueue != null) add(FloatingAction("Queue", keep = 40, onOpenQueue) { icon ->
        Image(rememberVectorPainter(Icons.AutoMirrored.Rounded.QueueMusic), null, Modifier.size(icon(23f)), colorFilter = white)
    })
    controls.onRotate?.let { rotate ->
        add(FloatingAction(if (controls.rotated) "Back to portrait" else "Turn to landscape", keep = 25, rotate) { icon ->
            Image(rememberVectorPainter(Icons.Rounded.ScreenRotation), null, Modifier.size(icon(22f)), colorFilter = white)
        })
    }
    if (onEnterPip != null) add(FloatingAction("Picture-in-picture", keep = 20, onEnterPip) { icon ->
        Image(rememberVectorPainter(Icons.Rounded.PictureInPictureAlt), null, Modifier.size(icon(23f)), colorFilter = white)
    })
    if (onOpenQuickSettings != null) add(FloatingAction("Quick settings", keep = 90, onOpenQuickSettings) { icon ->
        Image(rememberVectorPainter(Icons.Rounded.Tune), null, Modifier.size(icon(24f)), colorFilter = white)
    })
    others.forEach { action ->
        add(FloatingAction(action.name, keep = 10, { controls.onCustomAction(action.action) }) { icon ->
            ActionIcon(action, Modifier.size(icon(23f)))
        })
    }
    add(FloatingAction("Settings", keep = Int.MAX_VALUE, onOpenSettings) { icon ->
        SettingsGlyph(Modifier.size(icon(23f)))
    })
}

/**
 * All the controls over the landscape cover, the way the fullscreen now bar lays them over its
 * cover: the floating glass buttons across the top, the playback row, and the timeline along the
 * bottom. While they show, the cover darkens to 55% (`--ArtworkBrightness`) and they sit at 98.5%
 * (`--ControlsOpacity`), both following [shown]. The glyphs and buttons scale with the cover so
 * everything fits in one row; touches between them reach the cover's own gestures.
 */
@Composable
fun CoverControls(
    controls: PlaybackControlsState,
    romanizeAvailable: Boolean,
    romanized: Boolean,
    onToggleRomanize: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLyricsManager: (() -> Unit)?,
    onOpenQueue: (() -> Unit)? = null,
    onOpenSource: (() -> Unit)? = null,
    onOpenQuickSettings: (() -> Unit)?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSwapSide: (() -> Unit)?,
    coverOnRight: Boolean = false,
    shown: () -> Float,
    modifier: Modifier = Modifier,
    onEnterPip: (() -> Unit)? = null,
    showResync: Boolean = true,
    showExpand: Boolean = true,
) {
    val shuffle = controls.customActions.firstOrNull { it.kind == ActionKind.Shuffle }
    val repeat = controls.customActions.firstOrNull { it.kind == ActionKind.Repeat }
    val others = controls.customActions.filter { it.kind == ActionKind.Other }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val side = maxWidth
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    alpha = shown()
                    shape = RoundedCornerShape(size.width * COVER_CORNER_FRACTION)
                    clip = true
                }
                .background(Color.Black.copy(alpha = 0.45f)),
        )
        Column(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = shown() * 0.985f }
                .padding(horizontal = side * 0.06f, vertical = side * 0.06f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FloatingButtons(
                floatingActions(controls, others, romanizeAvailable, romanized, onToggleRomanize, onOpenSettings, onOpenLyricsManager, onOpenQuickSettings, expanded, onToggleExpanded, onSwapSide, coverOnRight, showResync, showExpand, onOpenQueue = onOpenQueue, onOpenSource = onOpenSource, onEnterPip = onEnterPip),
                side * 0.88f, minSize = COVER_BUTTON_MIN, maxSize = COVER_BUTTON_MAX, minGap = COVER_BUTTON_GAP, maxGap = SpicySpacing.S3,
            )
            Spacer(Modifier.weight(1f))
            PlaybackRow(controls, shuffle, repeat, Modifier, skipWidth = side * 0.15f, fillSlots = true)
            Spacer(Modifier.weight(1f))
            Timeline(controls, shown, Modifier)
        }
    }
}

private val SIDE_MARGIN = 28.dp
/** The bottom controls' widest, so they don't stretch across a landscape or tablet screen. */
private val CONTROLS_MAX_WIDTH = 560.dp
/** The glass buttons over the cover: as big as fits in one row, between these; the rest go in More. */
private val COVER_BUTTON_MAX = 40.dp
private val COVER_BUTTON_MIN = 32.dp
private val COVER_BUTTON_GAP = 6.dp
/** The one-row landscape bar ([ControlsBar]). */
private val BAR_MIDDLE_WIDTH = 360.dp
private val BAR_SKIP_WIDTH = 36.dp
private val BAR_BOTTOM_MARGIN = 12.dp
private val BAR_SHADE_REACH = 72.dp
/** The landscape cover's corners (`border-radius: 2cqh` against its square). */
private const val COVER_CORNER_FRACTION = 0.02f
private val ROW_GAP = 24.dp
/** The floating buttons, sized to match the transport so they don't look lost under it. */
private val FLOATING_SIZE = 48.dp
private val BOTTOM_MARGIN = 84.dp
/** How far above the controls the shade starts. */
private val SHADE_REACH = 180.dp
private val SHADE_BLUR = 24.dp
private const val SHADE_ALPHA = 0.3f

/**
 * The shade's ramp, for both blur and darkening: flat at the top so it has no visible edge where
 * it starts, steepest in the middle, flat again at full strength.
 */
private val ShadeEasing = CubicBezierEasing(0.45f, 0f, 0.35f, 1f)

/** [ShadeEasing] sampled into stops, since a gradient only ramps linearly between stops. */
private val ShadeGradient = Brush.verticalGradient(
    *Array(9) { i ->
        val t = i / 8f
        t to Color.Black.copy(alpha = SHADE_ALPHA * ShadeEasing.transform(t))
    },
)

/** Where a player's custom action goes. Players only give a name, so this reads the name. */
internal enum class ActionKind { Shuffle, Repeat, Other }

internal val SessionCustomAction.kind: ActionKind
    get() {
        val text = "$name $action".lowercase()
        return when {
            "shuffle" in text -> ActionKind.Shuffle
            "repeat" in text || "loop" in text -> ActionKind.Repeat
            else -> ActionKind.Other
        }
    }

/** A player's own icon, in white like everything else here; its name when it sent no icon. */
@Composable
private fun ActionIcon(action: SessionCustomAction, modifier: Modifier) {
    val bitmap = action.icon
    if (bitmap != null) {
        val painter = remember(bitmap) { BitmapPainter(bitmap.asImageBitmap()) }
        Image(painter, null, modifier, colorFilter = ColorFilter.tint(SpicyColors.TextPrimary))
    } else {
        Text(action.name.take(2), style = TimeStyle.copy(color = SpicyColors.TextPrimary))
    }
}

/**
 * `.PlaybackControls`: shuffle and repeat 7 units wide, skips 12, play/pause 9.25, spread across
 * the row. A slot the player has no action for stays empty so play stays centred.
 */
@Composable
private fun PlaybackRow(
    controls: PlaybackControlsState,
    shuffle: SessionCustomAction?,
    repeat: SessionCustomAction?,
    modifier: Modifier,
    skipWidth: Dp = SKIP_WIDTH,
    /** Each control gets an equal share of the row as its touch area, for rows too narrow for 48dp ones. */
    fillSlots: Boolean = false,
) {
    val playWidth = skipWidth * (9.25f / 12f)
    val sideWidth = skipWidth * (7f / 12f)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val slot = if (fillSlots) Modifier.weight(1f).height(TOUCH_SIZE) else null
        SideAction(shuffle, controls, sideWidth, slot)
        val animate = LocalUiAnimations.current
        val scope = rememberCoroutineScope()
        // The skips move on and play turns into pause, Apple Music's way, when animations are on.
        val previous = remember { Animatable(0f) }
        val next = remember { Animatable(0f) }
        fun moveOn(glyph: Animatable<Float, *>) = scope.launch {
            glyph.snapTo(0f)
            glyph.animateTo(1f, tween(SKIP_MOVE_MS, easing = FastOutSlowInEasing))
            glyph.snapTo(0f)
        }
        val pause by animateFloatAsState(
            if (controls.isPlaying) 1f else 0f,
            if (animate) tween(MORPH_MS, easing = FastOutSlowInEasing) else snap(),
            label = "playPause",
        )
        val previousGlyph = remember { SkipGlyph { previous.value } }
        val nextGlyph = remember { SkipGlyph { next.value } }
        val playPauseGlyph = remember { PlayPauseGlyph { pause } }
        val skip = rememberVectorPainter(SpicyIcons.TrackSkip)
        PressableGlyph(
            if (animate) previousGlyph else skip, skipWidth, SpicyIcons.TrackSkip.aspect, "Previous", rotate = true, touch = slot,
            onClick = { if (animate) moveOn(previous); controls.onPrevious() },
        )
        val playIcon = if (controls.isPlaying) SpicyIcons.Pause else SpicyIcons.Play
        PressableGlyph(
            if (animate) playPauseGlyph else rememberVectorPainter(playIcon),
            playWidth,
            if (animate) SpicyIcons.Pause.aspect else playIcon.aspect,
            if (controls.isPlaying) "Pause" else "Play",
            touch = slot,
            onClick = controls.onPlayPause,
        )
        PressableGlyph(
            if (animate) nextGlyph else skip, skipWidth, SpicyIcons.TrackSkip.aspect, "Next", touch = slot,
            onClick = { if (animate) moveOn(next); controls.onNext() },
        )
        SideAction(repeat, controls, sideWidth, slot)
    }
}

/** Big, like Apple Music's transport, which suits a thumb. */
private val SKIP_WIDTH = 52.dp
/** How long a skip glyph takes to move on, and play to turn into pause. */
private const val SKIP_MOVE_MS = 420
private const val MORPH_MS = 260

@Composable
private fun SideAction(action: SessionCustomAction?, controls: PlaybackControlsState, width: Dp, slot: Modifier?) {
    val bitmap = action?.icon
    if (bitmap == null) {
        Spacer(slot ?: Modifier.size(TOUCH_SIZE))
        return
    }
    val painter = remember(bitmap) { BitmapPainter(bitmap.asImageBitmap()) }
    PressableGlyph(
        painter, width, 1f, action.name,
        colorFilter = ColorFilter.tint(SpicyColors.TextPrimary),
        touch = slot,
        onClick = { controls.onCustomAction(action.action) },
    )
}

private val ImageVector.aspect get() = viewportWidth / viewportHeight

/** Glyphs get a 48dp touch area however small they are drawn. */
private val TOUCH_SIZE = 48.dp

/** `transition: transform 0.175s cubic-bezier(0.37, 0, 0.63, 1)` into `.Pressed`. */
private val PressEasing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)
private val CssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

/**
 * A transport button: shrinks to 0.9 while held, then plays a 0.6 s bounce on release.
 */
@Composable
private fun PressableGlyph(
    painter: Painter,
    width: Dp,
    aspect: Float,
    description: String,
    rotate: Boolean = false,
    colorFilter: ColorFilter? = null,
    /** The touch area; null: at least 48dp square around the glyph. */
    touch: Modifier? = null,
    onClick: () -> Unit,
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    // The screen recomposes on every touch; keying the gesture on a fresh lambda would cancel
    // the press before it lifts.
    val currentOnClick by rememberUpdatedState(onClick)
    val haptics by rememberUpdatedState(LocalHapticFeedback.current)
    Box(
        modifier = (touch ?: Modifier.size(width = maxOf(TOUCH_SIZE, width + 12.dp), height = TOUCH_SIZE))
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick { currentOnClick(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    scope.launch { scale.animateTo(SHRINK, tween(175, easing = PressEasing)) }
                    val up = waitForUpOrCancellation()
                    scope.launch { scale.animateTo(1f, PressBounce) }
                    if (up != null) {
                        up.consume()
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        currentOnClick()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            colorFilter = colorFilter,
            modifier = Modifier
                .width(width)
                .aspectRatio(aspect)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    rotationZ = if (rotate) 180f else 0f
                },
        )
    }
}

private const val SHRINK = 0.9f

/** `@keyframes pressAnimation`: scale(1 - ShrinkDelta * k) with CSS's default `ease` per step. */
private val PressBounce = keyframes {
    val delta = 1f - SHRINK
    durationMillis = 600
    (1f - delta * 1f) at 0 using CssEase
    (1f - delta * -0.32f) at 96 using CssEase
    (1f - delta * 0.13f) at 168 using CssEase
    (1f - delta * -0.05f) at 264 using CssEase
    (1f - delta * 0.02f) at 354 using CssEase
    (1f - delta * -0.01f) at 438 using CssEase
    1f at 528 using CssEase
}

/** Times: weight 500, 60% white, tabular figures. */
private val TimeStyle = TextStyle(
    fontFamily = LyricsLayoutCalculator.spicyFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    fontFeatureSettings = "tnum",
    color = SpicyPalette.Neutral.TextSecondary,
)

/**
 * The glass timeline: a frosted capsule whose white fill is the progress,
 * thickening while dragged. Seeks once, on release, because MediaSession seeks are slow and
 * seeking along the drag would make the lyrics jump about. Elapsed and total time sit underneath.
 */
@Composable
private fun Timeline(controls: PlaybackControlsState, shown: () -> Float, modifier: Modifier) {
    val current by rememberUpdatedState(controls)
    val haptics by rememberUpdatedState(LocalHapticFeedback.current)
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var positionMs by remember { mutableLongStateOf(controls.positionMs()) }
    val shownUpdated by rememberUpdatedState(shown)
    LaunchedEffect(controls.positionMs) {
        while (true) {
            // Faded out, nobody sees the bar move: a few updates a second keep it close.
            if (shownUpdated() == 0f) delay(HIDDEN_UPDATE_MS)
            withFrameMillis { positionMs = controls.positionMs() }
        }
    }
    val duration = controls.durationMs.coerceAtLeast(0L)
    val durationUpdated by rememberUpdatedState(duration)
    // Read only where it is drawn, so the moving position redraws the bar without recomposing.
    fun shownMs() = dragFraction?.let { (it * durationUpdated).toLong() } ?: positionMs
    val elapsedText by remember { derivedStateOf { formatClock(shownMs()) } }

    val thickness = remember { Animatable(BAR_HEIGHT.value) }
    LaunchedEffect(dragFraction != null) {
        // `transition: height 0.28s cubic-bezier(0.34, 1.56, 0.64, 1)`.
        thickness.animateTo(
            if (dragFraction != null) BAR_HEIGHT_DRAGGING.value else BAR_HEIGHT.value,
            tween(280, easing = SpicyMotion.Overshoot),
        )
    }
    val seekable = controls.canSeek && duration > 0L

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(BAR_TOUCH_HEIGHT)
                .pointerInput(seekable, duration) {
                    if (!seekable) return@pointerInput
                    detectTapGestures {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        current.onSeek((it.x / size.width * duration).toLong().coerceIn(0L, duration))
                    }
                }
                .pointerInput(seekable, duration) {
                    if (!seekable) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = {
                            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            dragFraction = (it.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                            dragFraction?.let { current.onSeek((it * duration).toLong()) }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                    )
                },
        ) {
            val barHeight = thickness.value.dp.toPx()
            val top = (size.height - barHeight) / 2f
            val radius = CornerRadius(barHeight / 2f)
            // `--material-regular-bg: rgba(0,0,0,.1)` with the capsule's 1px white inner edge.
            drawRoundRect(Color.Black.copy(alpha = 0.1f), Offset(0f, top), Size(size.width, barHeight), radius)
            drawRoundRect(
                Color.White.copy(alpha = 0.16f), Offset(0f, top), Size(size.width, barHeight), radius,
                style = Stroke(width = 1.dp.toPx()),
            )
            // The fill is a scaleX of a full-width white bar, so it keeps the capsule's rounded clip.
            val capsule = Path().apply { addRoundRect(RoundRect(0f, top, size.width, top + barHeight, radius)) }
            val progress = if (duration > 0L) (shownMs().toFloat() / duration).coerceIn(0f, 1f) else 0f
            clipPath(capsule) {
                drawRect(Color.White, Offset(0f, top), Size(size.width * progress, barHeight))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(elapsedText, style = TimeStyle, maxLines = 1)
            Text(formatClock(duration), style = TimeStyle, maxLines = 1)
        }
    }
}

private const val HIDDEN_UPDATE_MS = 250L
private val BAR_HEIGHT = 10.dp
private val BAR_HEIGHT_DRAGGING = 14.dp
private val BAR_TOUCH_HEIGHT = 32.dp

internal fun formatClock(ms: Long): String {
    val totalSeconds = (ms / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

/*
 * The glyphs with a motion of their own, on the same 24-unit line grid as [SpicyIcons]. Each part
 * moves over 0.25 s (0.2 s for the corners' nudge, 0.3 s for the gear), easing out. Phones have
 * no hover, so what a pointer resting on the button would do happens while it's held.
 */
private object Glyphs {
    private fun nodes(path: String) = PathParser().parsePathString(path).toNodes()
    val Gear = nodes("M10.07 5.69L10.48 3.38A8.75 8.75 0 0 1 13.52 3.38L13.93 5.69A6.6 6.6 0 0 1 15.1 6.17L17.02 4.83A8.75 8.75 0 0 1 19.17 6.98L17.83 8.9A6.6 6.6 0 0 1 18.31 10.07L20.62 10.48A8.75 8.75 0 0 1 20.62 13.52L18.31 13.93A6.6 6.6 0 0 1 17.83 15.1L19.17 17.02A8.75 8.75 0 0 1 17.02 19.17L15.1 17.83A6.6 6.6 0 0 1 13.93 18.31L13.52 20.62A8.75 8.75 0 0 1 10.48 20.62L10.07 18.31A6.6 6.6 0 0 1 8.9 17.83L6.98 19.17A8.75 8.75 0 0 1 4.83 17.02L6.17 15.1A6.6 6.6 0 0 1 5.69 13.93L3.38 13.52A8.75 8.75 0 0 1 3.38 10.48L5.69 10.07A6.6 6.6 0 0 1 6.17 8.9L4.83 6.98A8.75 8.75 0 0 1 6.98 4.83L8.9 6.17A6.6 6.6 0 0 1 10.07 5.69Z")
    val Hub = nodes("M14.75 12a2.75 2.75 0 1 1-5.5 0a2.75 2.75 0 1 1 5.5 0Z")
    // Four rounded corners at the edges, each with the way it nudges: outward to show the
    // cover, toward the middle (the same corners turned inside out) to go back.
    val ExpandCorners = listOf(
        nodes("M3.5 8.5V6A2.5 2.5 0 0 1 6 3.5h2.5") to Offset(-1f, -1f),
        nodes("M15.5 3.5H18A2.5 2.5 0 0 1 20.5 6v2.5") to Offset(1f, -1f),
        nodes("M20.5 15.5V18a2.5 2.5 0 0 1-2.5 2.5h-2.5") to Offset(1f, 1f),
        nodes("M8.5 20.5H6A2.5 2.5 0 0 1 3.5 18v-2.5") to Offset(-1f, 1f),
    )
    val CollapseCorners = listOf(
        nodes("M8.5 3.5V6A2.5 2.5 0 0 1 6 8.5H3.5") to Offset(1f, 1f),
        nodes("M20.5 8.5H18A2.5 2.5 0 0 1 15.5 6V3.5") to Offset(-1f, 1f),
        nodes("M15.5 20.5V18a2.5 2.5 0 0 1 2.5-2.5h2.5") to Offset(-1f, -1f),
        nodes("M3.5 15.5H6A2.5 2.5 0 0 1 8.5 18v2.5") to Offset(1f, -1f),
    )
    val Screen = nodes("M6 4h12a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3Z")
    val Cover = nodes("M7.25 9.5h2.5a1.25 1.25 0 0 1 1.25 1.25v2.5a1.25 1.25 0 0 1-1.25 1.25h-2.5A1.25 1.25 0 0 1 6 13.25v-2.5A1.25 1.25 0 0 1 7.25 9.5Z")
    val Arrow = nodes("M14 12h4m-1.75-1.75L18 12l-1.75 1.75")
}

private val GlyphEase = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

@Composable
private fun lineGlyph(content: @Composable (Float, Float) -> Unit) =
    rememberVectorPainter(24.dp, 24.dp, 24f, 24f, content = content)

@Composable
private fun LinePath(nodes: List<PathNode>) = Path(
    nodes,
    stroke = SolidColor(SpicyPalette.Neutral.TextPrimary),
    strokeLineWidth = 2f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
)

/** The gear, turning an eighth while its button is held. */
@Composable
private fun SettingsGlyph(modifier: Modifier) {
    val turn by animateFloatAsState(if (LocalGlyphPressed.current) 45f else 0f, tween(300, easing = GlyphEase), label = "gear")
    Image(lineGlyph { _, _ ->
        Group(rotation = turn, pivotX = 12f, pivotY = 12f) { LinePath(Glyphs.Gear) }
        LinePath(Glyphs.Hub)
    }, null, modifier)
}

/** Corners out to show the cover, in to go back; each nudges its own way while held. */
@Composable
private fun ExpandGlyph(expanded: Boolean, modifier: Modifier) {
    val nudge by animateFloatAsState(if (LocalGlyphPressed.current) 1f else 0f, tween(200, easing = GlyphEase), label = "corners")
    val corners = if (expanded) Glyphs.CollapseCorners else Glyphs.ExpandCorners
    Image(lineGlyph { _, _ ->
        corners.forEach { (nodes, way) ->
            Group(translationX = way.x * nudge, translationY = way.y * nudge) { LinePath(nodes) }
        }
    }, null, modifier)
}

/**
 * The screen with the cover on the side it's on now and an arrow to the other: when the cover
 * moves, it slides across and the arrow turns to point back.
 */
@Composable
private fun SwapSideGlyph(coverOnRight: Boolean, modifier: Modifier) {
    val side by animateFloatAsState(if (coverOnRight) 1f else 0f, tween(250, easing = GlyphEase), label = "swapSide")
    Image(lineGlyph { _, _ ->
        LinePath(Glyphs.Screen)
        Group(translationX = 7f * side) { LinePath(Glyphs.Cover) }
        Group(rotation = 180f * side, pivotX = 16f, pivotY = 12f, translationX = -8f * side) { LinePath(Glyphs.Arrow) }
    }, null, modifier)
}

/** The controls' glyphs, in white. */
internal object SpicyIcons {
    val TrackSkip = icon(
        35f, 20f,
        "M 19.467 19.905 C 20.008 19.905 20.463 19.746 21.005 19.426 L 33.61 12.023 C 34.533 11.482 35 10.817 35 9.993 C 35 9.158 34.545 8.53 33.61 7.977 L 21.005 0.574 C 20.463 0.254 19.998 0.094 19.456 0.094 C 18.374 0.094 17.475 0.917 17.475 2.418 L 17.475 9.49 C 17.315 8.898 16.873 8.408 16.135 7.977 L 3.529 0.574 C 3 0.254 2.533 0.094 1.993 0.094 C 0.911 0.094 0 0.917 0 2.418 L 0 17.582 C 0 19.083 0.91 19.906 1.993 19.906 C 2.533 19.906 3 19.746 3.529 19.426 L 16.135 12.023 C 16.861 11.593 17.315 11.088 17.475 10.485 L 17.475 17.582 C 17.475 19.083 18.386 19.906 19.467 19.906 L 19.467 19.905 Z",
    )
    val Play = icon(
        18f, 20f,
        "M 1.558 20 C 2.006 20 2.381 19.838 2.874 19.561 L 16.622 11.572 C 17.527 11.053 17.894 10.65 17.894 9.997 C 17.894 9.35 17.527 8.948 16.622 8.419 L 2.874 0.439 C 2.381 0.153 2.006 0 1.558 0 C 0.706 0 0.106 0.654 0.106 1.694 L 0.106 18.298 C 0.106 19.346 0.706 20 1.558 20 L 1.558 20 Z",
    )
    val Pause = icon(
        15f, 20f,
        "M 4.427 19.963 C 5.513 19.963 6.06 19.416 6.06 18.33 L 6.06 1.66 C 6.06 0.545 5.513 0.037 4.427 0.037 L 1.633 0.037 C 0.548 0.037 0 0.575 0 1.66 L 0 18.331 C -0.009 19.416 0.538 19.963 1.633 19.963 L 4.427 19.963 Z M 13.377 19.963 C 14.462 19.963 15 19.416 15 18.33 L 15 1.66 C 15 0.545 14.462 0.037 13.376 0.037 L 10.573 0.037 C 9.487 0.037 8.949 0.575 8.949 1.66 L 8.949 18.331 C 8.949 19.416 9.487 19.963 10.573 19.963 L 13.376 19.963 L 13.377 19.963 Z",
    )
    // A and ア are both "a": the script a tap switches to is drawn at full strength, the one it
    // leaves dimmed.
    val DisableRomanization = line(
        DIM to "M2.5 18 6.75 6 11 18", DIM to "M4 14h5.5",
        1f to "M13.5 6.5h8L19 9.75", 1f to "M17.25 9.75v1.75c0 2.75-1.25 5-3.75 6.5",
    )
    val EnableRomanization = line(
        1f to "M2.5 18 6.75 6 11 18", 1f to "M4 14h5.5",
        DIM to "M13.5 6.5h8L19 9.75", DIM to "M17.25 9.75v1.75c0 2.75-1.25 5-3.75 6.5",
    )

    /** Lucide's refresh-cw, for resyncing with the player. */
    val Resync = lucide(
        "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
        "M21 3v5h-5",
        "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
        "M8 16H3v5",
    )

    /** A lyric sheet in front of another, for the Lyrics Manager. */
    val LyricsManager = lucide(
        "M8 3h8.5A2.5 2.5 0 0 1 19 5.5V16",
        "M7.5 6h6a2.5 2.5 0 0 1 2.5 2.5v10a2.5 2.5 0 0 1-2.5 2.5h-6a2.5 2.5 0 0 1-2.5-2.5v-10a2.5 2.5 0 0 1 2.5-2.5Z",
        "M8 10.5h5", "M8 14h5", "M8 17.5h3",
    )

    /** A line icon: 24-unit paths stroked 2 units wide with round caps and joins. */
    private fun lucide(vararg paths: String): ImageVector = line(*paths.map { 1f to it }.toTypedArray())

    /** [lucide], each path at its own alpha. */
    private fun line(vararg paths: Pair<Float, String>): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                paths.forEach { (alpha, path) ->
                    addPath(
                        PathParser().parsePathString(path).toNodes(),
                        stroke = SolidColor(SpicyPalette.Neutral.TextPrimary),
                        strokeAlpha = alpha,
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
            .build()

    /** How strongly the script a romanize tap leaves is drawn. */
    private const val DIM = 0.4f

    private fun icon(width: Float, height: Float, vararg paths: String): ImageVector =
        ImageVector.Builder(defaultWidth = width.dp, defaultHeight = height.dp, viewportWidth = width, viewportHeight = height)
            .apply { paths.forEach { addPath(PathParser().parsePathString(it).toNodes(), fill = SolidColor(Color.White)) } }
            .build()
}
