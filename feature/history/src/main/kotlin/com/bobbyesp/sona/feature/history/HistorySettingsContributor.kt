/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.history

import com.bobbyesp.sona.core.model.settings.HistoryRetention
import com.bobbyesp.sona.core.model.settings.HistorySettings
import com.bobbyesp.sona.core.model.settings.RecentTapAction
import com.bobbyesp.sona.core.ui.contribution.SettingItem
import com.bobbyesp.sona.core.ui.contribution.SettingsAreas
import com.bobbyesp.sona.core.ui.contribution.SettingsContributor
import com.bobbyesp.sona.directory.domain.CallHistory

/** How Recents behaves and how long calls are kept, in *Contacts and history*. */
internal class HistorySettingsContributor(history: CallHistory) : SettingsContributor {
    override val area = SettingsAreas.Directory

    override val items: List<SettingItem> =
        listOf(
            SettingItem.Switch(
                setting = HistorySettings.GroupCalls,
                title = R.string.settings_group,
                summary = R.string.settings_group_summary,
                keywords = R.string.settings_group_keywords,
            ),
            SettingItem.Choice(
                setting = HistorySettings.TapAction,
                title = R.string.settings_tap,
                summary = R.string.settings_tap_summary,
                label = { action ->
                    when (action) {
                        RecentTapAction.Details -> R.string.settings_tap_details
                        RecentTapAction.CallBack -> R.string.settings_tap_call_back
                    }
                },
                keywords = R.string.settings_tap_keywords,
            ),
            SettingItem.Switch(
                setting = HistorySettings.ConfirmCallBack,
                title = R.string.settings_confirm_calls,
                summary = R.string.settings_confirm_summary,
                keywords = R.string.settings_confirm_keywords,
            ),
            SettingItem.Choice(
                setting = HistorySettings.Retention,
                title = R.string.settings_retention,
                summary = R.string.settings_retention_summary,
                label = { retention ->
                    when (retention) {
                        HistoryRetention.Forever -> R.string.settings_retention_forever
                        HistoryRetention.Year -> R.string.settings_retention_year
                        HistoryRetention.Days90 -> R.string.settings_retention_90
                        HistoryRetention.Days30 -> R.string.settings_retention_30
                    }
                },
                keywords = R.string.settings_retention_keywords,
            ),
            SettingItem.Action(
                title = R.string.settings_clear,
                summary = R.string.settings_clear_summary,
                keywords = R.string.settings_clear_keywords,
                destructive = true,
                confirm = R.string.settings_clear_confirm,
                run = { history.clear() },
            ),
        )
}
