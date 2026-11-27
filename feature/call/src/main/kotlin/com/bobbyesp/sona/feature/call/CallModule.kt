/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.call

import com.bobbyesp.sona.core.ui.contribution.SettingsContributor
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val callModule = module {
    viewModelOf(::CallViewModel)
    single { CallSettingsContributor } bind SettingsContributor::class
    single { AudioSettingsContributor } bind SettingsContributor::class
    single { NetworkSettingsContributor } bind SettingsContributor::class
    single { VideoSettingsContributor } bind SettingsContributor::class
}
