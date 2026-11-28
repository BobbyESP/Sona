/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.call

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bobbyesp.sona.calling.domain.CallState
import com.bobbyesp.sona.core.designsystem.blur.BlurSource
import com.bobbyesp.sona.core.designsystem.blur.blurSource
import com.bobbyesp.sona.core.designsystem.blur.frosted
import com.bobbyesp.sona.core.designsystem.blur.rememberBlurSource
import com.bobbyesp.sona.core.designsystem.components.Dialpad
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme
import com.bobbyesp.sona.feature.call.video.Corner
import com.bobbyesp.sona.feature.call.video.QualityIndicator
import com.bobbyesp.sona.feature.call.video.StatisticsPanel
import com.bobbyesp.sona.feature.call.video.VideoStage

/**
 * A call with video on: the pictures fill the screen, and the controls float over them, fading a
 * few seconds after the last touch.
 *
 * The controls are a bottom bar upright, an end rail lying down. The panels open from the bottom,
 * or beside the picture on a wide window. Buttons are icons with words for TalkBack, so nothing is
 * cut at a large font.
 */
@Composable
internal fun VideoCallScreen(
    state: CallUiState,
    onIntent: (CallIntent) -> Unit,
    actions: CallActions,
) {
    val call = state.call ?: return
    val behind = rememberBlurSource()
    var fill by rememberSaveable { mutableStateOf(true) }
    var swapWanted by rememberSaveable { mutableStateOf(false) }
    var corner by rememberSaveable { mutableStateOf(Corner.TopEnd) }
    // Swapped only while there is a camera picture to make big.
    val swapped = swapWanted && call.video?.sending == true

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rail = maxWidth > maxHeight
        val wide = maxWidth >= WideWidth
        val safe = WindowInsets.safeDrawing.asPaddingValues()
        val direction = LocalLayoutDirection.current
        // The tile keeps clear of the top line and of the controls, wherever they are.
        val tileInsets =
            PaddingValues(
                start = safe.calculateStartPadding(direction),
                top = safe.calculateTopPadding() + TopReserve,
                end = safe.calculateEndPadding(direction) + if (rail) RailReserve else 0.dp,
                bottom = safe.calculateBottomPadding() + if (rail) 0.dp else BarReserve,
            )

        VideoStage(
            call = call,
            paused = state.videoPaused,
            fill = fill,
            swapped = swapped,
            corner = corner,
            onCorner = { corner = it },
            onSwap = {
                swapWanted = !swapped
                onIntent(CallIntent.DismissMore)
            },
            onTap = { onIntent(CallIntent.TapScreen) },
            onDoubleTap = { fill = !fill },
            tileInsets = tileInsets,
            modifier = Modifier.fillMaxSize().blurSource(behind),
        )

        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
            Column(
                modifier =
                    Modifier.align(Alignment.TopCenter)
                        .widthIn(max = BannerMaxWidth)
                        .padding(end = if (rail) RailReserve else 0.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AnimatedVisibility(
                    visible = state.controlsVisible,
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
                ) {
                    TopLine(state, behind, onIntent)
                }
                CallBanners(state, onIntent, actions, behind)
            }

            AnimatedVisibility(
                visible = state.controlsVisible,
                modifier =
                    Modifier.align(if (rail) Alignment.CenterEnd else Alignment.BottomCenter),
                enter =
                    fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                        if (rail) slideInHorizontally { it / 2 } else slideInVertically { it / 2 },
                exit =
                    fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                        if (rail) slideOutHorizontally { it / 2 }
                        else slideOutVertically { it / 2 },
            ) {
                VideoControls(state, onIntent, actions, vertical = rail, behind = behind)
            }
        }

        val panelOpen = state.statisticsOpen || state.keypadOpen
        AnimatedVisibility(
            visible = panelOpen,
            modifier = Modifier.align(if (wide) Alignment.CenterEnd else Alignment.BottomCenter),
            enter =
                fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    if (wide) slideInHorizontally { it } else slideInVertically { it },
            exit =
                fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    if (wide) slideOutHorizontally { it } else slideOutVertically { it },
        ) {
            CallPanel(wide = wide, behind = behind, maxHeight = maxHeight * PanelHeightShare) {
                if (state.statisticsOpen) {
                    StatisticsPanel(
                        statistics = state.statistics,
                        history = state.history,
                        quality = state.quality,
                        measured = state.statistics != null || state.quality != null,
                        onClose = { onIntent(CallIntent.ToggleStatistics) },
                    )
                } else if (state.keypadOpen) {
                    KeypadPanel(state, onIntent)
                }
            }
        }
    }
}

/**
 * The statistics or the keypad over the call: beside it on a wide window, from the bottom on a
 * phone.
 */
@Composable
internal fun CallPanel(
    wide: Boolean,
    behind: BlurSource,
    maxHeight: Dp,
    content: @Composable () -> Unit,
) {
    val shape = MaterialTheme.shapes.extraLarge
    val modifier =
        if (wide) Modifier.fillMaxHeight().width(PanelWidth).safeDrawingPadding().padding(12.dp)
        else
            Modifier.fillMaxWidth()
                .heightIn(max = maxHeight)
                .safeDrawingPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp)
    Box(
        modifier.frosted(
            behind,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape,
        ),
    ) {
        content()
    }
}

@Composable
private fun KeypadPanel(state: CallUiState, onIntent: (CallIntent) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.dtmf,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = SonaTheme.typography.monospace,
                modifier = Modifier.weight(1f).heightIn(min = 40.dp),
            )
            FilledTonalIconToggleButton(
                checked = true,
                onCheckedChange = { onIntent(CallIntent.ToggleKeypad) },
                shapes = IconButtonDefaults.toggleableShapes(),
            ) {
                Icon(
                    Icons.Rounded.Dialpad,
                    contentDescription = stringResource(R.string.call_hide_keypad),
                )
            }
        }
        Dialpad(
            onKey = { onIntent(CallIntent.Dtmf(it)) },
            tones = state.dialpadTones,
            keyAspectRatio = 1.9f,
        )
    }
}

@Composable
private fun TopLine(state: CallUiState, behind: BlurSource, onIntent: (CallIntent) -> Unit) {
    val call = state.call ?: return
    val colors = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier.frosted(behind, colors.surfaceContainer, MaterialTheme.shapes.extraLarge)
                .padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f, fill = false).padding(vertical = 6.dp)) {
            Text(
                call.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when (call.state) {
                    CallState.Active -> Timer(call.connectedAt, MaterialTheme.typography.labelLarge)
                    else ->
                        Text(
                            call.statusWords().orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                }
                call.media?.encryption?.let {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = it,
                        modifier = Modifier.size(14.dp),
                    )
                }
                state.account?.let {
                    Text(
                        it.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        QualityIndicator(
            quality = state.quality,
            onClick = { onIntent(CallIntent.ToggleStatistics) },
            container = Color.Transparent,
        )
    }
}

/** The video call's controls. Hang up stays in reach when the others have to scroll. */
@Composable
private fun VideoControls(
    state: CallUiState,
    onIntent: (CallIntent) -> Unit,
    actions: CallActions,
    vertical: Boolean,
    behind: BlurSource,
) {
    val call = state.call ?: return
    val live = !call.isOver
    val buttons: @Composable () -> Unit = {
        VideoToggle(
            icon = if (call.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
            label = stringResource(R.string.call_mute),
            checked = call.muted,
            enabled = live,
            onClick = { onIntent(CallIntent.ToggleMute) },
        )
        VideoToggle(
            icon = if (call.cameraOn) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
            label =
                stringResource(
                    if (call.cameraOn) R.string.call_camera_off else R.string.call_camera_on,
                ),
            checked = !call.cameraOn,
            enabled = live,
            onClick = { actions.camera(!call.cameraOn) },
        )
        if (call.cameraOn && call.video?.sending == true) {
            VideoButton(
                icon = Icons.Rounded.Cameraswitch,
                label = stringResource(R.string.call_switch_camera),
                enabled = live,
                onClick = { onIntent(CallIntent.SwitchCamera) },
            )
        }
        RouteControl(call, enabled = live, onIntent = onIntent, compact = true)
        MoreControl(state, onIntent, enabled = live)
    }
    val hangUp: @Composable () -> Unit = {
        val label = stringResource(R.string.call_end)
        FilledIconButton(
            onClick = { onIntent(CallIntent.HangUp) },
            enabled = live,
            shapes = IconButtonDefaults.shapes(),
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            modifier =
                if (vertical) Modifier.size(width = ControlSize, height = HangUpLength)
                else Modifier.size(width = HangUpLength, height = ControlSize),
        ) {
            Icon(Icons.Rounded.CallEnd, contentDescription = label)
        }
    }
    val surface =
        Modifier.frosted(
                behind,
                MaterialTheme.colorScheme.surfaceContainer,
                MaterialTheme.shapes.extraLarge,
            )
            .padding(8.dp)
    if (vertical) {
        Column(
            modifier = surface,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                buttons()
            }
            hangUp()
        }
    } else {
        Column(
            modifier = surface,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                buttons()
            }
            hangUp()
        }
    }
}

@Composable
private fun MoreControl(state: CallUiState, onIntent: (CallIntent) -> Unit, enabled: Boolean) {
    val call = state.call ?: return
    val held = call.state == CallState.OnHold
    Box {
        VideoButton(
            icon = Icons.Rounded.MoreVert,
            label = stringResource(R.string.call_more),
            enabled = enabled,
            onClick = { onIntent(CallIntent.ToggleMore) },
        )
        DropdownMenu(
            expanded = state.moreOpen,
            onDismissRequest = { onIntent(CallIntent.DismissMore) },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.call_statistics)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ShowChart, null) },
                onClick = { onIntent(CallIntent.ToggleStatistics) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.call_keypad)) },
                leadingIcon = { Icon(Icons.Rounded.Dialpad, null) },
                onClick = { onIntent(CallIntent.ToggleKeypad) },
            )
            DropdownMenuItem(
                text = {
                    Text(stringResource(if (held) R.string.call_resume else R.string.call_hold))
                },
                leadingIcon = {
                    Icon(if (held) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, null)
                },
                enabled = call.state == CallState.Active || held,
                onClick = { onIntent(CallIntent.ToggleHold) },
            )
        }
    }
}

@Composable
internal fun VideoToggle(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilledTonalIconToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        enabled = enabled,
        shapes = IconButtonDefaults.toggleableShapes(),
        modifier = Modifier.size(ControlSize),
    ) {
        Icon(icon, contentDescription = label)
    }
}

@Composable
internal fun VideoButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        shapes = IconButtonDefaults.shapes(),
        modifier = Modifier.size(ControlSize),
    ) {
        Icon(icon, contentDescription = label)
    }
}

/** A window this wide puts the panels beside the picture. */
internal val WideWidth = 600.dp
private val PanelWidth = 380.dp
/** A phone's panel takes at most this share of the height: the call stays in sight above. */
internal const val PanelHeightShare = 0.62f
private val BannerMaxWidth = 560.dp
private val ControlSize = 56.dp
private val HangUpLength = 96.dp
private val TopReserve = 72.dp
private val BarReserve = 160.dp
private val RailReserve = 88.dp
