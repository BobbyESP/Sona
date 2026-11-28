/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.calling.domain

import com.bobbyesp.sona.core.model.settings.AudioCodec
import com.bobbyesp.sona.core.model.settings.AudioSettings
import com.bobbyesp.sona.core.model.settings.CallSettings
import com.bobbyesp.sona.core.model.settings.DiagnosticsSettings
import com.bobbyesp.sona.core.model.settings.DtmfMethod
import com.bobbyesp.sona.core.model.settings.LogLevel
import com.bobbyesp.sona.core.model.settings.NetworkSettings
import com.bobbyesp.sona.core.model.settings.VideoBitrate
import com.bobbyesp.sona.core.model.settings.VideoCodec
import com.bobbyesp.sona.core.model.settings.VideoFrameRate
import com.bobbyesp.sona.core.model.settings.VideoQuality
import com.bobbyesp.sona.core.model.settings.VideoSettings
import com.bobbyesp.sona.core.testing.InMemorySettingsStore
import com.bobbyesp.sona.sip.api.DtmfMode
import com.bobbyesp.sona.sip.api.EngineConfig
import com.bobbyesp.sona.sip.api.EngineLogLevel
import com.bobbyesp.sona.sip.api.VideoConfig
import com.bobbyesp.sona.sip.api.VideoSize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class EngineConfigBuilderTest {
    private val settings = InMemorySettingsStore()
    private val network = FakeNetworkMonitor(NetworkStatus.Wifi)
    private val builder = EngineConfigBuilder(settings, network)

    @Test
    fun `untouched settings give the catalogue's defaults`() = runTest {
        assertEquals(
            EngineConfig(
                userAgent = "Sona",
                logLevel = EngineLogLevel.Warnings,
                codecs = listOf("opus", "G722", "PCMU", "PCMA"),
                dtmf = DtmfMode.Rfc4733,
                echoCancellation = true,
                wifiOnly = false,
                captureTrace = false,
                video =
                    VideoConfig(
                        enabled = true,
                        codecs = listOf("VP8", "H264", "H265"),
                        definition = VideoSize(1280, 720),
                        maxFrameRate = 30,
                        uploadKbps = 1500,
                        downloadKbps = 0,
                        adaptiveRate = true,
                        retransmission = true,
                        fec = true,
                        lowBandwidth = false,
                        acceptRemoteVideo = true,
                        adaptiveJitter = true,
                        audioDscp = 46,
                        videoDscp = 34,
                    ),
            ),
            builder.config.first(),
        )
    }

    @Test
    fun `the codec order is the user's, without the codecs turned off`() = runTest {
        val codecs = AudioSettings.Codecs
        settings.set(
            codecs,
            codecs.default
                .moved(AudioCodec.G722.name, up = true)
                .switched(AudioCodec.Pcma.name, enabled = false)
                .switched(AudioCodec.Gsm.name, enabled = true),
        )

        assertEquals(listOf("G722", "opus", "PCMU", "GSM"), builder.config.first().codecs)
    }

    @Test
    fun `each option reaches its field`() = runTest {
        settings.set(CallSettings.Dtmf, DtmfMethod.SipInfo)
        settings.set(AudioSettings.EchoCancellation, false)
        settings.set(NetworkSettings.MobileData, false)
        settings.set(DiagnosticsSettings.Logging, LogLevel.Messages)
        settings.set(DiagnosticsSettings.TraceCapture, true)

        val config = builder.config.first()

        assertEquals(DtmfMode.SipInfo, config.dtmf)
        assertEquals(false, config.echoCancellation)
        assertEquals(true, config.wifiOnly)
        assertEquals(EngineLogLevel.Messages, config.logLevel)
        assertEquals(true, config.captureTrace)
    }

    @Test
    fun `each video option reaches its field`() = runTest {
        val codecs = VideoSettings.Codecs
        settings.set(
            codecs,
            codecs.default
                .moved(VideoCodec.H264.name, up = true)
                .switched(VideoCodec.H265.name, enabled = false),
        )
        settings.set(VideoSettings.Enabled, false)
        settings.set(VideoSettings.AcceptRemote, false)
        settings.set(VideoSettings.FrameRate, VideoFrameRate.Fps15)
        settings.set(VideoSettings.Retransmission, false)
        settings.set(VideoSettings.Fec, false)
        settings.set(VideoSettings.AdaptiveRate, false)
        settings.set(VideoSettings.WifiQuality, VideoQuality.Hd1080)
        settings.set(VideoSettings.WifiBitrate, VideoBitrate.Unlimited)

        val video = builder.config.first().video

        assertEquals(listOf("H264", "VP8"), video.codecs)
        assertEquals(false, video.enabled)
        assertEquals(false, video.acceptRemoteVideo)
        assertEquals(15, video.maxFrameRate)
        assertEquals(false, video.retransmission)
        assertEquals(false, video.fec)
        assertEquals(false, video.adaptiveRate)
        assertEquals(VideoSize.Hd1080, video.definition)
        assertEquals(0, video.uploadKbps)
    }

    @Test
    fun `on mobile data video is sent within the mobile limits`() = runTest {
        network.set(NetworkStatus.Cellular)
        settings.set(VideoSettings.MobileBitrate, VideoBitrate.Kbps256)

        val video = builder.config.first().video

        assertEquals(VideoSize.Vga, video.definition)
        assertEquals(256, video.uploadKbps)
        // The other side is asked to send within the same cap: the data is the user's.
        assertEquals(256, video.downloadKbps)
        assertEquals(true, video.lowBandwidth)
    }

    @Test
    fun `a metered Wi-Fi is mobile data a step away`() = runTest {
        network.set(NetworkStatus(NetworkKind.Wifi, metered = true))

        assertEquals(512, builder.config.first().video.uploadKbps)
    }

    @Test
    fun `the configuration follows the network, and losing it keeps the last limits`() = runTest {
        val seen = mutableListOf<EngineConfig>()
        val collecting = launch { builder.config.toList(seen) }
        testScheduler.runCurrent()

        network.set(NetworkStatus.Cellular)
        testScheduler.runCurrent()
        network.set(NetworkStatus.None)
        testScheduler.runCurrent()
        network.set(NetworkStatus.Cellular)
        testScheduler.runCurrent()
        network.set(NetworkStatus.Wifi)
        testScheduler.runCurrent()
        collecting.cancel()

        assertEquals(listOf(1500, 512, 1500), seen.map { it.video.uploadKbps })
    }

    @Test
    fun `starting without a network uses the Wi-Fi limits until one comes`() = runTest {
        network.set(NetworkStatus.None)

        assertEquals(1500, builder.config.first().video.uploadKbps)
    }

    @Test
    fun `a change gives a new configuration, and a setting the engine ignores gives none`() =
        runTest {
            val seen = mutableListOf<EngineConfig>()
            val collecting = launch { builder.config.toList(seen) }
            testScheduler.runCurrent()

            settings.set(CallSettings.CallWaiting, false)
            testScheduler.runCurrent()
            settings.set(CallSettings.Dtmf, DtmfMethod.InBand)
            testScheduler.runCurrent()
            collecting.cancel()

            assertEquals(listOf(DtmfMode.Rfc4733, DtmfMode.InBand), seen.map { it.dtmf })
        }
}
