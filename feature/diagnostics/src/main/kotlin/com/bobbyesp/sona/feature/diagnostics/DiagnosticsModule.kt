/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.diagnostics

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Subject
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Timeline
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.sona.core.model.settings.DiagnosticsSettings
import com.bobbyesp.sona.core.model.settings.LogLevel
import com.bobbyesp.sona.core.navigation.Navigator
import com.bobbyesp.sona.core.navigation.keys.Diagnostics
import com.bobbyesp.sona.core.ui.contribution.NavSection
import com.bobbyesp.sona.core.ui.contribution.SettingItem
import com.bobbyesp.sona.core.ui.contribution.SettingsAreas
import com.bobbyesp.sona.core.ui.contribution.SettingsContributor
import com.bobbyesp.sona.feature.diagnostics.trace.TraceRoute
import com.bobbyesp.sona.feature.diagnostics.trace.TraceViewModel
import kotlinx.serialization.Serializable
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

/** The SIP messages sent and received. Only this feature opens it, from its settings item. */
@Serializable data object SipTrace : NavKey

internal object DiagnosticsSection : NavSection {
    override fun EntryProviderScope<NavKey>.register(navigator: Navigator) {
        entry<Diagnostics> { DiagnosticsRoute(onBack = navigator::goBack) }
        entry<SipTrace> { TraceRoute(onBack = navigator::goBack) }
    }
}

/** Puts the overview and how much is logged in the settings, under *Diagnostics*. */
internal object DiagnosticsSettingsContributor : SettingsContributor {
    override val area = SettingsAreas.Diagnostics

    override val items: List<SettingItem> =
        listOf(
            SettingItem.Link(
                title = R.string.settings_diagnostics_open,
                icon = Icons.Rounded.MonitorHeart,
                key = Diagnostics,
                summary = R.string.settings_diagnostics_open_summary,
                keywords = R.string.settings_diagnostics_keywords,
            ),
            SettingItem.Link(
                title = R.string.settings_trace_open,
                icon = Icons.Rounded.Timeline,
                key = SipTrace,
                summary = R.string.settings_trace_open_summary,
                keywords = R.string.settings_trace_keywords,
            ),
            SettingItem.Switch(
                setting = DiagnosticsSettings.TraceCapture,
                title = R.string.trace_capture,
                icon = Icons.Rounded.FiberManualRecord,
                summary = R.string.trace_capture_summary,
                keywords = R.string.settings_trace_keywords,
            ),
            SettingItem.Choice(
                setting = DiagnosticsSettings.Logging,
                title = R.string.settings_log_level,
                icon = Icons.AutoMirrored.Rounded.Subject,
                summary = R.string.settings_log_level_summary,
                label = { level ->
                    when (level) {
                        LogLevel.Errors -> R.string.settings_log_errors
                        LogLevel.Warnings -> R.string.settings_log_warnings
                        LogLevel.Messages -> R.string.settings_log_messages
                        LogLevel.Debug -> R.string.settings_log_debug
                    }
                },
                keywords = R.string.settings_log_level_keywords,
            ),
        )
}

val diagnosticsModule = module {
    single { DiagnosticsSection } bind NavSection::class
    single { DiagnosticsSettingsContributor } bind SettingsContributor::class
    viewModelOf(::DiagnosticsViewModel)
    viewModelOf(::TraceViewModel)
}
