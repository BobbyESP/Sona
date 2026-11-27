/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.linphone

import com.bobbyesp.sona.sip.api.CallStatistics
import com.bobbyesp.sona.sip.api.IceState
import com.bobbyesp.sona.sip.api.IpFamily
import com.bobbyesp.sona.sip.api.StreamStatistics
import com.bobbyesp.sona.sip.api.Transport
import com.bobbyesp.sona.sip.api.VideoFrames
import org.linphone.core.Address
import org.linphone.core.CallParams
import org.linphone.core.CallStats
import org.linphone.core.IceState as LinphoneIce
import org.linphone.core.MediaEncryption
import org.linphone.core.PayloadType
import org.linphone.core.SrtpSuite

/*
 * A call's statistics as Linphone keeps them, in Sona's words and units. Everything here is a pure
 * function, tested on the JVM against stand-ins of Linphone's interfaces.
 *
 * Linphone's names are its RTCP's: the "sender" loss rate and jitter are read from the report this
 * side sends (what it measured of what it receives: local), the "receiver" ones from the report it
 * received (what the other side measured of what this side sends: remote). Jitter and round trip
 * come in seconds; Sona shows milliseconds.
 */

/**
 * The statistics of a call with these current [params], from its streams' [audio] and [video] stats
 * (`null` for a stream it does not have). [transport] is the account's, which Linphone does not
 * keep per call.
 */
internal fun callStatisticsOf(
    params: CallParams,
    audio: CallStats?,
    video: CallStats?,
    transport: Transport?,
): CallStatistics {
    val audioStream = audio?.let { streamStatisticsOf(it, params.usedAudioPayloadType) }
    val videoStream =
        video
            ?.takeIf { params.isVideoEnabled }
            ?.let {
                streamStatisticsOf(it, params.usedVideoPayloadType)
                    .copy(
                        frames =
                            VideoFrames(
                                sentSize = params.sentVideoDefinition.toSize(),
                                receivedSize = params.receivedVideoDefinition.toSize(),
                                sentFps = params.sentFramerate.coerceAtLeast(0f),
                                receivedFps = params.receivedFramerate.coerceAtLeast(0f),
                            ),
                    )
            }
    val main = audio ?: video
    val encryption = params.mediaEncryption.toName()
    return CallStatistics(
        audio = audioStream,
        video = videoStream,
        transport = transport,
        ice = main?.iceState?.toSona() ?: IceState.NotUsed,
        ipFamily = main?.ipFamilyOfRemote?.toSona(),
        encryption = encryption,
        srtpSuite = main?.srtpSuite?.takeIf { encryption != null }?.sdpName(),
    )
}

/** One stream's numbers, in Sona's units. */
internal fun streamStatisticsOf(stats: CallStats, payload: PayloadType?): StreamStatistics =
    StreamStatistics(
        codec = payload?.mimeType,
        clockRate = payload?.clockRate?.takeIf { it > 0 },
        uploadKbps = (stats.uploadBandwidth + stats.rtcpUploadBandwidth).coerceAtLeast(0f),
        downloadKbps = (stats.downloadBandwidth + stats.rtcpDownloadBandwidth).coerceAtLeast(0f),
        packetsSent = stats.rtpPacketSent.coerceAtLeast(0),
        packetsReceived = stats.rtpPacketRecv.coerceAtLeast(0),
        // Duplicates make RTP's cumulative loss go below zero: nothing was lost then.
        cumulativeLost = stats.rtpCumPacketLoss.toLong().coerceAtLeast(0),
        localLossPercent = stats.senderLossRate.percent(),
        remoteLossPercent = stats.receiverLossRate.percent(),
        jitterMs = (stats.senderInterarrivalJitter * MS).coerceAtLeast(0f),
        remoteJitterMs = (stats.receiverInterarrivalJitter * MS).coerceAtLeast(0f),
        roundTripMs = stats.roundTripDelay.takeIf { it > 0f }?.let { it * MS },
        jitterBufferMs = stats.jitterBufferSizeMs.coerceAtLeast(0f),
        latePackets = stats.latePacketsCumulativeNumber.coerceAtLeast(0),
        fecRepairedPackets = stats.fecRepairedPacketsNumber.coerceAtLeast(0),
        estimatedDownloadKbps = stats.estimatedDownloadBandwidth.takeIf { it > 0f },
    )

internal fun LinphoneIce.toSona(): IceState =
    when (this) {
        LinphoneIce.NotActivated -> IceState.NotUsed
        LinphoneIce.InProgress -> IceState.InProgress
        LinphoneIce.Failed -> IceState.Failed
        LinphoneIce.HostConnection -> IceState.Host
        LinphoneIce.ReflexiveConnection -> IceState.Reflexive
        LinphoneIce.RelayConnection -> IceState.Relay
    }

internal fun Address.Family.toSona(): IpFamily? =
    when (this) {
        Address.Family.Inet -> IpFamily.V4
        Address.Family.Inet6 -> IpFamily.V6
        Address.Family.Unspec -> null
    }

/** `SRTP`, `ZRTP`, `DTLS`; `null` for media in clear. */
internal fun MediaEncryption.toName(): String? = takeIf { it != MediaEncryption.None }?.name

/** The suite as SDP names it (`AES_CM_128_HMAC_SHA1_80`, RFC 4568 and 7714); `null` if none. */
internal fun SrtpSuite.sdpName(): String? =
    when (this) {
        SrtpSuite.AESCM128HMACSHA180 -> "AES_CM_128_HMAC_SHA1_80"
        SrtpSuite.AESCM128HMACSHA132 -> "AES_CM_128_HMAC_SHA1_32"
        SrtpSuite.AES192CMHMACSHA180 -> "AES_192_CM_HMAC_SHA1_80"
        SrtpSuite.AES192CMHMACSHA132 -> "AES_192_CM_HMAC_SHA1_32"
        SrtpSuite.AES256CMHMACSHA180 -> "AES_256_CM_HMAC_SHA1_80"
        SrtpSuite.AES256CMHMACSHA132 -> "AES_256_CM_HMAC_SHA1_32"
        SrtpSuite.AEADAES128GCM -> "AEAD_AES_128_GCM"
        SrtpSuite.AEADAES256GCM -> "AEAD_AES_256_GCM"
        SrtpSuite.Invalid -> null
    }

/** A rate Linphone gives in percent, kept between 0 and 100. */
private fun Float.percent(): Float = coerceIn(0f, 100f)

private const val MS = 1000f
