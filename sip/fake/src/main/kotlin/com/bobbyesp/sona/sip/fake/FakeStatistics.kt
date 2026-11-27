/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.fake

import com.bobbyesp.sona.sip.api.CallStatistics
import com.bobbyesp.sona.sip.api.EngineCall
import com.bobbyesp.sona.sip.api.EngineCallState
import com.bobbyesp.sona.sip.api.IceState
import com.bobbyesp.sona.sip.api.IpFamily
import com.bobbyesp.sona.sip.api.StreamStatistics
import com.bobbyesp.sona.sip.api.Transport
import com.bobbyesp.sona.sip.api.VideoConfig
import com.bobbyesp.sona.sip.api.VideoFrames
import kotlin.random.Random

/**
 * What the network between the two sides does to a call's media, as the fake plays it: the
 * statistics wander around these numbers.
 */
data class FakeNetwork(val lossPercent: Float, val jitterMs: Float, val roundTripMs: Float) {
    companion object {
        /** A good Wi-Fi: nearly nothing lost, little jitter. */
        val Good = FakeNetwork(lossPercent = 0.3f, jitterMs = 6f, roundTripMs = 45f)

        /** A weak mobile link, the one that makes the quality indicator say Poor. */
        val Weak = FakeNetwork(lossPercent = 9f, jitterMs = 80f, roundTripMs = 520f)
    }
}

/**
 * One call's numbers as the fake measures them, a second at a time: a random walk pulled towards
 * the network's, from a seed, so a test sees the same numbers every run. Counters grow by what a
 * second of the call carries; the video's bitrate and frame rate shrink with loss, as adaptive rate
 * control would make them.
 */
internal class FakeMeter(seed: Int) {
    private val random = Random(seed)

    private var loss = UNSET
    private var jitter = UNSET
    private var roundTrip = UNSET

    private var audioSent = 0L
    private var audioReceived = 0L
    private var audioLost = 0L
    private var videoSent = 0L
    private var videoReceived = 0L
    private var videoLost = 0L
    private var late = 0L
    private var repaired = 0L

    fun step(network: FakeNetwork) {
        loss = walk(loss, network.lossPercent).coerceAtMost(100f)
        jitter = walk(jitter, network.jitterMs)
        roundTrip = walk(roundTrip, network.roundTripMs)
    }

    fun sample(call: EngineCall, config: VideoConfig, transport: Transport?): CallStatistics {
        val held =
            call.state == EngineCallState.LocalHold || call.state == EngineCallState.RemoteHold
        if (call.state != EngineCallState.Active && !held) {
            // Nothing flows yet: ringing, or still being answered.
            return CallStatistics(transport = transport)
        }
        val lostShare = loss / 100f

        val audioPackets = if (held) 0L else AUDIO_PACKETS
        audioSent += AUDIO_PACKETS
        audioReceived += audioPackets
        audioLost += (audioPackets * lostShare).toLong()
        late += if (jitter > LATE_JITTER_MS) 1 else 0
        val audio =
            StreamStatistics(
                codec = call.media?.codec ?: "opus",
                clockRate = call.media?.clockRate ?: 48_000,
                uploadKbps = if (call.muted) MUTED_KBPS else AUDIO_KBPS,
                downloadKbps = if (held) 0f else AUDIO_KBPS,
                packetsSent = audioSent,
                packetsReceived = audioReceived,
                cumulativeLost = audioLost,
                localLossPercent = loss,
                remoteLossPercent = loss * REMOTE_SHARE,
                jitterMs = jitter,
                remoteJitterMs = jitter * REMOTE_SHARE,
                roundTripMs = roundTrip,
                jitterBufferMs = BUFFER_MS + jitter * 1.5f,
                latePackets = late,
            )

        val video =
            call.video?.let { video ->
                // The adaptive rate's answer to loss: less of everything, never nothing.
                val squeeze = (1f - loss / SQUEEZE_LOSS).coerceIn(MIN_SQUEEZE, 1f)
                val cap = if (config.uploadKbps > 0) config.uploadKbps else UNCAPPED_KBPS
                val sending = video.sending && !held
                val receiving = video.showsRemote && !held
                val up = if (sending) (cap - AUDIO_KBPS) * squeeze * (0.9f + 0.1f * next()) else 0f
                val down = if (receiving) REMOTE_KBPS * squeeze * (0.9f + 0.1f * next()) else 0f
                val packetsUp = packetsOf(up)
                val packetsDown = packetsOf(down)
                videoSent += packetsUp
                videoReceived += packetsDown
                val lostDown = (packetsDown * lostShare).toLong()
                videoLost += lostDown
                if (config.fec) repaired += lostDown / 2
                StreamStatistics(
                    codec = video.codec,
                    clockRate = VIDEO_CLOCK,
                    uploadKbps = up,
                    downloadKbps = down,
                    packetsSent = videoSent,
                    packetsReceived = videoReceived,
                    cumulativeLost = videoLost,
                    localLossPercent = if (receiving) loss else 0f,
                    remoteLossPercent = if (sending) loss * REMOTE_SHARE else 0f,
                    jitterMs = if (receiving) jitter * VIDEO_JITTER else 0f,
                    remoteJitterMs = if (sending) jitter * VIDEO_JITTER else 0f,
                    roundTripMs = roundTrip,
                    jitterBufferMs = if (receiving) BUFFER_MS * 2 + jitter * 2 else 0f,
                    fecRepairedPackets = repaired,
                    estimatedDownloadKbps = if (receiving) down / squeeze else null,
                    frames =
                        VideoFrames(
                            sentSize = video.sentSize,
                            receivedSize = video.receivedSize,
                            sentFps = if (sending) config.maxFrameRate * squeeze else 0f,
                            receivedFps = if (receiving) REMOTE_FPS * squeeze else 0f,
                        ),
                )
            }

        return CallStatistics(
            audio = audio,
            video = video,
            transport = transport,
            ice = IceState.NotUsed,
            ipFamily = IpFamily.V4,
        )
    }

    private fun walk(value: Float, target: Float): Float {
        if (value == UNSET) return target
        val pulled = value + (target - value) * PULL
        val noise = (next() - 0.5f) * NOISE * target
        return (pulled + noise).coerceAtLeast(0f)
    }

    private fun next(): Float = random.nextFloat()

    private fun packetsOf(kbps: Float): Long = (kbps * 1000f / 8f / VIDEO_PACKET_BYTES).toLong()

    private companion object {
        const val UNSET = -1f
        const val PULL = 0.5f
        const val NOISE = 0.3f
        const val AUDIO_PACKETS = 50L
        const val AUDIO_KBPS = 42f
        const val MUTED_KBPS = 6f
        const val REMOTE_SHARE = 0.8f
        const val LATE_JITTER_MS = 60f
        const val BUFFER_MS = 40f
        const val SQUEEZE_LOSS = 15f
        const val MIN_SQUEEZE = 0.25f
        const val UNCAPPED_KBPS = 2500
        const val REMOTE_KBPS = 1200f
        const val REMOTE_FPS = 30f
        const val VIDEO_CLOCK = 90_000
        const val VIDEO_JITTER = 2f
        const val VIDEO_PACKET_BYTES = 1100f
    }
}
