/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.contacts

import com.bobbyesp.sona.core.ui.contribution.NavSection
import com.bobbyesp.sona.core.ui.contribution.TopLevelDestination
import org.koin.dsl.bind
import org.koin.dsl.module

val contactsModule = module {
    single { ContactsSection } bind NavSection::class
    single { ContactsTab } bind TopLevelDestination::class
}
