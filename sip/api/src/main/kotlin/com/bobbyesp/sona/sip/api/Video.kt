/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.api

import com.bobbyesp.sona.core.model.CallId

/** Which of the phone's cameras a call sends. */
enum class CameraFacing {
    Front,
    Back,
}

/** A picture's size in pixels, as the encoder or the decoder has it. */
data class VideoSize(val width: Int, val height: Int) {
    val isLandscape: Boolean
        get() = width > height

    /** As people read it: `1280×720`. */
    override fun toString(): String = "$width×$height"

    companion object {
        val Hd1080 = VideoSize(1920, 1080)
        val Hd720 = VideoSize(1280, 720)
        val Vga = VideoSize(640, 480)
        val Qvga = VideoSize(320, 240)
    }
}

/**
 * The video of a call, as negotiated: a second stream of the same call, never a second kind of
 * call. [EngineCall.video] is `null` while a call has no video stream at all (audio only, or both
 * directions turned off).
 *
 * The directions are this side's and the other side's, as each said in its SDP: this side may
 * [receive] what the remote [remoteSending] sends, and the remote sees this side's camera while
 * [sending]. A picture arrives only when both of the first two hold.
 *
 * @property sending This side sends its camera (`sendrecv`, `sendonly`): the camera is on.
 * @property receiving This side takes the other side's video (`sendrecv`, `recvonly`).
 * @property remoteSending The other side says it sends (its `sendrecv` or `sendonly`).
 * @property camera Which camera is sent, or would be once [sending].
 * @property sentSize, [receivedSize] As the encoder and the decoder last reported them, for the
 *   screen's aspect ratio and Picture-in-Picture; `null` until known. They are refreshed with the
 *   engine's own reports, every few seconds at most; [SipEngine.statistics] has them each second.
 * @property codec The video codec's SDP name (`VP8`), once negotiated.
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
    /** The other side's picture can arrive: it sends, and this side takes it. */
    val showsRemote: Boolean
        get() = receiving && remoteSending

    /** Something is on screen: this side's camera, or the other side's picture. */
    val isActive: Boolean
        get() = sending || showsRemote
}

/**
 * Where a call's video is drawn: the other side's picture and this side's preview. A port of its
 * own, beside [SipEngine], because it is the only part of a call that holds views.
 *
 * On Android both handles are `android.view.TextureView`s. They travel as `Any` so this module
 * stays plain Kotlin; an implementation that is given anything else ignores it. The full flavor
 * binds the Linphone engine's; the demo flavor binds a renderer that paints test patterns, so the
 * whole video screen runs without a camera or a server.
 *
 * It renders, it never changes a call: what is sent and received is the coordinator's decision.
 */
interface VideoSurfaces {
    /**
     * Draws [call]'s video on these views from now on: the other side's on [remote], this side's
     * camera on [preview]. `null` detaches one; the screen detaches both when it goes. Callable
     * from any thread, the main one included: the work happens on the engine's own.
     */
    fun attach(call: CallId, remote: Any?, preview: Any?)
}

/**
 * How calls carry video, built from *Settings › Video* and the network in use (Wi-Fi or mobile
 * data) by `EngineConfigBuilder`. Every field is a Linphone switch in `LinphoneConfigurer`; what a
 * call negotiates takes effect on the next call or re-INVITE.
 *
 * @property enabled Video at all. Off, the engine neither offers nor accepts it and opens no
 *   camera.
 * @property codecs The video codecs to offer by SDP name (`VP8`, `H264`, `H265`), first choice
 *   first; every other one is off. `null` leaves the engine's own choice.
 * @property definition The largest picture to send. Adaptive rate control may send less.
 * @property maxFrameRate The most frames per second to capture and send.
 * @property uploadKbps, [downloadKbps] The most to send, and the most the other side is asked to
 *   send, in kbit/s, audio included; `0` sets no limit.
 * @property adaptiveRate Lower the bitrate, then the frame rate, then the definition when the path
 *   is congested, and raise them again when it clears (Linphone's `advanced` algorithm, from RTCP).
 * @property retransmission Send again the packets the other side reports lost (RTCP NACK).
 * @property fec Forward error correction for video: a little more upload, fewer frozen frames.
 * @property lowBandwidth Linphone's low-bandwidth mode (longer packets, a 20 kbit/s voice) for the
 *   calls that have no video: the mode turns video off, so a video call never uses it.
 * @property acceptRemoteVideo Video the other side adds (or offers to a call answered without the
 *   camera) is accepted receive-only: the other side is seen, this side's camera stays off.
 * @property adaptiveJitter Jitter buffers that grow and shrink with the network, audio and video.
 * @property audioDscp, [videoDscp] The DSCP marks of the media packets: EF (46) for voice, AF41
 *   (34) for video, so routers that honour them keep voice first.
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
