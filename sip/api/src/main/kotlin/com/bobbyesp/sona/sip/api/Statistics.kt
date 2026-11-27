/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.api

/**
 * A call's numbers at one moment, from [SipEngine.statistics]: what the statistics panel shows and
 * what the quality indicator is worked out from. Rates are since the engine's last report, counts
 * since the call began.
 *
 * @property audio, [video] Each stream's, or `null` for a stream the call does not have.
 * @property transport How the call's SIP travels: the account's transport.
 * @property ice How ICE connected the media, or [IceState.NotUsed] without ICE.
 * @property ipFamily The other side's media address family, once known.
 * @property encryption `SRTP`, `ZRTP` or `DTLS`; `null` for media in clear.
 * @property srtpSuite The SRTP crypto suite in use, by its SDP name (`AES_CM_128_HMAC_SHA1_80`).
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
 * the other side reports, by RTCP, of what this side sends.
 *
 * @property codec, [clockRate] The SDP name and the clock rate of the payload in use.
 * @property uploadKbps, [downloadKbps] The bitrate sent and received, RTP and RTCP, in kbit/s.
 * @property packetsSent, [packetsReceived] RTP packets since the call began.
 * @property cumulativeLost RTP packets lost on the way here since the call began.
 * @property localLossPercent, [remoteLossPercent] The share of packets lost since the last report,
 *   0 to 100: here, and at the other side.
 * @property jitterMs, [remoteJitterMs] Interarrival jitter, here and at the other side.
 * @property roundTripMs The round trip, from RTCP; `null` until a report came back.
 * @property jitterBufferMs How much the jitter buffer holds back to play smoothly.
 * @property latePackets Packets that arrived too late to be played.
 * @property fecRepairedPackets Lost packets that FEC rebuilt.
 * @property estimatedDownloadKbps What the engine estimates the path down can carry; `null` when it
 *   has no estimate.
 * @property frames The pictures, for video only.
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

/** The pictures sent and received: definition and frame rate. A size is `null` until known. */
data class VideoFrames(
    val sentSize: VideoSize? = null,
    val receivedSize: VideoSize? = null,
    val sentFps: Float = 0f,
    val receivedFps: Float = 0f,
)

/** How ICE connected a call's media, when the call uses it. */
enum class IceState {
    /** No ICE in this call. */
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
