/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.contacts

import com.bobbyesp.sona.core.navigation.keys.ContactEditor
import com.bobbyesp.sona.core.ui.contribution.NavSection
import com.bobbyesp.sona.core.ui.contribution.SettingsContributor
import com.bobbyesp.sona.core.ui.contribution.TopLevelDestination
import com.bobbyesp.sona.feature.contacts.editor.ContactEditorViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val contactsModule = module {
    single { ContactsSection } bind NavSection::class
    single { ContactsTab } bind TopLevelDestination::class
    single { ContactsSettingsContributor } bind SettingsContributor::class
    viewModelOf(::ContactsViewModel)
    viewModel { params ->
        val key: ContactEditor = params.get()
        ContactEditorViewModel(
            contactId = key.contactId,
            number = key.number,
            name = key.name,
            contacts = get(),
        )
    }
}
