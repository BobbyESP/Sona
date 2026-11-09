/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.call

import com.bobbyesp.sona.core.model.settings.CallSettings
import com.bobbyesp.sona.core.model.settings.DeclineMode
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
        )
}
