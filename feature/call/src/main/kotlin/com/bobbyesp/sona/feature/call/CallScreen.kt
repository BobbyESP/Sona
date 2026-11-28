/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.call

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.sona.calling.domain.Call
import com.bobbyesp.sona.calling.domain.CallState
import com.bobbyesp.sona.calling.domain.EndReason
import com.bobbyesp.sona.calling.ui.detail
import com.bobbyesp.sona.calling.ui.text
import com.bobbyesp.sona.core.designsystem.blur.BlurSource
import com.bobbyesp.sona.core.designsystem.blur.blurSource
import com.bobbyesp.sona.core.designsystem.blur.frosted
import com.bobbyesp.sona.core.designsystem.blur.rememberBlurSource
import com.bobbyesp.sona.core.designsystem.components.Dialpad
import com.bobbyesp.sona.core.designsystem.theme.SonaAccentTheme
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme
import com.bobbyesp.sona.feature.call.video.PreviewBackdrop
import com.bobbyesp.sona.feature.call.video.QualityIndicator
import com.bobbyesp.sona.feature.call.video.RemoteVideoOnly
import com.bobbyesp.sona.feature.call.video.StatisticsPanel
import com.bobbyesp.sona.telecom.api.AudioRouteKind
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

/**
 * The call screen, hosted by the app's call activity over the lock screen. [onClosed] is called
 * when no call is left. In [pictureInPicture] it shows the other side's picture alone.
 */
@Composable
fun CallRoute(
    onClosed: () -> Unit,
    modifier: Modifier = Modifier,
    pictureInPicture: Boolean = false,
) {
    val viewModel: CallViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onClosed() }
    // Picture-in-Picture counts as shown: the activity is still started.
    LifecycleStartEffect(viewModel) {
        viewModel.onSendIntent(CallIntent.Shown(true))
        onStopOrDispose { viewModel.onSendIntent(CallIntent.Shown(false)) }
    }
    DisplayRotation { viewModel.onSendIntent(CallIntent.Rotation(it)) }
    CallScreen(
        state = state,
        onIntent = viewModel::onSendIntent,
        pictureInPicture = pictureInPicture,
        modifier = modifier,
    )
}

/**
 * What needs Android first: a permission asked at the moment it is needed, or the app's settings.
 * Each ends in an intent.
 */
@Stable
internal class CallActions(
    val answer: () -> Unit,
    val answerWithVideo: () -> Unit,
    val camera: (Boolean) -> Unit,
    val openAppSettings: () -> Unit,
)

@Composable
private fun rememberCallActions(onIntent: (CallIntent) -> Unit): CallActions {
    val context = LocalContext.current
    val send by rememberUpdatedState(onIntent)
    // A refusal still answers: a call heard one way beats one that rings on. After a refused camera
    // too, so the coordinator can say why it is off.
    val answerAsked =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            send(CallIntent.Answer)
        }
    val videoAnswerAsked =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            send(CallIntent.AnswerWithVideo)
        }
    val cameraAsked =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            send(CallIntent.Camera(true))
        }
    return remember(context) {
        CallActions(
            answer = {
                if (context.has(Manifest.permission.RECORD_AUDIO)) send(CallIntent.Answer)
                else answerAsked.launch(callPermissions())
            },
            answerWithVideo = {
                if (
                    context.has(Manifest.permission.RECORD_AUDIO) &&
                        context.has(Manifest.permission.CAMERA)
                )
                    send(CallIntent.AnswerWithVideo)
                else videoAnswerAsked.launch(videoCallPermissions())
            },
            camera = { on ->
                if (!on || context.has(Manifest.permission.CAMERA)) send(CallIntent.Camera(on))
                else cameraAsked.launch(Manifest.permission.CAMERA)
            },
            openAppSettings = {
                context.startActivity(
                    Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
    }
}

@Composable
internal fun CallScreen(
    state: CallUiState,
    onIntent: (CallIntent) -> Unit,
    modifier: Modifier = Modifier,
    pictureInPicture: Boolean = false,
) {
    val call = state.call ?: return
    val actions = rememberCallActions(onIntent)
    // Never during video: the screen is what the user looks at, even held near the face.
    ProximityLock(
        active =
            call.state == CallState.Active &&
                call.route?.kind == AudioRouteKind.Earpiece &&
                !call.isVideo,
    )
    KeepScreenOn(active = !call.isOver)

    SonaAccentTheme(accent = state.account?.color?.let(::Color)) {
        // The activity draws nothing below: without a surface, text and icons are black. Inside the
        // accent theme, to take its colors.
        Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            when {
                pictureInPicture ->
                    if (call.isVideo) {
                        RemoteVideoOnly(
                            call,
                            paused = state.videoPaused,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            CallerMark(call.title, size = 72.dp, ringing = false)
                        }
                    }
                call.isVideo -> VideoCallScreen(state, onIntent, actions)
                else -> AudioCallScreen(state, onIntent, actions)
            }
        }
    }
}

@Composable
private fun AudioCallScreen(
    state: CallUiState,
    onIntent: (CallIntent) -> Unit,
    actions: CallActions,
) {
    val call = state.call ?: return
    val context = LocalContext.current
    val ringing = call.state == CallState.Incoming
    val offersVideo = ringing && call.videoOffered && state.videoAvailable
    // This side's camera behind an incoming video call, only with the permission already granted.
    val backdrop = offersVideo && context.has(Manifest.permission.CAMERA)
    val behind = rememberBlurSource()
    val colors = MaterialTheme.colorScheme

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WideWidth
        Box(Modifier.fillMaxSize().blurSource(behind)) {
            if (backdrop) {
                PreviewBackdrop(call, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(colors.surface.copy(alpha = BackdropVeil)))
            }
            // Scrolls only when it does not fit (a large font, a phone lying down), so nothing is
            // cut.
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .heightIn(min = maxHeight)
                            .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Header(state, onIntent)
                        CallBanners(state, onIntent, actions, behind = null)
                    }
                    Box(Modifier.padding(vertical = 24.dp)) { Caller(call) }
                    AnimatedContent(
                        targetState = ringing,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "CallControls",
                    ) { isRinging ->
                        if (isRinging) {
                            IncomingActions(
                                otherInProgress = state.other != null,
                                offersVideo = offersVideo,
                                onAnswer = actions.answer,
                                onAnswerWithVideo = actions.answerWithVideo,
                                onDecline = { onIntent(CallIntent.Decline) },
                            )
                        } else {
                            InCallActions(state, onIntent, actions)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = state.statisticsOpen,
            modifier = Modifier.align(if (wide) Alignment.CenterEnd else Alignment.BottomCenter),
            enter =
                fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    if (wide) slideInHorizontally { it } else slideInVertically { it },
            exit =
                fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    if (wide) slideOutHorizontally { it } else slideOutVertically { it },
        ) {
            CallPanel(wide = wide, behind = behind, maxHeight = maxHeight * PanelHeightShare) {
                StatisticsPanel(
                    statistics = state.statistics,
                    history = state.history,
                    quality = state.quality,
                    measured = state.statistics != null || state.quality != null,
                    onClose = { onIntent(CallIntent.ToggleStatistics) },
                )
            }
        }
    }
}

@Composable
private fun Header(state: CallUiState, onIntent: (CallIntent) -> Unit) {
    val call = state.call
    val measured =
        call != null &&
            (call.state == CallState.Active ||
                call.state == CallState.OnHold ||
                call.state == CallState.HeldByRemote)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowRow(
            verticalArrangement = Arrangement.Center,
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            state.account?.let { account ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(account.color)))
                    Text(account.label, style = MaterialTheme.typography.labelLarge)
                    call?.media?.encryption?.let {
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = it,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            if (measured) {
                QualityIndicator(
                    quality = state.quality,
                    onClick = { onIntent(CallIntent.ToggleStatistics) },
                )
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

/**
 * One banner at a time, the most pressing first: camera blocked, weak connection, the other side's
 * video.
 *
 * Frosted over [behind] when given; solid otherwise.
 */
@Composable
internal fun CallBanners(
    state: CallUiState,
    onIntent: (CallIntent) -> Unit,
    actions: CallActions,
    behind: BlurSource?,
) {
    val call = state.call ?: return
    val blocked = call.cameraBlocked.takeIf { !call.isOver }
    val banner =
        when {
            blocked != null -> Banner.CameraBlocked
            state.weakConnection -> Banner.Weak
            state.remoteTurnedOnVideo -> Banner.RemoteVideo
            else -> null
        }
    val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = banner,
        transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
        label = "CallBanner",
    ) { shown ->
        when (shown) {
            null -> Unit
            Banner.CameraBlocked ->
                BannerCard(
                    icon = Icons.Rounded.VideocamOff,
                    text = blocked?.text().orEmpty(),
                    detail = blocked?.detail,
                    action = stringResource(R.string.call_open_settings),
                    onAction = actions.openAppSettings,
                    onDismiss = null,
                    behind = behind,
                )
            Banner.Weak ->
                BannerCard(
                    icon = Icons.Rounded.NetworkCheck,
                    text = stringResource(R.string.call_weak_connection),
                    detail = null,
                    action = stringResource(R.string.call_turn_off_video),
                    onAction = { onIntent(CallIntent.TurnOffVideo) },
                    onDismiss = { onIntent(CallIntent.DismissBanner) },
                    behind = behind,
                )
            Banner.RemoteVideo ->
                BannerCard(
                    icon = Icons.Rounded.Videocam,
                    text = stringResource(R.string.call_remote_turned_on_video, call.title),
                    detail = null,
                    action = stringResource(R.string.call_camera_on),
                    onAction = { actions.camera(true) },
                    onDismiss = { onIntent(CallIntent.DismissBanner) },
                    behind = behind,
                )
        }
    }
}

private enum class Banner {
    CameraBlocked,
    Weak,
    RemoteVideo,
}

@Composable
private fun BannerCard(
    icon: ImageVector,
    text: String,
    detail: String?,
    action: String,
    onAction: () -> Unit,
    onDismiss: (() -> Unit)?,
    behind: BlurSource?,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val surface =
        if (behind != null) Modifier.frosted(behind, colors.surfaceContainerHigh, shape)
        else Modifier.clip(shape).background(colors.secondaryContainer)
    val content = if (behind != null) colors.onSurface else colors.onSecondaryContainer
    CompositionLocalProvider(LocalContentColor provides content) {
        Row(
            modifier =
                Modifier.padding(top = 8.dp)
                    .then(surface)
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null)
            Column(Modifier.weight(1f, fill = false)) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                detail?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = SonaTheme.typography.monospace,
                        color = if (behind != null) colors.onSurfaceVariant else content,
                    )
                }
            }
            TextButton(onClick = onAction) { Text(action) }
            onDismiss?.let {
                IconButton(onClick = it) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.call_dismiss),
                    )
                }
            }
        }
    }
}

/** The other side: their mark on a Material shape that turns while it rings, name and status. */
@Composable
private fun Caller(call: Call) {
    val ringing = call.state == CallState.Incoming || call.state == CallState.RingingRemote
    val size by
        animateDpAsState(
            targetValue = if (call.state == CallState.Active || call.isOver) 128.dp else 168.dp,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "CallerSize",
        )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CallerMark(call.title, size = size, ringing = ringing, ended = call.isOver)
        Spacer(Modifier.size(24.dp))
        Text(
            call.title,
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
        )
        Text(
            call.remote.uri,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = SonaTheme.typography.monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(12.dp))
        Status(call)
    }
}

/**
 * The first letter of [title] on a Material shape, animated while [ringing]. The animation is read
 * while drawing, so a frame never recomposes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CallerMark(title: String, size: Dp, ringing: Boolean, ended: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "CallerShape")
    val rotation =
        transition.animateFloat(
            0f,
            360f,
            infiniteRepeatable(tween(12_000, easing = LinearEasing)),
            label = "Rotation",
        )
    val breath =
        transition.animateFloat(
            0.94f,
            1.04f,
            infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "Breath",
        )
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size)
                .graphicsLayer {
                    if (ringing) {
                        rotationZ = rotation.value
                        scaleX = breath.value
                        scaleY = breath.value
                    }
                }
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(if (ended) colors.surfaceContainerHighest else colors.primaryContainer),
        )
        Text(
            title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "#",
            style =
                if (size < 96.dp) MaterialTheme.typography.headlineMedium
                else MaterialTheme.typography.displayMedium,
            color = colors.onPrimaryContainer,
        )
    }
}

/** What is happening, in words; `null` while the call is up (the timer says it). */
@Composable
internal fun Call.statusWords(): String? =
    when (val state = state) {
        CallState.Dialing -> stringResource(R.string.call_calling)
        CallState.RingingRemote -> stringResource(R.string.call_ringing)
        CallState.Incoming ->
            stringResource(
                if (videoOffered) R.string.call_incoming_video else R.string.call_incoming,
            )
        CallState.OnHold -> stringResource(R.string.call_on_hold)
        CallState.HeldByRemote -> stringResource(R.string.call_held_by, title)
        CallState.Active -> null
        is CallState.Ended -> state.reason.sentence(remote.user ?: title)
    }

/** What is happening, in words: spoken by TalkBack as it changes (the timer is not). */
@Composable
private fun Status(call: Call) {
    val colors = MaterialTheme.colorScheme
    val ended = call.state as? CallState.Ended
    val text = call.statusWords()
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
internal fun Timer(connectedAt: Long?, style: TextStyle = MaterialTheme.typography.titleLarge) {
    val elapsed by
        produceState(0L, connectedAt) {
            while (connectedAt != null) {
                value = (System.currentTimeMillis() - connectedAt) / 1000
                delay(1000.milliseconds)
            }
        }
    Text(
        "%d:%02d".format(elapsed / 60, elapsed % 60),
        style = style,
        fontFamily = SonaTheme.typography.monospace,
    )
}

/**
 * Answer and decline; for a call that offers video, *Answer with video* beside *Answer audio only*
 * (the other side is still seen, this side's camera stays off).
 */
@Composable
private fun IncomingActions(
    otherInProgress: Boolean,
    offersVideo: Boolean,
    onAnswer: () -> Unit,
    onAnswerWithVideo: () -> Unit,
    onDecline: () -> Unit,
) {
    val size =
        if (offersVideo) IconButtonDefaults.largeContainerSize()
        else IconButtonDefaults.extraLargeContainerSize()
    Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top,
    ) {
        BigAction(
            icon = Icons.Rounded.CallEnd,
            label = stringResource(R.string.call_decline),
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
            size = size,
            onClick = onDecline,
        )
        if (offersVideo) {
            BigAction(
                icon = Icons.Rounded.Call,
                label = stringResource(R.string.call_answer_audio_only),
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
                size = size,
                onClick = onAnswer,
            )
            BigAction(
                icon = Icons.Rounded.Videocam,
                label = stringResource(R.string.call_answer_video),
                container = MaterialTheme.colorScheme.primary,
                content = MaterialTheme.colorScheme.onPrimary,
                size = size,
                onClick = onAnswerWithVideo,
            )
        } else {
            BigAction(
                icon = Icons.Rounded.Call,
                label =
                    stringResource(
                        if (otherInProgress) R.string.call_hold_and_answer
                        else R.string.call_answer,
                    ),
                container = MaterialTheme.colorScheme.primary,
                content = MaterialTheme.colorScheme.onPrimary,
                size = size,
                onClick = onAnswer,
            )
        }
    }
}

@Composable
private fun BigAction(
    icon: ImageVector,
    label: String,
    container: Color,
    content: Color,
    size: androidx.compose.ui.unit.DpSize,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.widthIn(max = size.width + 24.dp),
    ) {
        FilledIconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = container,
                    contentColor = content,
                ),
            modifier = Modifier.size(size),
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(IconButtonDefaults.largeIconSize),
            )
        }
        Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InCallActions(
    state: CallUiState,
    onIntent: (CallIntent) -> Unit,
    actions: CallActions,
) {
    val call = state.call ?: return
    val live = !call.isOver
    val offersVideo = state.videoAvailable && live && call.state == CallState.Active
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
            Dialpad(
                onKey = { onIntent(CallIntent.Dtmf(it)) },
                tones = state.dialpadTones,
                keyAspectRatio = 1.9f,
            )
            Spacer(Modifier.size(16.dp))
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = if (offersVideo) 3 else 4,
        ) {
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
            if (offersVideo) {
                CallControl(
                    icon = Icons.Rounded.Videocam,
                    label = stringResource(R.string.call_video),
                    checked = false,
                    enabled = true,
                    onClick = { actions.camera(true) },
                )
            }
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
 * Bluetooth) it opens the list, each by the name the system gives it. [compact] draws it as the
 * video bar's icon button.
 */
@Composable
internal fun RouteControl(
    call: Call,
    enabled: Boolean,
    onIntent: (CallIntent) -> Unit,
    compact: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    val route = call.route
    val speaker = call.routes.firstOrNull { it.kind == AudioRouteKind.Speaker }
    val earpiece = call.routes.firstOrNull { it.kind == AudioRouteKind.Earpiece }
    val label =
        route?.takeIf { call.routes.size > 2 }?.name ?: stringResource(R.string.call_speaker)
    val checked = route?.kind == AudioRouteKind.Speaker
    val usable = enabled && call.routes.size > 1
    val onClick: () -> Unit = {
        if (call.routes.size > 2) open = true
        else
            (if (route?.kind == AudioRouteKind.Speaker) earpiece else speaker)?.let {
                onIntent(CallIntent.Route(it))
            }
    }
    Box {
        if (compact) {
            VideoToggle(route?.kind.icon(), label, checked, usable, onClick)
        } else {
            CallControl(route?.kind.icon(), label, checked, usable, onClick)
        }
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
        AudioRouteKind.Speaker -> Icons.AutoMirrored.Rounded.VolumeUp
        AudioRouteKind.Bluetooth -> Icons.Rounded.Bluetooth
        AudioRouteKind.WiredHeadset -> Icons.Rounded.Headphones
        else -> Icons.Rounded.PhoneInTalk
    }

/** A toggle whose shape squares when on, so its state reads from afar. */
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
        modifier = Modifier.width(88.dp),
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
            textAlign = TextAlign.Center,
            maxLines = 2,
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

/**
 * Keeps the screen on while a call is shown: its controls stay one touch away, and the phone does
 * not lock under a call on the speaker. At the ear the proximity sensor still turns it off. Found
 * on a phone, which slept and locked thirty seconds into a call.
 */
@Composable
private fun KeepScreenOn(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * Reports the display's rotation in degrees. It takes a [DisplayManager] listener: a half turn (90
 * to 270) changes no configuration.
 */
@Composable
private fun DisplayRotation(onRotation: (Int) -> Unit) {
    val view = LocalView.current
    val report by rememberUpdatedState(onRotation)
    DisposableEffect(view) {
        val displays = view.context.getSystemService(DisplayManager::class.java)
        fun current() {
            val display = view.display ?: return
            report(display.rotation * QUARTER_TURN)
        }
        val listener =
            object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) = Unit

                override fun onDisplayRemoved(displayId: Int) = Unit

                override fun onDisplayChanged(displayId: Int) {
                    if (displayId == view.display?.displayId) current()
                }
            }
        displays.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        current()
        onDispose { displays.unregisterDisplayListener(listener) }
    }
}

private const val QUARTER_TURN = 90

/** Opacity of the surface over the preview behind an incoming video call. */
private const val BackdropVeil = 0.6f

/** An ended call's reason in a sentence; [who] is the other side's extension or name. */
@Composable
private fun EndReason.sentence(who: String): String =
    when (this) {
        is EndReason.HungUp -> stringResource(R.string.end_hung_up)
        EndReason.Missed -> stringResource(R.string.end_missed)
        EndReason.Cancelled -> stringResource(R.string.end_cancelled)
        EndReason.EndedAtOnce -> stringResource(R.string.end_at_once, who)
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

private fun Context.has(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

/** What a call needs: the microphone, and on Android 13+ notifications to ring and show it. */
fun callPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    else arrayOf(Manifest.permission.RECORD_AUDIO)

internal fun videoCallPermissions(): Array<String> = callPermissions() + Manifest.permission.CAMERA
