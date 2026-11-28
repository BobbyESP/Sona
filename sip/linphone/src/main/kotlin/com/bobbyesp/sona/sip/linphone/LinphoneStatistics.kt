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
 * Linphone's call statistics in Sona's words and units.
 *
 * Linphone names them after RTCP: "sender" loss and jitter come from the report this side sends
 * (local), "receiver" ones from the report it receives (remote). Jitter and round trip come in
 * seconds.
 */

/**
 * [audio] or [video] is `null` without that stream. [transport] is the account's: Linphone keeps
 * none per call.
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

internal fun MediaEncryption.toName(): String? = takeIf { it != MediaEncryption.None }?.name

/** As SDP names it (RFC 4568 and 7714); `null` if it has none. */
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

private fun Float.percent(): Float = coerceIn(0f, 100f)

private const val MS = 1000f
