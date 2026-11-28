/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.model.settings

enum class VideoCodec(val mime: String, val label: String) {
    Vp8("VP8", "VP8"),
    H264("H264", "H.264"),
    H265("H265", "H.265 (HEVC)");

    companion object {
        /** The name to show for a codec as [VideoSettings.Codecs] ranks it (by its enum name). */
        fun labelOf(id: String): String = entries.firstOrNull { it.name == id }?.label ?: id
    }
}

/** The largest picture a call sends; adaptive rate control may send less. */
enum class VideoQuality(val width: Int, val height: Int) {
    Hd1080(1920, 1080),
    Hd720(1280, 720),
    Vga(640, 480),
    Qvga(320, 240),
}

/** The most a call sends, audio included. [kbps] `0` sets no limit. */
enum class VideoBitrate(val kbps: Int) {
    Kbps256(256),
    Kbps512(512),
    Kbps1000(1000),
    Kbps1500(1500),
    Kbps2500(2500),
    Unlimited(0),
}

enum class VideoFrameRate(val fps: Int) {
    Fps15(15),
    Fps24(24),
    Fps30(30),
}

/**
 * How calls carry video (docs/video.md). What is sent depends on the network in use: Wi-Fi takes
 * the `wifi_*` limits, mobile data and a metered Wi-Fi (a phone's hotspot) the `mobile_*` ones.
 */
object VideoSettings {
    /** Video calls at all. Off, calls are audio only: nothing is offered, accepted or captured. */
    val Enabled = Setting.Toggle("video.enabled", default = true)

    /** The codecs offered, first choice first. The two sides settle on the first both have. */
    val Codecs =
        Setting.Ranked(
            "video.codecs",
            default = Ranking(VideoCodec.entries.map { Ranking.Entry(it.name) }),
        )

    /**
     * Video the other side turns on is received, with this side's camera off. Off, it is declined.
     */
    val AcceptRemote = Setting.Toggle("video.accept_remote", default = true)

    val WifiQuality = Setting.choice("video.wifi_quality", VideoQuality.Hd720)

    val WifiBitrate = Setting.choice("video.wifi_bitrate", VideoBitrate.Kbps1500)

    val MobileQuality = Setting.choice("video.mobile_quality", VideoQuality.Vga)

    val MobileBitrate = Setting.choice("video.mobile_bitrate", VideoBitrate.Kbps512)

    val FrameRate = Setting.choice("video.frame_rate", VideoFrameRate.Fps30)

    /** Packets the other side reports lost are sent again (RTCP NACK). */
    val Retransmission = Setting.Toggle("video.retransmission", default = true)

    /** Forward error correction: a little more upload, fewer frozen pictures on a lossy link. */
    val Fec = Setting.Toggle("video.fec", default = true)

    /**
     * Lower the bitrate, then the frame rate, then the definition when the network is congested,
     * and raise them when it clears. It steers the voice's bitrate too.
     */
    val AdaptiveRate = Setting.Toggle("video.adaptive_rate", default = true)
}
