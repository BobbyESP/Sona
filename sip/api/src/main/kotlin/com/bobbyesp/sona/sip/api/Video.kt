/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.api

import com.bobbyesp.sona.core.model.CallId

enum class CameraFacing {
    Front,
    Back,
}

data class VideoSize(val width: Int, val height: Int) {
    val isLandscape: Boolean
        get() = width > height

    override fun toString(): String = "$width×$height"

    companion object {
        val Hd1080 = VideoSize(1920, 1080)
        val Hd720 = VideoSize(1280, 720)
        val Vga = VideoSize(640, 480)
        val Qvga = VideoSize(320, 240)
    }
}

/**
 * A call's video stream, as negotiated; [EngineCall.video] is `null` without one (audio only, or
 * both directions off). The other side's picture arrives only while this side is [receiving] and
 * the other is [remoteSending].
 *
 * @property sending This side's camera is on (`sendrecv`, `sendonly`).
 * @property receiving This side takes the other side's video (`sendrecv`, `recvonly`).
 * @property camera The camera sent, or to be sent.
 * @property sentSize As last reported by the engine, every few seconds at most, like
 *   [receivedSize]; `null` until known.
 * @property codec The SDP name (`VP8`), once negotiated.
 */
data class CallVideo(
    val sending: Boolean,
    val receiving: Boolean,
    val remoteSending: Boolean,
    val camera: CameraFacing = CameraFacing.Front,
    val sentSize: VideoSize? = null,
    val receivedSize: VideoSize? = null,
    val codec: String? = null,
) {
    val showsRemote: Boolean
        get() = receiving && remoteSending

    /** Something is on screen: this side's camera, or the other side's picture. */
    val isActive: Boolean
        get() = sending || showsRemote
}

/**
 * Where a call's video is drawn. Apart from [SipEngine] because it is the only part of a call that
 * holds views; it never changes a call.
 *
 * The views are `android.view.TextureView`s, typed `Any` to keep this module plain Kotlin; anything
 * else is ignored.
 */
interface VideoSurfaces {
    /**
     * Draws [call]'s video on [remote] and this side's camera on [preview]; `null` detaches.
     * Callable from any thread.
     */
    fun attach(call: CallId, remote: Any?, preview: Any?)
}

/**
 * How calls carry video, built by `EngineConfigBuilder` from *Settings › Video* and the network in
 * use. Changes take effect on the next call or re-INVITE.
 *
 * @property enabled Off, video is neither offered nor accepted, and no camera is opened.
 * @property codecs SDP names (`VP8`, `H264`, `H265`), first choice first; the others are off.
 *   `null` leaves the engine's choice.
 * @property definition The largest picture to send; adaptive rate control may send less.
 * @property uploadKbps In kbit/s, audio included; `0` sets no limit.
 * @property downloadKbps The most the other side is asked to send, counted like [uploadKbps].
 * @property adaptiveRate Under congestion, lowers the bitrate, then the frame rate, then the
 *   definition (Linphone's `advanced` algorithm).
 * @property retransmission Resends packets reported lost (RTCP NACK).
 * @property fec Forward error correction: a little more upload, fewer frozen frames.
 * @property lowBandwidth Linphone's low-bandwidth mode, only for calls without video: the mode
 *   turns video off.
 * @property acceptRemoteVideo Video the other side adds, or offers to a call answered without the
 *   camera, is received while this side's camera stays off.
 * @property audioDscp DSCP mark of voice packets; [videoDscp] is the video's.
 */
data class VideoConfig(
    val enabled: Boolean = true,
    val codecs: List<String>? = null,
    val definition: VideoSize = VideoSize.Hd720,
    val maxFrameRate: Int = 30,
    val uploadKbps: Int = 1500,
    val downloadKbps: Int = 0,
    val adaptiveRate: Boolean = true,
    val retransmission: Boolean = true,
    val fec: Boolean = true,
    val lowBandwidth: Boolean = false,
    val acceptRemoteVideo: Boolean = true,
    val adaptiveJitter: Boolean = true,
    val audioDscp: Int = DSCP_EF,
    val videoDscp: Int = DSCP_AF41,
) {
    companion object {
        /** Expedited forwarding: voice. */
        const val DSCP_EF = 46

        /** Assured forwarding, class 4, low drop: interactive video. */
        const val DSCP_AF41 = 34
    }
}
