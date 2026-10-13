package com.tx24.spicyplayer.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.tx24.spicyplayer.ui.components.DescriptionStyle
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicyRadii
import com.tx24.spicyplayer.ui.theme.SpicyType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tx24.spicyplayer.analytics.UsageCounter
import com.tx24.spicyplayer.analytics.UsageStats
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.playback.ExternalPlaybackViewModel
import com.tx24.spicyplayer.playback.PlayerUiState
import com.tx24.spicyplayer.ui.components.LocalUiAnimations
import com.tx24.spicyplayer.ui.components.SettingRow
import com.tx24.spicyplayer.ui.components.SettingsSkeleton
import com.tx24.spicyplayer.ui.components.SpicyModal
import com.tx24.spicyplayer.ui.components.screenSlide
import com.tx24.spicyplayer.ui.lyricsmanager.LyricsManagerContent
import com.tx24.spicyplayer.ui.theme.SpicySpacing
import dev.chrisbanes.haze.HazeState

/** The screens of quick settings; a floating button can open it on its own. */
enum class QuickScreen { Main, Queue, LyricsManager, Source }

/**
 * The quick settings pop-up, from its floating button: tiles for the player's queue (when it
 * shares one), the song's lyrics source and the Lyrics Manager, then the language and delays, tuned while the lyrics keep playing behind it rather
 * than from inside Settings. The queue and the Lyrics Manager open as screens of this pop-up,
 * sliding in over it, with a way back.
 */
@Composable
fun QuickSettingsModal(
    visible: Boolean,
    state: PlayerUiState,
    viewModel: ExternalPlaybackViewModel,
    settings: AppSettings,
    backdrop: HazeState?,
    /** The screen it opens on: the queue's and the source's own floating buttons open theirs. */
    startOn: QuickScreen = QuickScreen.Main,
    onDismiss: () -> Unit,
) {
    var screen by remember { mutableStateOf(QuickScreen.Main) }
    var uploading by remember { mutableStateOf(false) }
    LaunchedEffect(visible, state.lyrics) { if (visible) viewModel.detectLyricsLanguage() }
    // Opens on its first screen each time.
    LaunchedEffect(visible) {
        if (visible) {
            screen = startOn
            if (startOn == QuickScreen.Queue) UsageStats.count(UsageCounter.QUEUE)
            uploading = false
        }
    }
    fun open(target: QuickScreen) {
        if (target == QuickScreen.Queue) UsageStats.count(UsageCounter.QUEUE)
        if (target == QuickScreen.LyricsManager) {
            UsageStats.count(UsageCounter.LYRICS_MANAGER)
            viewModel.refreshLocalLyrics()
        }
        screen = target
    }
    SpicyModal(
        visible = visible,
        onDismissRequest = onDismiss,
        backdrop = backdrop,
        title = when (screen) {
            QuickScreen.Main -> "Quick settings"
            QuickScreen.Queue -> "Queue"
            QuickScreen.LyricsManager -> if (uploading) "Upload TTML" else "Local Lyrics DB"
            QuickScreen.Source -> "Lyrics source"
        },
        skeleton = { SettingsSkeleton(rows = 4) },
        // The header's back arrow (and Back) step out of a screen before Back closes the pop-up.
        onBack = when {
            screen == QuickScreen.Main -> null
            uploading -> ({ uploading = false })
            else -> ({ screen = QuickScreen.Main })
        },
    ) {
        val animate = LocalUiAnimations.current
        AnimatedContent(
            targetState = screen,
            transitionSpec = { screenSlide(forward = targetState != QuickScreen.Main, animate = animate) },
            label = "quickSettingsScreen",
        ) { shown ->
            Column(verticalArrangement = Arrangement.spacedBy(SpicySpacing.S3)) {
                when (shown) {
                    QuickScreen.Main -> MainScreen(state, viewModel, ::open)
                    // Its own height: the list scrolls inside the pop-up's body.
                    QuickScreen.Queue -> QueueList(state, viewModel, onJumped = onDismiss, modifier = Modifier.height(QUEUE_HEIGHT))
                    QuickScreen.LyricsManager -> LyricsManagerContent(state, viewModel, settings, uploading, { uploading = it }, onDismiss)
                    QuickScreen.Source -> SourceList(state, viewModel, onChosen = { screen = QuickScreen.Main }, modifier = Modifier.height(QUEUE_HEIGHT))
                }
            }
        }
    }
}

@Composable
private fun MainScreen(state: PlayerUiState, viewModel: ExternalPlaybackViewModel, open: (QuickScreen) -> Unit) {
    // The screens this opens share one row of tiles, so the settings tuned right here keep the room.
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(SpicySpacing.S2)) {
        if (state.queue.isNotEmpty()) {
            QuickTile(
                label = "Queue",
                detail = "${state.queue.size} ${if (state.queue.size == 1) "song" else "songs"}",
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
            ) { open(QuickScreen.Queue) }
        }
        if (state.localLyricsKey != null && state.lyrics !is LyricsState.Idle) {
            val chosen = state.sourceOverride?.let { id -> state.sourceDescriptors.firstOrNull { it.id == id }?.displayName ?: id }
            QuickTile(
                label = "Source",
                detail = chosen ?: (state.lyrics as? LyricsState.Ready)?.provider?.let { "Auto: $it" } ?: "Automatic",
                icon = Icons.Rounded.Source,
            ) { open(QuickScreen.Source) }
        }
        QuickTile(label = "Lyrics Manager", detail = "Save or upload", icon = Icons.Rounded.LibraryMusic) { open(QuickScreen.LyricsManager) }
    }
    LyricsLanguageRow(state, viewModel)
    SongDelayRow(state, viewModel)
    SettingRow(
        label = "Output delay",
        description = "For every song on ${state.outputLabel}. Move it right if the lyrics run ahead of the song.",
        icon = Icons.Rounded.Timer,
        stacked = true,
    ) {
        DelayControl(state.lyricDelayMs, viewModel::setLyricDelay)
    }
}

/** One of the screens quick settings opens: its glyph, name and what it holds now. */
@Composable
private fun RowScope.QuickTile(label: String, detail: String, icon: ImageVector, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(SpicyRadii.Md)
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(shape)
            .background(if (pressed) SpicyColors.TintBgPressed else SpicyColors.TintBg)
            .border(1.dp, SpicyColors.Hairline, shape)
            .clickable(interaction, indication = null, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .padding(SpicySpacing.S3),
        verticalArrangement = Arrangement.spacedBy(SpicySpacing.S2),
    ) {
        Icon(icon, null, tint = SpicyColors.TextSecondary, modifier = Modifier.size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = SpicyType.Body.copy(fontWeight = FontWeight.Medium))
            Text(detail, maxLines = 2, overflow = TextOverflow.Ellipsis, style = DescriptionStyle)
        }
    }
}

/** The queue's height in the pop-up: room for about eight songs. */
private val QUEUE_HEIGHT = 440.dp
