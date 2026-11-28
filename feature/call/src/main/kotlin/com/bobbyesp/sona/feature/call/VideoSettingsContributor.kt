/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.call

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.PersonalVideo
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoSettings as VideoSettingsIcon
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Wifi
import com.bobbyesp.sona.core.model.settings.VideoBitrate
import com.bobbyesp.sona.core.model.settings.VideoCodec
import com.bobbyesp.sona.core.model.settings.VideoFrameRate
import com.bobbyesp.sona.core.model.settings.VideoQuality
import com.bobbyesp.sona.core.model.settings.VideoSettings
import com.bobbyesp.sona.core.ui.contribution.SettingItem
import com.bobbyesp.sona.core.ui.contribution.SettingsAreas
import com.bobbyesp.sona.core.ui.contribution.SettingsContributor

internal object VideoSettingsContributor : SettingsContributor {
    override val area = SettingsAreas.Video

    override val items: List<SettingItem> =
        listOf(
            SettingItem.Switch(
                setting = VideoSettings.Enabled,
                title = R.string.settings_video,
                icon = Icons.Rounded.Videocam,
                summary = R.string.settings_video_summary,
                keywords = R.string.settings_video_keywords,
            ),
            SettingItem.Switch(
                setting = VideoSettings.AcceptRemote,
                title = R.string.settings_video_accept,
                icon = Icons.Rounded.PersonalVideo,
                summary = R.string.settings_video_accept_summary,
                keywords = R.string.settings_video_accept_keywords,
            ),
            SettingItem.Ranked(
                setting = VideoSettings.Codecs,
                title = R.string.settings_video_codecs,
                icon = Icons.Rounded.VideoSettingsIcon,
                summary = R.string.settings_video_codecs_summary,
                label = VideoCodec::labelOf,
                keywords = R.string.settings_video_codecs_keywords,
            ),
            SettingItem.Choice(
                setting = VideoSettings.WifiQuality,
                title = R.string.settings_video_wifi_quality,
                icon = Icons.Rounded.Wifi,
                summary = R.string.settings_video_quality_summary,
                label = ::qualityLabel,
                keywords = R.string.settings_video_quality_keywords,
            ),
            SettingItem.Choice(
                setting = VideoSettings.WifiBitrate,
                title = R.string.settings_video_wifi_bitrate,
                icon = Icons.Rounded.Speed,
                summary = R.string.settings_video_wifi_bitrate_summary,
                label = ::bitrateLabel,
                keywords = R.string.settings_video_bitrate_keywords,
            ),
            SettingItem.Choice(
                setting = VideoSettings.MobileQuality,
                title = R.string.settings_video_mobile_quality,
                icon = Icons.Rounded.SignalCellularAlt,
                summary = R.string.settings_video_mobile_quality_summary,
                label = ::qualityLabel,
                keywords = R.string.settings_video_quality_keywords,
            ),
            SettingItem.Choice(
                setting = VideoSettings.MobileBitrate,
                title = R.string.settings_video_mobile_bitrate,
                icon = Icons.Rounded.DataUsage,
                summary = R.string.settings_video_mobile_bitrate_summary,
                label = ::bitrateLabel,
                keywords = R.string.settings_video_bitrate_keywords,
            ),
            SettingItem.Choice(
                setting = VideoSettings.FrameRate,
                title = R.string.settings_video_frame_rate,
                icon = Icons.Rounded.SlowMotionVideo,
                summary = R.string.settings_video_frame_rate_summary,
                label = { rate ->
                    when (rate) {
                        VideoFrameRate.Fps15 -> R.string.settings_video_fps_15
                        VideoFrameRate.Fps24 -> R.string.settings_video_fps_24
                        VideoFrameRate.Fps30 -> R.string.settings_video_fps_30
                    }
                },
                keywords = R.string.settings_video_frame_rate_keywords,
            ),
            SettingItem.Switch(
                setting = VideoSettings.AdaptiveRate,
                title = R.string.settings_video_adaptive,
                icon = Icons.Rounded.Tune,
                summary = R.string.settings_video_adaptive_summary,
                keywords = R.string.settings_video_adaptive_keywords,
            ),
            SettingItem.Switch(
                setting = VideoSettings.Retransmission,
                title = R.string.settings_video_retransmission,
                icon = Icons.Rounded.Replay,
                summary = R.string.settings_video_retransmission_summary,
                keywords = R.string.settings_video_retransmission_keywords,
            ),
            SettingItem.Switch(
                setting = VideoSettings.Fec,
                title = R.string.settings_video_fec,
                icon = Icons.Rounded.Healing,
                summary = R.string.settings_video_fec_summary,
                keywords = R.string.settings_video_fec_keywords,
            ),
        )

    private fun qualityLabel(quality: VideoQuality): Int =
        when (quality) {
            VideoQuality.Hd1080 -> R.string.settings_video_quality_1080
            VideoQuality.Hd720 -> R.string.settings_video_quality_720
            VideoQuality.Vga -> R.string.settings_video_quality_vga
            VideoQuality.Qvga -> R.string.settings_video_quality_qvga
        }

    private fun bitrateLabel(bitrate: VideoBitrate): Int =
        when (bitrate) {
            VideoBitrate.Kbps256 -> R.string.settings_video_bitrate_256
            VideoBitrate.Kbps512 -> R.string.settings_video_bitrate_512
            VideoBitrate.Kbps1000 -> R.string.settings_video_bitrate_1000
            VideoBitrate.Kbps1500 -> R.string.settings_video_bitrate_1500
            VideoBitrate.Kbps2500 -> R.string.settings_video_bitrate_2500
            VideoBitrate.Unlimited -> R.string.settings_video_bitrate_unlimited
        }
}
