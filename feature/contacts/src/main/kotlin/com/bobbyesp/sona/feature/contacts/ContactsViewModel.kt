/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.contacts

import com.bobbyesp.sona.calling.domain.CallCoordinator
import com.bobbyesp.sona.calling.domain.DialOutcome
import com.bobbyesp.sona.core.model.settings.ContactsSettings
import com.bobbyesp.sona.core.model.settings.SettingsStore
import com.bobbyesp.sona.core.ui.viewmodel.BaseViewModel
import com.bobbyesp.sona.directory.domain.Contact
import com.bobbyesp.sona.directory.domain.Directory
import com.bobbyesp.sona.directory.domain.LocalContacts
import com.bobbyesp.sona.directory.domain.containsFolded
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

internal data class ContactsState(
    val loaded: Boolean = false,
    val query: String = "",
    /** The contacts the query leaves, A to Z. */
    val contacts: List<Contact> = emptyList(),
    /** Every contact there is, so *none yet* can be told from *no one matches*. */
    val total: Int = 0,
    /** Whether to offer the phone contacts: the setting is on and the permission is not given. */
    val offerDevice: Boolean = false,
    val detailsOf: String? = null,
    val problem: DialOutcome? = null,
) {
    val favorites: List<Contact>
        get() = contacts.filter { it.favorite }

    val details: Contact?
        get() = detailsOf?.let { id -> contacts.firstOrNull { it.id == id } }
}

internal sealed interface ContactsIntent {
    data class Query(val text: String) : ContactsIntent

    data class Open(val contact: Contact) : ContactsIntent

    data object Close : ContactsIntent

    data class Call(val number: String) : ContactsIntent

    data class Favorite(val contact: Contact, val favorite: Boolean) : ContactsIntent

    data class Delete(val contact: Contact) : ContactsIntent

    /** The user answered the request for the phone contacts: read them again. */
    data object DeviceAccessAnswered : ContactsIntent
}

internal sealed interface ContactsEffect {
    data object OpenCall : ContactsEffect
}

internal class ContactsViewModel(
    private val directory: Directory,
    private val local: LocalContacts,
    private val coordinator: CallCoordinator,
    settings: SettingsStore,
) : BaseViewModel<ContactsIntent, ContactsState, ContactsEffect>(ContactsState()) {

    private val query = MutableStateFlow("")

    init {
        launch {
            combine(
                    directory.contacts,
                    query,
                    directory.deviceGranted,
                    settings.observe(ContactsSettings.UseDeviceContacts),
                ) { all, query, granted, useDevice ->
                    ContactsState(
                        loaded = true,
                        query = query,
                        contacts = all.filter { it.matches(query) },
                        total = all.size,
                        offerDevice = useDevice && !granted,
                    )
                }
                .collect { next ->
                    setState {
                        next.copy(detailsOf = detailsOf, problem = problem)
                    }
                }
        }
    }

    override fun onHandleIntent(intent: ContactsIntent) {
        when (intent) {
            is ContactsIntent.Query -> query.value = intent.text
            is ContactsIntent.Open ->
                setState { copy(detailsOf = intent.contact.id, problem = null) }
            ContactsIntent.Close -> setState { copy(detailsOf = null) }
            is ContactsIntent.Call -> call(intent.number)
            is ContactsIntent.Favorite ->
                launch {
                    // Only Sona's own contacts can be starred: the phone's are read only.
                    if (intent.contact.editable)
                        local.setFavorite(intent.contact.id, intent.favorite)
                }
            is ContactsIntent.Delete -> {
                setState { copy(detailsOf = null) }
                launch { local.delete(intent.contact.id) }
            }
            ContactsIntent.DeviceAccessAnswered -> launch { directory.refreshDevice() }
        }
    }

    private fun call(number: String) {
        launch {
            when (val outcome = coordinator.dial(number)) {
                is DialOutcome.Started -> {
                    setState { copy(problem = null, detailsOf = null) }
                    sendEffect(ContactsEffect.OpenCall)
                }
                else -> setState { copy(problem = outcome) }
            }
        }
    }
}

private fun Contact.matches(query: String): Boolean {
    val text = query.trim()
    if (text.isEmpty()) return true
    val digits = text.filter { it.isDigit() || it == '+' }
    return name.containsFolded(text) ||
        (digits.isNotEmpty() &&
            numbers.any { number ->
                number.value.filter { it.isDigit() || it == '+' }.contains(digits)
            })
}
