/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.calling.domain

import com.bobbyesp.sona.accounts.domain.toCodecMimes
import com.bobbyesp.sona.accounts.domain.toEngine
import com.bobbyesp.sona.core.model.settings.AudioSettings
import com.bobbyesp.sona.core.model.settings.CallSettings
import com.bobbyesp.sona.core.model.settings.DiagnosticsSettings
import com.bobbyesp.sona.core.model.settings.LogLevel
import com.bobbyesp.sona.core.model.settings.NetworkSettings
import com.bobbyesp.sona.core.model.settings.Ranking
import com.bobbyesp.sona.core.model.settings.SettingsStore
import com.bobbyesp.sona.core.model.settings.VideoBitrate
import com.bobbyesp.sona.core.model.settings.VideoCodec
import com.bobbyesp.sona.core.model.settings.VideoFrameRate
import com.bobbyesp.sona.core.model.settings.VideoQuality
import com.bobbyesp.sona.core.model.settings.VideoSettings
import com.bobbyesp.sona.sip.api.EngineConfig
import com.bobbyesp.sona.sip.api.EngineLogLevel
import com.bobbyesp.sona.sip.api.VideoConfig
import com.bobbyesp.sona.sip.api.VideoSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.scan

/**
 * The user's settings as the engine takes them. The one place that says which option becomes which
 * field of [EngineConfig]: an option the engine must know is mapped here, and nowhere else reads it
 * for the engine. What an account overrides travels with the account instead (`AccountSpec`),
 * through the same two translations.
 *
 * The video limits follow [network] (docs/video.md, The network). Losing the network keeps the ones
 * in use, so a short drop does not push two configurations.
 */
class EngineConfigBuilder(
    private val settings: SettingsStore,
    private val network: NetworkMonitor,
    private val userAgent: String = "Sona",
) {
    private val constrained: Flow<Boolean> =
        network.status
            .scan(null as Boolean?) { before, now ->
                if (now.kind == NetworkKind.None) before ?: false else now.constrained
            }
            .filterNotNull()
            .distinctUntilChanged()

    private val videoChoices: Flow<VideoChoices> =
        combine(
            combine(
                settings.observe(VideoSettings.Enabled),
                settings.observe(VideoSettings.Codecs),
                settings.observe(VideoSettings.AcceptRemote),
                settings.observe(VideoSettings.FrameRate),
                settings.observe(VideoSettings.AdaptiveRate),
            ) { enabled, codecs, acceptRemote, frameRate, adaptiveRate ->
                VideoChoices(enabled, codecs, acceptRemote, frameRate, adaptiveRate)
            },
            combine(
                settings.observe(VideoSettings.Retransmission),
                settings.observe(VideoSettings.Fec),
                ::Pair,
            ),
            combine(
                settings.observe(VideoSettings.WifiQuality),
                settings.observe(VideoSettings.WifiBitrate),
                ::VideoLimits,
            ),
            combine(
                settings.observe(VideoSettings.MobileQuality),
                settings.observe(VideoSettings.MobileBitrate),
                ::VideoLimits,
            ),
        ) { choices, (retransmission, fec), wifi, mobile ->
            choices.copy(retransmission = retransmission, fec = fec, wifi = wifi, mobile = mobile)
        }

    private val video: Flow<VideoConfig> =
        combine(videoChoices, constrained) { choices, constrained ->
            videoConfigOf(choices, constrained)
        }

    /** The configuration as the settings are now, then again at every change of one of them. */
    val config: Flow<EngineConfig> =
        combine(
                settings.observe(AudioSettings.Codecs),
                settings.observe(CallSettings.Dtmf),
                settings.observe(AudioSettings.EchoCancellation),
                settings.observe(NetworkSettings.MobileData),
                settings.observe(DiagnosticsSettings.Logging),
            ) { codecs, dtmf, echo, mobileData, logging ->
                EngineConfig(
                    userAgent = userAgent,
                    logLevel = logging.toEngine(),
                    codecs = codecs.toCodecMimes(),
                    dtmf = dtmf.toEngine(),
                    echoCancellation = echo,
                    wifiOnly = !mobileData,
                )
            }
            .combine(settings.observe(DiagnosticsSettings.TraceCapture)) { config, trace ->
                config.copy(captureTrace = trace)
            }
            .combine(video) { config, video -> config.copy(video = video) }
            .distinctUntilChanged()
}

internal data class VideoLimits(val quality: VideoQuality, val bitrate: VideoBitrate)

/** *Settings › Video* as read, before the network chooses which limits apply. */
internal data class VideoChoices(
    val enabled: Boolean,
    val codecs: Ranking,
    val acceptRemote: Boolean,
    val frameRate: VideoFrameRate,
    val adaptiveRate: Boolean,
    val retransmission: Boolean = true,
    val fec: Boolean = true,
    val wifi: VideoLimits = VideoLimits(VideoQuality.Hd720, VideoBitrate.Kbps1500),
    val mobile: VideoLimits = VideoLimits(VideoQuality.Vga, VideoBitrate.Kbps512),
)

/**
 * On mobile data the other side is asked to send within the same cap, since the data is the user's;
 * on Wi-Fi only what is sent is capped.
 */
internal fun videoConfigOf(choices: VideoChoices, constrained: Boolean): VideoConfig {
    val limits = if (constrained) choices.mobile else choices.wifi
    return VideoConfig(
        enabled = choices.enabled,
        codecs = choices.codecs.toVideoCodecMimes(),
        definition = VideoSize(limits.quality.width, limits.quality.height),
        maxFrameRate = choices.frameRate.fps,
        uploadKbps = limits.bitrate.kbps,
        downloadKbps = if (constrained) limits.bitrate.kbps else 0,
        adaptiveRate = choices.adaptiveRate,
        retransmission = choices.retransmission,
        fec = choices.fec,
        lowBandwidth = constrained,
        acceptRemoteVideo = choices.acceptRemote,
    )
}

private fun Ranking.toVideoCodecMimes(): List<String> = enabled.mapNotNull { name ->
    VideoCodec.entries.firstOrNull { it.name == name }?.mime
}

private fun LogLevel.toEngine(): EngineLogLevel =
    when (this) {
        LogLevel.Errors -> EngineLogLevel.Errors
        LogLevel.Warnings -> EngineLogLevel.Warnings
        LogLevel.Messages -> EngineLogLevel.Messages
        LogLevel.Debug -> EngineLogLevel.Debug
    }
