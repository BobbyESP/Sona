/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.dialer

import com.bobbyesp.sona.core.ui.contribution.NavSection
import com.bobbyesp.sona.core.ui.contribution.TopLevelDestination
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val dialerModule = module {
    single { KeypadSection } bind NavSection::class
    single { KeypadTab } bind TopLevelDestination::class
    viewModelOf(::KeypadViewModel)
}
