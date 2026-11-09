/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.contacts.editor

import com.bobbyesp.sona.core.ui.viewmodel.BaseViewModel
import com.bobbyesp.sona.directory.domain.Contact
import com.bobbyesp.sona.directory.domain.ContactNumber
import com.bobbyesp.sona.directory.domain.LocalContacts

internal data class NumberField(val label: String = "", val value: String = "")

internal data class ContactEditorState(
    val isNew: Boolean = true,
    /** False while an existing contact is being read. */
    val loaded: Boolean = true,
    val name: String = "",
    val numbers: List<NumberField> = listOf(NumberField()),
    val favorite: Boolean = false,
    /** Whether Save was pressed with something missing: only then are the problems shown. */
    val showProblems: Boolean = false,
) {
    val nameMissing: Boolean
        get() = name.isBlank()

    val numberMissing: Boolean
        get() = numbers.none { it.value.isNotBlank() }

    val canSave: Boolean
        get() = !nameMissing && !numberMissing
}

internal sealed interface ContactEditorIntent {
    data class Name(val text: String) : ContactEditorIntent

    data class Label(val index: Int, val text: String) : ContactEditorIntent

    data class Value(val index: Int, val text: String) : ContactEditorIntent

    data object AddNumber : ContactEditorIntent

    data class RemoveNumber(val index: Int) : ContactEditorIntent

    data class Favorite(val favorite: Boolean) : ContactEditorIntent

    data object Save : ContactEditorIntent
}

internal sealed interface ContactEditorEffect {
    data object Leave : ContactEditorEffect
}

/**
 * Adding a contact, or editing one of Sona's own. Comes with [number] and [name] filled in when
 * Recents saves a number it showed.
 */
internal class ContactEditorViewModel(
    private val contactId: String?,
    number: String?,
    name: String?,
    private val contacts: LocalContacts,
) :
    BaseViewModel<ContactEditorIntent, ContactEditorState, ContactEditorEffect>(
        ContactEditorState(
            isNew = contactId == null,
            loaded = contactId == null,
            name = name.orEmpty(),
            numbers = listOf(NumberField(value = number.orEmpty())),
        ),
    ) {

    init {
        if (contactId != null) {
            launch {
                val contact = contacts.get(contactId)
                if (contact == null) sendEffect(ContactEditorEffect.Leave)
                else
                    setState {
                        copy(
                            loaded = true,
                            name = contact.name,
                            numbers =
                                contact.numbers
                                    .map { NumberField(it.label, it.value) }
                                    .ifEmpty { listOf(NumberField()) },
                            favorite = contact.favorite,
                        )
                    }
            }
        }
    }

    override fun onHandleIntent(intent: ContactEditorIntent) {
        when (intent) {
            is ContactEditorIntent.Name -> setState { copy(name = intent.text) }
            is ContactEditorIntent.Label ->
                setState {
                    copy(numbers = numbers.update(intent.index) { it.copy(label = intent.text) })
                }
            is ContactEditorIntent.Value ->
                setState {
                    copy(numbers = numbers.update(intent.index) { it.copy(value = intent.text) })
                }
            ContactEditorIntent.AddNumber -> setState { copy(numbers = numbers + NumberField()) }
            is ContactEditorIntent.RemoveNumber ->
                setState {
                    copy(
                        numbers =
                            numbers
                                .filterIndexed { i, _ -> i != intent.index }
                                .ifEmpty {
                                    listOf(NumberField())
                                },
                    )
                }
            is ContactEditorIntent.Favorite -> setState { copy(favorite = intent.favorite) }
            ContactEditorIntent.Save -> save()
        }
    }

    private fun save() {
        val state = currentState
        if (!state.canSave) {
            setState { copy(showProblems = true) }
            return
        }
        launch {
            contacts.save(
                Contact(
                    id = contactId.orEmpty(),
                    name = state.name.trim(),
                    numbers =
                        state.numbers
                            .filter { it.value.isNotBlank() }
                            .map { ContactNumber(it.label.trim(), it.value.trim()) },
                    favorite = state.favorite,
                ),
            )
            sendEffect(ContactEditorEffect.Leave)
        }
    }
}

private fun List<NumberField>.update(index: Int, change: (NumberField) -> NumberField) =
    mapIndexed { i, field ->
        if (i == index) change(field) else field
    }
