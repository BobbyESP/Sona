/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.messages.start

import com.bobbyesp.sona.accounts.domain.Account
import com.bobbyesp.sona.accounts.domain.AccountsRepository
import com.bobbyesp.sona.calling.domain.AddressResolution
import com.bobbyesp.sona.calling.domain.AddressResolver
import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.navigation.keys.NewConversation
import com.bobbyesp.sona.core.ui.viewmodel.BaseViewModel
import com.bobbyesp.sona.directory.domain.Contact
import com.bobbyesp.sona.directory.domain.Directory
import com.bobbyesp.sona.directory.domain.containsFolded
import com.bobbyesp.sona.messaging.domain.ConversationId
import com.bobbyesp.sona.messaging.domain.Messenger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** Why no conversation was opened. */
internal enum class StartProblem {
    /** There is no account to write from. */
    NoAccount,

    /** What was typed is not a number nor an address. */
    NotAnAddress,

    /** The account chosen is gone. */
    AccountGone,
}

internal data class NewConversationState(
    val loaded: Boolean = false,
    val query: String = "",
    /** The contacts with a number, that the query leaves. */
    val contacts: List<Contact> = emptyList(),
    val accounts: List<Account> = emptyList(),
    /** The account the conversation will be on: the one asked for, else the default. */
    val accountId: AccountId? = null,
    /** What the typed text would be written to (`sip:102@lab.local`); `null` when it is none. */
    val typedAddress: String? = null,
    val problem: StartProblem? = null,
    /** A conversation is being opened: the screen waits rather than taking a second tap. */
    val opening: Boolean = false,
    /**
     * The conversation opened, for the screen to give way to. State rather than an effect: opened
     * at once from a party's *Message*, it may be ready before the screen listens.
     */
    val opened: ConversationId? = null,
) {
    /** The account picker is shown only when there is a choice to make. */
    val choosesAccount: Boolean
        get() = accounts.size > 1

    val account: Account?
        get() = accounts.firstOrNull { it.id == accountId }
}

internal sealed interface NewConversationIntent {
    data class Query(val text: String) : NewConversationIntent

    data class ChooseAccount(val id: AccountId) : NewConversationIntent

    /** A contact's number was tapped. */
    data class Pick(val number: String) : NewConversationIntent

    /** *Write to …* for what was typed. */
    data object WriteTyped : NewConversationIntent
}

/**
 * *New message*: who to write to, from the contacts or typed as the keypad takes it (`102`, `+34…`,
 * `ana@lab.local`), and from which account when there are several. Opened with an address (a
 * party's *Message*), it goes straight to the conversation when there is no account to choose.
 */
internal class NewConversationViewModel(
    key: NewConversation,
    private val messenger: Messenger,
    directory: Directory,
    private val accounts: AccountsRepository,
) :
    BaseViewModel<NewConversationIntent, NewConversationState, Nothing>(
        NewConversationState(query = key.address.orEmpty()),
    ) {

    private val query = MutableStateFlow(key.address.orEmpty())

    init {
        launch {
            val all = accounts.accounts.first()
            val asked = all.firstOrNull { it.id.value == key.accountId }
            val chosen = asked ?: all.firstOrNull { it.isDefault } ?: all.firstOrNull()
            setState { copy(accountId = chosen?.id) }
            val address = key.address
            if (address != null && chosen != null && (asked != null || all.size == 1)) {
                open(address)
            }
        }
        launch {
            combine(directory.contacts, accounts.accounts, query) { contacts, accounts, query ->
                    Triple(
                        contacts.filter { it.numbers.isNotEmpty() && it.matches(query) },
                        accounts,
                        query,
                    )
                }
                .collect { (contacts, accounts, query) ->
                    setState {
                        val accountId =
                            accountId?.takeIf { id -> accounts.any { it.id == id } }
                                ?: accounts.firstOrNull { it.isDefault }?.id
                                ?: accounts.firstOrNull()?.id
                        copy(
                            loaded = true,
                            query = query,
                            contacts = contacts,
                            accounts = accounts,
                            accountId = accountId,
                            typedAddress =
                                typed(query, accounts.firstOrNull { it.id == accountId }),
                            problem = if (accounts.isEmpty()) StartProblem.NoAccount else null,
                        )
                    }
                }
        }
    }

    override fun onHandleIntent(intent: NewConversationIntent) {
        when (intent) {
            is NewConversationIntent.Query -> query.value = intent.text
            is NewConversationIntent.ChooseAccount ->
                setState {
                    copy(
                        accountId = intent.id,
                        typedAddress = typed(query, accounts.firstOrNull { it.id == intent.id }),
                    )
                }
            is NewConversationIntent.Pick -> launch { open(intent.number) }
            NewConversationIntent.WriteTyped -> launch { open(currentState.query) }
        }
    }

    private suspend fun open(input: String) {
        if (currentState.opening || currentState.opened != null) return
        val account =
            accounts.accounts.first().firstOrNull { it.id == currentState.accountId }
                ?: return setState { copy(problem = StartProblem.NoAccount) }
        val address =
            when (val resolved = AddressResolver.resolve(input, account.domain)) {
                is AddressResolution.Resolved -> resolved.address
                is AddressResolution.Invalid ->
                    return setState { copy(problem = StartProblem.NotAnAddress) }
            }
        setState { copy(opening = true, problem = null) }
        val id = messenger.open(account.id, address)
        if (id == null) {
            setState { copy(opening = false, problem = StartProblem.AccountGone) }
            return
        }
        setState { copy(opening = false, opened = id) }
    }

    private fun typed(text: String, account: Account?): String? {
        if (account == null || text.isBlank()) return null
        return (AddressResolver.resolve(text, account.domain) as? AddressResolution.Resolved)
            ?.address
            ?.uri
    }
}

/** Whether [query] is in the name or, as digits, in one of the numbers. */
internal fun Contact.matches(query: String): Boolean {
    val text = query.trim()
    if (text.isEmpty()) return true
    val digits = text.filter { it.isDigit() || it == '+' }
    return name.containsFolded(text) ||
        numbers.any { number ->
            number.value.containsFolded(text) ||
                (digits.isNotEmpty() &&
                    number.value.filter { it.isDigit() || it == '+' }.contains(digits))
        }
}
