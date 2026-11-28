/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.api

/**
 * A call's numbers at one moment ([SipEngine.statistics]). Rates are since the engine's last
 * report, counts since the call began.
 *
 * @property audio `null` without an audio stream; likewise [video].
 * @property encryption `SRTP`, `ZRTP` or `DTLS`; `null` for media in clear.
 * @property srtpSuite The suite's SDP name (`AES_CM_128_HMAC_SHA1_80`).
 */
data class CallStatistics(
    val audio: StreamStatistics? = null,
    val video: StreamStatistics? = null,
    val transport: Transport? = null,
    val ice: IceState = IceState.NotUsed,
    val ipFamily: IpFamily? = null,
    val encryption: String? = null,
    val srtpSuite: String? = null,
)

/**
 * One stream's numbers. "Local" is what this side measures of what it receives; "remote" is what
 * the other side reports over RTCP of what it is sent. Bitrates are in kbit/s (RTP and RTCP),
 * losses in percent since the last report.
 *
 * @property roundTripMs From RTCP; `null` until a report came back.
 * @property estimatedDownloadKbps The engine's estimate of the path down; `null` without one.
 * @property frames Video only.
 */
data class StreamStatistics(
    val codec: String? = null,
    val clockRate: Int? = null,
    val uploadKbps: Float = 0f,
    val downloadKbps: Float = 0f,
    val packetsSent: Long = 0,
    val packetsReceived: Long = 0,
    val cumulativeLost: Long = 0,
    val localLossPercent: Float = 0f,
    val remoteLossPercent: Float = 0f,
    val jitterMs: Float = 0f,
    val remoteJitterMs: Float = 0f,
    val roundTripMs: Float? = null,
    val jitterBufferMs: Float = 0f,
    val latePackets: Long = 0,
    val fecRepairedPackets: Long = 0,
    val estimatedDownloadKbps: Float? = null,
    val frames: VideoFrames? = null,
)

/** A size is `null` until known. */
data class VideoFrames(
    val sentSize: VideoSize? = null,
    val receivedSize: VideoSize? = null,
    val sentFps: Float = 0f,
    val receivedFps: Float = 0f,
)

/** How ICE connected a call's media. */
enum class IceState {
    NotUsed,
    InProgress,
    Failed,

    /** Directly, address to address. */
    Host,

    /** Through the address a STUN server saw. */
    Reflexive,

    /** Through a TURN relay. */
    Relay,
}

enum class IpFamily {
    V4,
    V6,
}
