/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.settings

import com.bobbyesp.sona.core.ui.contribution.NavSection
import com.bobbyesp.sona.core.ui.contribution.SettingsContributor
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val settingsModule = module {
    single { SettingsSection } bind NavSection::class
    single { AppearanceContributor } bind SettingsContributor::class
    // Every feature's contributions, gathered by type: this feature names none of them.
    viewModel { SettingsViewModel(getAll<SettingsContributor>(), get(), get()) }
}
