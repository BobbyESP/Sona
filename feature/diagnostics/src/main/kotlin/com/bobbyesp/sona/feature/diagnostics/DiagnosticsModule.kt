/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.diagnostics

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.sona.core.navigation.Navigator
import com.bobbyesp.sona.core.navigation.keys.Diagnostics
import com.bobbyesp.sona.core.ui.contribution.NavSection
import com.bobbyesp.sona.core.ui.contribution.SettingItem
import com.bobbyesp.sona.core.ui.contribution.SettingsAreas
import com.bobbyesp.sona.core.ui.contribution.SettingsContributor
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

internal object DiagnosticsSection : NavSection {
    override fun EntryProviderScope<NavKey>.register(navigator: Navigator) {
        entry<Diagnostics> { DiagnosticsRoute(onBack = navigator::goBack) }
    }
}

/** Puts the overview in the settings, under *Diagnostics*. */
internal object DiagnosticsSettingsContributor : SettingsContributor {
    override val area = SettingsAreas.Diagnostics

    override val items: List<SettingItem> =
        listOf(
            SettingItem.Link(
                title = R.string.settings_diagnostics_open,
                key = Diagnostics,
                summary = R.string.settings_diagnostics_open_summary,
                keywords = R.string.settings_diagnostics_keywords,
            ),
        )
}

val diagnosticsModule = module {
    single { DiagnosticsSection } bind NavSection::class
    single { DiagnosticsSettingsContributor } bind SettingsContributor::class
    viewModelOf(::DiagnosticsViewModel)
}
