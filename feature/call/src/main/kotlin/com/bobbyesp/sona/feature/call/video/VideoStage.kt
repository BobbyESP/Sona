/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.call.video

import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.bobbyesp.sona.calling.domain.Call
import com.bobbyesp.sona.feature.call.CallerMark
import com.bobbyesp.sona.feature.call.R
import com.bobbyesp.sona.feature.call.statusWords
import com.bobbyesp.sona.sip.api.CameraFacing
import com.bobbyesp.sona.sip.api.VideoSize
import com.bobbyesp.sona.sip.api.VideoSurfaces
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * The other side's picture fills the stage; this side's camera is a tile, dragged to snap to a
 * corner and tapped to swap the two. The [TextureView]s go to [VideoSurfaces] while on screen.
 *
 * A swap only changes modifiers, so no view is rebuilt and the engine keeps drawing on the same
 * window.
 *
 * @param fill Whether the big picture covers the stage (cropped) or fits in it whole.
 * @param swapped Whether this side's camera is the big picture.
 * @param tileInsets What the tile keeps clear of: the system bars, and the controls.
 * @param showTile `false` shows the other side alone.
 */
@Composable
internal fun VideoStage(
    call: Call,
    paused: Boolean,
    fill: Boolean,
    swapped: Boolean,
    corner: Corner,
    onCorner: (Corner) -> Unit,
    onSwap: () -> Unit,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    tileInsets: PaddingValues,
    modifier: Modifier = Modifier,
    showTile: Boolean = true,
) {
    val surfaces: VideoSurfaces = koinInject()
    // Not keyed by the call: a view outlives a change of the call shown, and is handed to the next.
    var remoteView by remember { mutableStateOf<TextureView?>(null) }
    var previewView by remember { mutableStateOf<TextureView?>(null) }
    DisposableEffect(surfaces, call.id, remoteView, previewView) {
        surfaces.attach(call.id, remoteView, previewView)
        onDispose { surfaces.attach(call.id, null, null) }
    }

    val video = call.video
    val sending = video?.sending == true
    val tap by rememberUpdatedState(onTap)
    val doubleTap by rememberUpdatedState(onDoubleTap)
    val swap by rememberUpdatedState(onSwap)
    val snap by rememberUpdatedState(onCorner)
    val colors = MaterialTheme.colorScheme

    BoxWithConstraints(
        modifier.background(colors.scrim).pointerInput(Unit) {
            detectTapGestures(onTap = { tap() }, onDoubleTap = { doubleTap() })
        },
    ) {
        val density = LocalDensity.current
        val direction = LocalLayoutDirection.current
        val rtl = direction == LayoutDirection.Rtl
        val stage = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        val insets =
            with(density) {
                Insets(
                    left = tileInsets.calculateLeftPadding(direction).toPx(),
                    top = tileInsets.calculateTopPadding().toPx(),
                    right = tileInsets.calculateRightPadding(direction).toPx(),
                    bottom = tileInsets.calculateBottomPadding().toPx(),
                )
            }
        val tileSize = with(density) { tileSize(stage, TileShortSide.toPx(), TileLongSide.toPx()) }
        val area =
            Size(
                (stage.width - insets.left - insets.right).coerceAtLeast(tileSize.width),
                (stage.height - insets.top - insets.bottom).coerceAtLeast(tileSize.height),
            )
        val inset = Offset(insets.left, insets.top)
        val margin = with(density) { TileMargin.toPx() }
        val position = remember { Animatable(Offset.Unspecified, Offset.VectorConverter) }
        val scope = rememberCoroutineScope()
        val spring = MaterialTheme.motionScheme.defaultSpatialSpec<Offset>()
        val restingAt = inset + cornerOffset(corner, tileSize, area, margin, rtl)
        LaunchedEffect(restingAt) {
            // A tile let go is already on its way there, with the fling's speed: left to it.
            if (position.value == Offset.Unspecified) position.snapTo(restingAt)
            else if (position.targetValue != restingAt) position.animateTo(restingAt, spring)
        }

        val tileShape = MaterialTheme.shapes.large
        val swapLabel =
            stringResource(
                if (swapped) R.string.call_their_video else R.string.call_your_camera,
                call.title,
            )
        val tile =
            Modifier.zIndex(1f)
                .offset {
                    val at = position.value.takeIf { it != Offset.Unspecified } ?: restingAt
                    IntOffset(at.x.roundToInt(), at.y.roundToInt())
                }
                .size(
                    with(density) { tileSize.width.toDp() },
                    with(density) { tileSize.height.toDp() },
                )
                .clip(tileShape)
                .background(colors.surfaceContainerHighest)
                .semantics {
                    role = Role.Button
                    contentDescription = swapLabel
                    onClick {
                        swap()
                        true
                    }
                }
                .pointerInput(tileSize, area, rtl) {
                    val tracker = VelocityTracker()
                    fun settle() {
                        val velocity = tracker.calculateVelocity()
                        val at = position.value - inset
                        val snapped =
                            nearestCorner(at, tileSize, area, Offset(velocity.x, velocity.y), rtl)
                        snap(snapped)
                        scope.launch {
                            position.animateTo(
                                inset + cornerOffset(snapped, tileSize, area, margin, rtl),
                                spring,
                                initialVelocity = Offset(velocity.x, velocity.y),
                            )
                        }
                    }
                    detectDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onDragEnd = ::settle,
                        onDragCancel = ::settle,
                    ) { change, amount ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, position.value + change.position)
                        scope.launch { position.snapTo(position.value + amount) }
                    }
                }
                .pointerInput(Unit) { detectTapGestures(onTap = { swap() }) }
        val big = Modifier.fillMaxSize()
        val remoteInTile = swapped && showTile

        // The other side's picture: always composed, so its window stays the engine's.
        Box((if (remoteInTile) tile else big).clipToBounds()) {
            AspectVideo(video?.receivedSize, fill = fill || remoteInTile) {
                AndroidView(
                    factory = { context -> TextureView(context).also { remoteView = it } },
                    onRelease = { if (remoteView === it) remoteView = null },
                )
            }
            if (!call.showsRemoteVideo) {
                Placeholder(
                    call = call,
                    text =
                        call.statusWords()
                            ?: stringResource(R.string.call_no_video_from, call.title),
                    small = remoteInTile,
                )
            }
            AnimatedVisibility(
                visible = paused && call.showsRemoteVideo,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
                modifier = Modifier.matchParentSize(),
            ) {
                Paused(small = remoteInTile)
            }
        }

        if (showTile && video != null) {
            val localBig = swapped
            Box((if (localBig) big else tile).clipToBounds()) {
                if (sending) {
                    val mirrored = video?.camera == CameraFacing.Front
                    AspectVideo(video?.sentSize, fill = fill || !localBig) {
                        AndroidView(
                            factory = { context -> TextureView(context).also { previewView = it } },
                            onRelease = { if (previewView === it) previewView = null },
                            // The front camera as in a mirror: on screen only, never as sent.
                            update = { it.scaleX = if (mirrored) -1f else 1f },
                        )
                    }
                } else {
                    Placeholder(
                        call = null,
                        text = stringResource(R.string.call_camera_is_off),
                        small = !localBig,
                    )
                }
            }
        }
    }
}

/** The other side's picture alone, for Picture-in-Picture. */
@Composable
internal fun RemoteVideoOnly(call: Call, paused: Boolean, modifier: Modifier = Modifier) {
    VideoStage(
        call = call,
        paused = paused,
        fill = true,
        swapped = false,
        corner = Corner.BottomEnd,
        onCorner = {},
        onSwap = {},
        onTap = {},
        onDoubleTap = {},
        tileInsets = PaddingValues(),
        modifier = modifier,
        showTile = false,
    )
}

/**
 * This side's camera, mirrored, behind a ringing video call. The engine runs the preview while the
 * view is attached, sending nothing (docs/video.md).
 */
@Composable
internal fun PreviewBackdrop(call: Call, modifier: Modifier = Modifier) {
    val surfaces: VideoSurfaces = koinInject()
    var previewView by remember { mutableStateOf<TextureView?>(null) }
    DisposableEffect(surfaces, call.id, previewView) {
        surfaces.attach(call.id, null, previewView)
        onDispose { surfaces.attach(call.id, null, null) }
    }
    Box(modifier.background(MaterialTheme.colorScheme.scrim).clipToBounds()) {
        AspectVideo(call.video?.sentSize, fill = true) {
            AndroidView(
                factory = { context -> TextureView(context).also { previewView = it } },
                onRelease = { if (previewView === it) previewView = null },
                update = { it.scaleX = -1f },
            )
        }
    }
}

/**
 * Centres its child at [video]'s aspect: whole, or covering the space when [fill] (the parent
 * clips). An unknown size takes the space.
 */
@Composable
internal fun AspectVideo(
    video: VideoSize?,
    fill: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier.fillMaxSize()) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val frame = videoFrame(Size(width.toFloat(), height.toFloat()), video, fill)
        val w = frame.width.roundToInt().coerceAtLeast(1)
        val h = frame.height.roundToInt().coerceAtLeast(1)
        val placeables = measurables.map { it.measure(Constraints.fixed(w, h)) }
        layout(width, height) { placeables.forEach { it.place((width - w) / 2, (height - h) / 2) } }
    }
}

@Composable
private fun Placeholder(call: Call?, text: String, small: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxSize().background(colors.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (small) {
            Icon(
                Icons.Rounded.VideocamOff,
                contentDescription = text,
                tint = colors.onSurfaceVariant,
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                if (call != null) CallerMark(call.title, size = 128.dp, ringing = false)
                else
                    Icon(
                        Icons.Rounded.VideocamOff,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                Text(
                    text,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Paused(small: Boolean) {
    val colors = MaterialTheme.colorScheme
    val text = stringResource(R.string.call_video_paused)
    Box(
        Modifier.fillMaxSize().background(colors.scrim.copy(alpha = PausedDim)),
        contentAlignment = Alignment.Center,
    ) {
        // The scrim is black in both themes: no one text color of the scheme reads on it in both.
        Surface(shape = CircleShape, color = colors.surfaceContainerHigh) {
            Row(
                modifier = Modifier.padding(if (small) 8.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.PauseCircle, contentDescription = if (small) text else null)
                if (!small) Text(text, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private data class Insets(val left: Float, val top: Float, val right: Float, val bottom: Float)

private fun tileSize(stage: Size, short: Float, long: Float): Size =
    if (stage.width >= stage.height) Size(long, short) else Size(short, long)

private val TileShortSide = 104.dp
private val TileLongSide = 148.dp
private val TileMargin = 12.dp
private const val PausedDim = 0.55f
