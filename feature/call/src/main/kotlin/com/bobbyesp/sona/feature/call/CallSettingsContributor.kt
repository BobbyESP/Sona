/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.call

import com.bobbyesp.sona.core.model.settings.AudioCodec
import com.bobbyesp.sona.core.model.settings.AudioSettings
import com.bobbyesp.sona.core.model.settings.CallSettings
import com.bobbyesp.sona.core.model.settings.DeclineMode
import com.bobbyesp.sona.core.model.settings.DtmfMethod
import com.bobbyesp.sona.core.model.settings.NetworkSettings
import com.bobbyesp.sona.core.ui.contribution.SettingItem
import com.bobbyesp.sona.core.ui.contribution.SettingsAreas
import com.bobbyesp.sona.core.ui.contribution.SettingsContributor

/** The options of how calls behave, in the settings' *Calls* area. */
internal object CallSettingsContributor : SettingsContributor {
    override val area = SettingsAreas.Calls

    override val items: List<SettingItem> =
        listOf(
            SettingItem.Switch(
                setting = CallSettings.CallWaiting,
                title = R.string.settings_call_waiting,
                summary = R.string.settings_call_waiting_summary,
                keywords = R.string.settings_call_waiting_keywords,
            ),
            SettingItem.Switch(
                setting = CallSettings.AlwaysReachable,
                title = R.string.settings_always_reachable,
                summary = R.string.settings_always_reachable_summary,
                keywords = R.string.settings_always_reachable_keywords,
            ),
            SettingItem.Switch(
                setting = CallSettings.DoNotDisturb,
                title = R.string.settings_dnd,
                summary = R.string.settings_dnd_summary,
                keywords = R.string.settings_dnd_keywords,
            ),
            SettingItem.Choice(
                setting = CallSettings.DeclineWith,
                title = R.string.settings_decline_with,
                summary = R.string.settings_decline_with_summary,
                label = { mode ->
                    when (mode) {
                        DeclineMode.Busy -> R.string.settings_decline_busy
                        DeclineMode.Declined -> R.string.settings_decline_declined
                    }
                },
                keywords = R.string.settings_decline_keywords,
            ),
            SettingItem.Choice(
                setting = CallSettings.Dtmf,
                title = R.string.settings_dtmf,
                summary = R.string.settings_dtmf_summary,
                label = { method ->
                    when (method) {
                        DtmfMethod.Rfc4733 -> R.string.settings_dtmf_rfc4733
                        DtmfMethod.SipInfo -> R.string.settings_dtmf_info
                        DtmfMethod.InBand -> R.string.settings_dtmf_inband
                    }
                },
                keywords = R.string.settings_dtmf_keywords,
            ),
        )
}

/** How calls sound, in the settings' *Audio* area. */
internal object AudioSettingsContributor : SettingsContributor {
    override val area = SettingsAreas.Audio

    override val items: List<SettingItem> =
        listOf(
            SettingItem.Ranked(
                setting = AudioSettings.Codecs,
                title = R.string.settings_codecs,
                summary = R.string.settings_codecs_summary,
                label = AudioCodec::labelOf,
                keywords = R.string.settings_codecs_keywords,
            ),
            SettingItem.Switch(
                setting = AudioSettings.EchoCancellation,
                title = R.string.settings_echo,
                summary = R.string.settings_echo_summary,
                keywords = R.string.settings_echo_keywords,
            ),
            SettingItem.Switch(
                setting = AudioSettings.DialpadTones,
                title = R.string.settings_dialpad_tones,
                summary = R.string.settings_dialpad_tones_summary,
                keywords = R.string.settings_dialpad_tones_keywords,
            ),
        )
}

/** Which networks calls use, in the settings' *Network* area. */
internal object NetworkSettingsContributor : SettingsContributor {
    override val area = SettingsAreas.Network

    override val items: List<SettingItem> =
        listOf(
            SettingItem.Switch(
                setting = NetworkSettings.MobileData,
                title = R.string.settings_mobile_data,
                summary = R.string.settings_mobile_data_summary,
                keywords = R.string.settings_mobile_data_keywords,
            ),
        )
}
