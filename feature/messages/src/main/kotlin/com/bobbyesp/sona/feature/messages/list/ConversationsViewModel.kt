/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.messages.list

import androidx.compose.runtime.Immutable
import com.bobbyesp.sona.accounts.domain.Account
import com.bobbyesp.sona.accounts.domain.AccountsRepository
import com.bobbyesp.sona.core.ui.viewmodel.BaseViewModel
import com.bobbyesp.sona.directory.domain.containsFolded
import com.bobbyesp.sona.messaging.domain.ConversationId
import com.bobbyesp.sona.messaging.domain.ConversationSummary
import com.bobbyesp.sona.messaging.domain.Messenger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * A conversation as the list shows it.
 *
 * @property title The peer's name, or its user (`102`) when it has none.
 * @property address The whole address, for search and for the details.
 * @property account The account it is on, when there are several, to tell them apart by color.
 */
@Immutable
internal data class ConversationRow(
    val summary: ConversationSummary,
    val title: String,
    val address: String,
    val account: Account? = null,
) {
    val id: ConversationId
        get() = summary.id
}

internal data class ConversationsState(
    /** False until the store was read: the screen shows nothing rather than "no messages". */
    val loaded: Boolean = false,
    val query: String = "",
    /** What the query leaves, the most recent first. */
    val rows: List<ConversationRow> = emptyList(),
    /** Every conversation there is, so *none yet* can be told from *no one matches*. */
    val total: Int = 0,
    /** The conversation waiting for the user to confirm deleting it. */
    val confirmDelete: ConversationRow? = null,
)

internal sealed interface ConversationsIntent {
    data class Query(val text: String) : ConversationsIntent

    data class AskDelete(val row: ConversationRow) : ConversationsIntent

    data object ConfirmDelete : ConversationsIntent

    data object DismissDelete : ConversationsIntent
}

internal class ConversationsViewModel(
    private val messenger: Messenger,
    accounts: AccountsRepository,
) : BaseViewModel<ConversationsIntent, ConversationsState, Nothing>(ConversationsState()) {

    private val query = MutableStateFlow("")

    init {
        launch {
            combine(messenger.conversations, accounts.accounts, query) { all, accounts, query ->
                    val several = accounts.size > 1
                    val rows =
                        all.sortedByDescending { it.conversation.lastActivityAt }
                            .map { summary ->
                                val account = accounts.firstOrNull {
                                    it.id == summary.conversation.account
                                }
                                summary.toRow(account, several)
                            }
                    Triple(rows.filter { it.matches(query) }, rows.size, query)
                }
                .collect { (rows, total, query) ->
                    setState { copy(loaded = true, rows = rows, total = total, query = query) }
                }
        }
    }

    override fun onHandleIntent(intent: ConversationsIntent) {
        when (intent) {
            is ConversationsIntent.Query -> query.value = intent.text
            is ConversationsIntent.AskDelete -> setState { copy(confirmDelete = intent.row) }
            ConversationsIntent.DismissDelete -> setState { copy(confirmDelete = null) }
            ConversationsIntent.ConfirmDelete -> {
                val row = currentState.confirmDelete ?: return
                setState { copy(confirmDelete = null) }
                launch { messenger.deleteConversation(row.id) }
            }
        }
    }
}

internal fun ConversationSummary.toRow(account: Account?, several: Boolean): ConversationRow {
    val peer = conversation.peer
    return ConversationRow(
        summary = this,
        title = name ?: peer.user ?: peer.uri,
        address = peer.uri,
        account = account?.takeIf { several },
    )
}

/** Whether [query] is in the name, the address or the last message. */
internal fun ConversationRow.matches(query: String): Boolean {
    val text = query.trim()
    if (text.isEmpty()) return true
    return title.containsFolded(text) ||
        address.containsFolded(text) ||
        summary.last?.text?.containsFolded(text) == true ||
        summary.last?.attachment?.name?.containsFolded(text) == true
}
