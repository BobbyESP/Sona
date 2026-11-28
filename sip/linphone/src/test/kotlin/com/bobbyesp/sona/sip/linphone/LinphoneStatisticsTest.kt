/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.linphone

import com.bobbyesp.sona.sip.api.IceState
import com.bobbyesp.sona.sip.api.IpFamily
import com.bobbyesp.sona.sip.api.StreamStatistics
import com.bobbyesp.sona.sip.api.Transport
import com.bobbyesp.sona.sip.api.VideoFrames
import com.bobbyesp.sona.sip.api.VideoSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.linphone.core.Address
import org.linphone.core.CallParams
import org.linphone.core.CallStats
import org.linphone.core.IceState as LinphoneIce
import org.linphone.core.MediaEncryption
import org.linphone.core.PayloadType
import org.linphone.core.SrtpSuite
import org.linphone.core.VideoDefinition

class LinphoneStatisticsTest {

    private fun payload(mime: String, rate: Int): PayloadType =
        stand("getMimeType" to mime, "getClockRate" to rate)

    private val audioStats: CallStats =
        stand(
            "getUploadBandwidth" to 38f,
            "getRtcpUploadBandwidth" to 2f,
            "getDownloadBandwidth" to 40f,
            "getRtcpDownloadBandwidth" to 1f,
            "getRtpPacketSent" to 1500L,
            "getRtpPacketRecv" to 1490L,
            "getRtpCumPacketLoss" to 12,
            "getSenderLossRate" to 1.5f,
            "getReceiverLossRate" to 0.5f,
            // Seconds, as Linphone gives them.
            "getSenderInterarrivalJitter" to 0.012f,
            "getReceiverInterarrivalJitter" to 0.004f,
            "getRoundTripDelay" to 0.085f,
            "getJitterBufferSizeMs" to 60f,
            "getLatePacketsCumulativeNumber" to 3L,
            "getFecRepairedPacketsNumber" to 0L,
            "getEstimatedDownloadBandwidth" to 0f,
            "getIceState" to LinphoneIce.ReflexiveConnection,
            "getIpFamilyOfRemote" to Address.Family.Inet6,
            "getSrtpSuite" to SrtpSuite.AESCM128HMACSHA180,
        )

    @Test
    fun `a stream's numbers come in Sona's units`() {
        assertEquals(
            StreamStatistics(
                codec = "opus",
                clockRate = 48_000,
                uploadKbps = 40f,
                downloadKbps = 41f,
                packetsSent = 1500,
                packetsReceived = 1490,
                cumulativeLost = 12,
                localLossPercent = 1.5f,
                remoteLossPercent = 0.5f,
                jitterMs = 12f,
                remoteJitterMs = 4f,
                roundTripMs = 85f,
                jitterBufferMs = 60f,
                latePackets = 3,
                fecRepairedPackets = 0,
                estimatedDownloadKbps = null,
            ),
            streamStatisticsOf(audioStats, payload("opus", 48_000)),
        )
    }

    @Test
    fun `what was not measured yet is not a number`() {
        val fresh: CallStats =
            stand(
                "getRoundTripDelay" to -1f,
                "getRtpCumPacketLoss" to -2,
                "getSenderLossRate" to 140f,
            )

        val stream = streamStatisticsOf(fresh, payload = null)

        assertNull(stream.roundTripMs)
        assertNull(stream.codec)
        assertNull(stream.clockRate)
        // Duplicates counted as negative loss: nothing lost.
        assertEquals(0L, stream.cumulativeLost)
        assertEquals(100f, stream.localLossPercent)
    }

    @Test
    fun `a call's statistics hold both streams, the pictures, and how the media travel`() {
        val videoStats: CallStats =
            stand("getUploadBandwidth" to 900f, "getEstimatedDownloadBandwidth" to 1800f)
        val params: CallParams =
            stand(
                "isVideoEnabled" to true,
                "getUsedAudioPayloadType" to payload("opus", 48_000),
                "getUsedVideoPayloadType" to payload("VP8", 90_000),
                "getSentVideoDefinition" to
                    stand<VideoDefinition>("getWidth" to 640, "getHeight" to 480),
                "getReceivedVideoDefinition" to stand<VideoDefinition>("isUndefined" to true),
                "getSentFramerate" to 24f,
                "getReceivedFramerate" to 0f,
                "getMediaEncryption" to MediaEncryption.SRTP,
            )

        val statistics = callStatisticsOf(params, audioStats, videoStats, Transport.Tls)

        val video = statistics.video!!
        assertEquals("VP8", video.codec)
        assertEquals(1800f, video.estimatedDownloadKbps)
        assertEquals(
            VideoFrames(sentSize = VideoSize(640, 480), receivedSize = null, sentFps = 24f),
            video.frames,
        )
        assertEquals(Transport.Tls, statistics.transport)
        assertEquals(IceState.Reflexive, statistics.ice)
        assertEquals(IpFamily.V6, statistics.ipFamily)
        assertEquals("SRTP", statistics.encryption)
        assertEquals("AES_CM_128_HMAC_SHA1_80", statistics.srtpSuite)
    }

    @Test
    fun `an audio call in clear has no video and no suite`() {
        val params: CallParams =
            stand("isVideoEnabled" to false, "getMediaEncryption" to MediaEncryption.None)

        val statistics =
            callStatisticsOf(params, audioStats, video = stand<CallStats>(), transport = null)

        assertNull(statistics.video)
        assertNull(statistics.encryption)
        assertNull(statistics.srtpSuite)
    }

    @Test
    fun `every ICE state, family and suite has a name`() {
        assertEquals(
            IceState.entries.toList(),
            listOf(
                    LinphoneIce.NotActivated,
                    LinphoneIce.InProgress,
                    LinphoneIce.Failed,
                    LinphoneIce.HostConnection,
                    LinphoneIce.ReflexiveConnection,
                    LinphoneIce.RelayConnection,
                )
                .map { it.toSona() },
        )
        assertNull(Address.Family.Unspec.toSona())
        assertEquals("AEAD_AES_256_GCM", SrtpSuite.AEADAES256GCM.sdpName())
        assertNull(SrtpSuite.Invalid.sdpName())
        assertEquals("ZRTP", MediaEncryption.ZRTP.toName())
    }
}
