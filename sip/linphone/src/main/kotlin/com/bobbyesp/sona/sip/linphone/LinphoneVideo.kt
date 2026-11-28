/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.linphone

import com.bobbyesp.sona.sip.api.CallVideo
import com.bobbyesp.sona.sip.api.CameraFacing
import com.bobbyesp.sona.sip.api.VideoSize
import org.linphone.core.Call.State as CallState
import org.linphone.core.CallParams
import org.linphone.core.MediaDirection
import org.linphone.core.VideoDefinition

/* Video in Linphone's and Sona's vocabularies, as pure functions. */

/** The SDP direction for what this side [send]s and [receive]s; `null`: no video stream at all. */
internal fun videoDirectionOf(send: Boolean, receive: Boolean): MediaDirection? =
    when {
        send && receive -> MediaDirection.SendRecv
        send -> MediaDirection.SendOnly
        receive -> MediaDirection.RecvOnly
        else -> null
    }

/**
 * The direction to answer an incoming call with; `null` answers without video.
 *
 * @param camera The user answered with the camera.
 * @param offered Without an offer, an answer cannot add video.
 * @param acceptRemote Offered video is received when the camera stays off.
 */
internal fun answerDirectionOf(
    enabled: Boolean,
    camera: Boolean,
    offered: Boolean,
    acceptRemote: Boolean,
): MediaDirection? =
    when {
        !enabled || !offered -> null
        camera -> MediaDirection.SendRecv
        acceptRemote -> MediaDirection.RecvOnly
        else -> null
    }

/** Whether the media are negotiated, so the current parameters say what was agreed. */
internal fun CallState.negotiated(): Boolean =
    when (this) {
        CallState.Idle,
        CallState.OutgoingInit,
        CallState.OutgoingProgress,
        CallState.OutgoingRinging,
        CallState.IncomingReceived,
        CallState.PushIncomingReceived,
        CallState.IncomingEarlyMedia -> false
        else -> true
    }

internal fun CallState.ringingHere(): Boolean =
    this == CallState.IncomingReceived ||
        this == CallState.PushIncomingReceived ||
        this == CallState.IncomingEarlyMedia

/**
 * [local] are the current parameters once negotiated, the asked ones before; [remote] is `null`
 * before an answer. `null` without a video stream.
 */
internal fun callVideoOf(local: CallParams, remote: CallParams?, camera: CameraFacing): CallVideo? {
    if (!local.isVideoEnabled) return null
    val direction = local.videoDirection
    val sending = direction == MediaDirection.SendRecv || direction == MediaDirection.SendOnly
    val receiving = direction == MediaDirection.SendRecv || direction == MediaDirection.RecvOnly
    val remoteDirection = remote?.takeIf { it.isVideoEnabled }?.videoDirection
    val remoteSending =
        remoteDirection == MediaDirection.SendRecv || remoteDirection == MediaDirection.SendOnly
    return CallVideo(
        sending = sending,
        receiving = receiving,
        remoteSending = remoteSending,
        camera = camera,
        sentSize = local.sentVideoDefinition.toSize()?.takeIf { sending },
        receivedSize = local.receivedVideoDefinition.toSize()?.takeIf { receiving },
        codec = local.usedVideoPayloadType?.mimeType,
    )
}

/** The preview sends nothing; it stops without an attached view, so the camera is not kept open. */
internal fun previewRuns(attached: Boolean, callGoing: Boolean, cameraAllowed: Boolean): Boolean =
    attached && callGoing && cameraAllowed

/** `null` while Linphone has no size: undefined, or 0×0. */
internal fun VideoDefinition?.toSize(): VideoSize? {
    if (this == null || isUndefined || width <= 0 || height <= 0) return null
    return VideoSize(width, height)
}

/**
 * From the names Linphone gives cameras on Android (`… Front`, `… Back`), as its own app does;
 * `null` when the name does not say.
 */
internal fun cameraFacingOf(device: String?): CameraFacing? {
    if (device == null || device.isStaticImage()) return null
    return when {
        device.contains("front", ignoreCase = true) -> CameraFacing.Front
        device.contains("back", ignoreCase = true) || device.contains("rear", ignoreCase = true) ->
            CameraFacing.Back
        else -> null
    }
}

/** The first of [devices] facing [facing]; the first real camera when none says; `null` if none. */
internal fun cameraFor(devices: List<String>, facing: CameraFacing): String? {
    val cameras = devices.filterNot { it.isStaticImage() }
    return cameras.firstOrNull { cameraFacingOf(it) == facing } ?: cameras.firstOrNull()
}

/**
 * The camera to switch to from [current]: one facing the other way, or else the next real camera in
 * the list. `null` when the phone has no other.
 */
internal fun otherCamera(devices: List<String>, current: String?): String? {
    val cameras = devices.filterNot { it.isStaticImage() }
    val facing = cameraFacingOf(current)
    if (facing != null) {
        val opposite = if (facing == CameraFacing.Front) CameraFacing.Back else CameraFacing.Front
        cameras
            .firstOrNull { cameraFacingOf(it) == opposite }
            ?.let {
                return it
            }
    }
    val others = cameras.filterNot { it == current }
    if (others.isEmpty()) return null
    val at = cameras.indexOf(current)
    return if (at < 0) others.first() else cameras[(at + 1) % cameras.size]
}

/** Linphone's stand-in camera, a still picture sent when no camera can be opened. */
private fun String.isStaticImage(): Boolean = startsWith("StaticImage", ignoreCase = true)
