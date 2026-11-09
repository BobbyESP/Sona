/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.call

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.sona.calling.domain.Call
import com.bobbyesp.sona.calling.domain.CallState
import com.bobbyesp.sona.calling.domain.EndReason
import com.bobbyesp.sona.core.designsystem.components.Dialpad
import com.bobbyesp.sona.core.designsystem.theme.SonaAccentTheme
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme
import com.bobbyesp.sona.telecom.api.AudioRouteKind
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

/**
 * The call screen, hosted by the app's call activity over the lock screen. [onClosed] is called
 * when no call is left.
 */
@Composable
fun CallRoute(onClosed: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: CallViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onClosed() }
    CallScreen(state = state, onIntent = viewModel::onSendIntent, modifier = modifier)
}

@Composable
internal fun CallScreen(
    state: CallUiState,
    onIntent: (CallIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val call = state.call ?: return
    // Answering needs the microphone; asked for at the moment it is needed. A refusal still
    // answers: a call heard one way is better than one that rings on.
    val permissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            onIntent(CallIntent.Answer)
        }
    val context = LocalContext.current
    val answer = {
        if (context.hasMicrophone()) onIntent(CallIntent.Answer)
        else permissions.launch(callPermissions())
    }
    ProximityLock(
        active = call.state == CallState.Active && call.route?.kind == AudioRouteKind.Earpiece,
    )

    SonaAccentTheme(accent = state.account?.color?.let(::Color)) {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Header(state)
                Spacer(Modifier.weight(0.6f))
                Caller(call)
                Spacer(Modifier.weight(1f))
                AnimatedContent(
                    targetState = call.state == CallState.Incoming,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "CallControls",
                ) { ringing ->
                    if (ringing) {
                        IncomingActions(
                            otherInProgress = state.other != null,
                            onAnswer = answer,
                            onDecline = { onIntent(CallIntent.Decline) },
                        )
                    } else {
                        InCallActions(state, onIntent)
                    }
                }
            }
        }
    }
}

/** The account the call is on, and the other call waiting, if any. */
@Composable
private fun Header(state: CallUiState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.account?.let { account ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(account.color)))
                Text(account.label, style = MaterialTheme.typography.labelLarge)
                state.call?.media?.encryption?.let {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = it,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        state.other?.let { other ->
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    stringResource(R.string.call_other_on_hold, other.title),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** The other side: their mark on a Material shape that turns while it rings, name and status. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Caller(call: Call) {
    val colors = MaterialTheme.colorScheme
    val ringing = call.state == CallState.Incoming || call.state == CallState.RingingRemote
    val transition = rememberInfiniteTransition(label = "CallerShape")
    val rotation by
        transition.animateFloat(
            0f,
            360f,
            infiniteRepeatable(tween(12_000, easing = LinearEasing)),
            label = "Rotation",
        )
    val breath by
        transition.animateFloat(
            0.94f,
            1.04f,
            infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "Breath",
        )
    val size by
        animateDpAsState(
            targetValue = if (call.state == CallState.Active || call.isOver) 128.dp else 168.dp,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "CallerSize",
        )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(size)
                    .graphicsLayer {
                        if (ringing) {
                            rotationZ = rotation
                            scaleX = breath
                            scaleY = breath
                        }
                    }
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(
                        if (call.isOver) colors.surfaceContainerHighest
                        else colors.primaryContainer,
                    ),
            )
            Text(
                call.title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "#",
                style = MaterialTheme.typography.displayMedium,
                color = colors.onPrimaryContainer,
            )
        }
        Spacer(Modifier.size(24.dp))
        Text(
            call.title,
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            call.remote.uri,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = SonaTheme.typography.monospace,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.size(12.dp))
        Status(call)
    }
}

/** What is happening, in words: spoken by TalkBack as it changes (the timer is not). */
@Composable
private fun Status(call: Call) {
    val colors = MaterialTheme.colorScheme
    val ended = call.state as? CallState.Ended
    val text =
        when (val state = call.state) {
            CallState.Dialing -> stringResource(R.string.call_calling)
            CallState.RingingRemote -> stringResource(R.string.call_ringing)
            CallState.Incoming -> stringResource(R.string.call_incoming)
            CallState.OnHold -> stringResource(R.string.call_on_hold)
            CallState.HeldByRemote -> stringResource(R.string.call_held_by, call.title)
            CallState.Active -> null
            is CallState.Ended -> state.reason.sentence(call.remote.user ?: call.title)
        }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (text != null) {
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                color =
                    if (ended != null && ended.reason.isFailure) colors.error else colors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        } else {
            Timer(call.connectedAt)
        }
        ended?.reason?.status?.let {
            Text(
                it.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = SonaTheme.typography.monospace,
                color = colors.onSurfaceVariant,
            )
        }
        call.media
            ?.takeIf { call.state == CallState.Active }
            ?.let {
                Text(
                    it.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
    }
}

@Composable
private fun Timer(connectedAt: Long?) {
    val elapsed by
        produceState(0L, connectedAt) {
            while (connectedAt != null) {
                value = (System.currentTimeMillis() - connectedAt) / 1000
                delay(1000)
            }
        }
    Text(
        "%d:%02d".format(elapsed / 60, elapsed % 60),
        style = MaterialTheme.typography.titleLarge,
        fontFamily = SonaTheme.typography.monospace,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IncomingActions(otherInProgress: Boolean, onAnswer: () -> Unit, onDecline: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        BigAction(
            icon = Icons.Rounded.CallEnd,
            label = stringResource(R.string.call_decline),
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
            onClick = onDecline,
        )
        BigAction(
            icon = Icons.Rounded.Call,
            label =
                stringResource(
                    if (otherInProgress) R.string.call_hold_and_answer else R.string.call_answer,
                ),
            container = MaterialTheme.colorScheme.primary,
            content = MaterialTheme.colorScheme.onPrimary,
            onClick = onAnswer,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BigAction(
    icon: ImageVector,
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledIconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = container,
                    contentColor = content,
                ),
            modifier = Modifier.size(IconButtonDefaults.extraLargeContainerSize()),
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(IconButtonDefaults.extraLargeIconSize),
            )
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun InCallActions(state: CallUiState, onIntent: (CallIntent) -> Unit) {
    val call = state.call ?: return
    val live = !call.isOver
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = 420.dp),
    ) {
        if (state.keypadOpen && live) {
            Text(
                state.dtmf,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = SonaTheme.typography.monospace,
                modifier = Modifier.heightIn(min = 40.dp),
            )
            Dialpad(onKey = { onIntent(CallIntent.Dtmf(it)) }, keyAspectRatio = 1.9f)
            Spacer(Modifier.size(16.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            CallControl(
                icon = if (call.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                label = stringResource(R.string.call_mute),
                checked = call.muted,
                enabled = live,
                onClick = { onIntent(CallIntent.ToggleMute) },
            )
            CallControl(
                icon = Icons.Rounded.Dialpad,
                label =
                    stringResource(
                        if (state.keypadOpen) R.string.call_hide_keypad else R.string.call_keypad,
                    ),
                checked = state.keypadOpen,
                enabled = live,
                onClick = { onIntent(CallIntent.ToggleKeypad) },
            )
            RouteControl(call, enabled = live, onIntent = onIntent)
            CallControl(
                icon = Icons.Rounded.Pause,
                label = stringResource(R.string.call_hold),
                checked = call.state == CallState.OnHold,
                enabled =
                    live && (call.state == CallState.Active || call.state == CallState.OnHold),
                onClick = { onIntent(CallIntent.ToggleHold) },
            )
        }
        Spacer(Modifier.size(24.dp))
        state.other?.let {
            Button(
                onClick = { onIntent(CallIntent.Swap) },
                colors = ButtonDefaults.filledTonalButtonColors(),
            ) {
                Icon(Icons.Rounded.PhoneInTalk, contentDescription = null)
                Text(it.title, modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.size(16.dp))
        }
        Button(
            onClick = { onIntent(CallIntent.HangUp) },
            enabled = live,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        ) {
            Icon(Icons.Rounded.CallEnd, contentDescription = null, modifier = Modifier.size(32.dp))
            Text(
                stringResource(R.string.call_end),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/**
 * Where the audio goes. With two routes (phone and speaker) it is a toggle; with more (a headset,
 * Bluetooth) it opens the list, each by the name the system gives it.
 */
@Composable
private fun RouteControl(call: Call, enabled: Boolean, onIntent: (CallIntent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val route = call.route
    val speaker = call.routes.firstOrNull { it.kind == AudioRouteKind.Speaker }
    val earpiece = call.routes.firstOrNull { it.kind == AudioRouteKind.Earpiece }
    Box {
        CallControl(
            icon = route?.kind.icon(),
            label =
                route?.takeIf { call.routes.size > 2 }?.name
                    ?: stringResource(R.string.call_speaker),
            checked = route?.kind == AudioRouteKind.Speaker,
            enabled = enabled && call.routes.size > 1,
            onClick = {
                if (call.routes.size > 2) open = true
                else
                    (if (route?.kind == AudioRouteKind.Speaker) earpiece else speaker)?.let {
                        onIntent(CallIntent.Route(it))
                    }
            },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            call.routes.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    leadingIcon = { Icon(option.kind.icon(), contentDescription = null) },
                    onClick = {
                        open = false
                        onIntent(CallIntent.Route(option))
                    },
                )
            }
        }
    }
}

private fun AudioRouteKind?.icon(): ImageVector =
    when (this) {
        AudioRouteKind.Speaker -> Icons.Rounded.VolumeUp
        AudioRouteKind.Bluetooth -> Icons.Rounded.Bluetooth
        AudioRouteKind.WiredHeadset -> Icons.Rounded.Headphones
        else -> Icons.Rounded.PhoneInTalk
    }

/** A control of the call: a toggle whose shape squares when on, so its state reads from afar. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CallControl(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.width(80.dp),
    ) {
        FilledTonalIconToggleButton(
            checked = checked,
            onCheckedChange = { onClick() },
            enabled = enabled,
            shapes = IconButtonDefaults.toggleableShapes(),
            modifier = Modifier.size(IconButtonDefaults.largeContainerSize()),
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(IconButtonDefaults.largeIconSize),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Turns the screen off when held to the ear, during an active call on the earpiece: never on the
 * speaker or a headset, where the screen is what the user looks at.
 */
@SuppressLint("WakelockTimeout")
@Composable
private fun ProximityLock(active: Boolean) {
    val context = LocalContext.current
    DisposableEffect(active) {
        val power = context.getSystemService(PowerManager::class.java)
        val lock =
            if (
                active &&
                    power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)
            )
                power
                    .newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "sona:proximity")
                    .apply { acquire() }
            else null
        onDispose { lock?.takeIf { it.isHeld }?.release() }
    }
}

/** An ended call's reason in a sentence; [who] is the other side's extension or name. */
@Composable
private fun EndReason.sentence(who: String): String =
    when (this) {
        is EndReason.HungUp -> stringResource(R.string.end_hung_up)
        EndReason.Missed -> stringResource(R.string.end_missed)
        EndReason.Cancelled -> stringResource(R.string.end_cancelled)
        is EndReason.Busy -> stringResource(R.string.end_busy, who)
        is EndReason.Declined -> stringResource(R.string.end_declined, who)
        is EndReason.Unavailable -> stringResource(R.string.end_unavailable, who)
        is EndReason.NotFound -> stringResource(R.string.end_not_found, who)
        is EndReason.NoAnswer -> stringResource(R.string.end_no_answer)
        is EndReason.Unauthorized -> stringResource(R.string.end_unauthorized)
        is EndReason.NoCommonMedia -> stringResource(R.string.end_no_common_media)
        is EndReason.ServerUnavailable -> stringResource(R.string.end_server_unavailable)
        is EndReason.MediaLost -> stringResource(R.string.end_media_lost)
        is EndReason.Failed -> stringResource(R.string.end_failed)
    }

private val EndReason.isFailure: Boolean
    get() = this !is EndReason.HungUp && this != EndReason.Cancelled && this != EndReason.Missed

private fun Context.hasMicrophone(): Boolean =
    checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

/** What a call needs: the microphone, and on Android 13+ notifications to ring and show it. */
fun callPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    else arrayOf(Manifest.permission.RECORD_AUDIO)
