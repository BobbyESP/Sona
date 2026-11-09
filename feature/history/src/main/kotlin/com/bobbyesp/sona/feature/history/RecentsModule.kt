/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.history

import com.bobbyesp.sona.core.ui.contribution.NavSection
import com.bobbyesp.sona.core.ui.contribution.TopLevelDestination
import org.koin.dsl.bind
import org.koin.dsl.module

val historyModule = module {
    single { RecentsSection } bind NavSection::class
    single { RecentsTab } bind TopLevelDestination::class
}
