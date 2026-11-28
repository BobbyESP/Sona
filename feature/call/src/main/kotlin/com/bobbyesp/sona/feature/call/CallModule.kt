/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.call

import com.bobbyesp.sona.core.ui.contribution.PartyActionProvider
import com.bobbyesp.sona.core.ui.contribution.SettingsContributor
import com.bobbyesp.sona.sip.api.SipEngine
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val callModule = module {
    viewModel { CallViewModel(get(), get(), get(), get<SipEngine>().capabilities) }
    single { CallSettingsContributor } bind SettingsContributor::class
    single { AudioSettingsContributor } bind SettingsContributor::class
    single { NetworkSettingsContributor } bind SettingsContributor::class
    single { VideoSettingsContributor } bind SettingsContributor::class
    single { VideoCallAction } bind PartyActionProvider::class
}
