/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.history

import com.bobbyesp.sona.core.ui.contribution.NavSection
import com.bobbyesp.sona.core.ui.contribution.SettingsContributor
import com.bobbyesp.sona.core.ui.contribution.TopLevelDestination
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val historyModule = module {
    single { RecentsSection } bind NavSection::class
    single { RecentsTab(get()) } bind TopLevelDestination::class
    single { HistorySettingsContributor(get()) } bind SettingsContributor::class
    viewModelOf(::RecentsViewModel)
}
