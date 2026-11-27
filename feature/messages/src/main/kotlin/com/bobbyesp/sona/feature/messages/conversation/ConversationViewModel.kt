/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.messages.conversation

import androidx.compose.runtime.Immutable
import com.bobbyesp.sona.accounts.domain.Account
import com.bobbyesp.sona.accounts.domain.AccountsRepository
import com.bobbyesp.sona.core.common.DispatcherProvider
import com.bobbyesp.sona.core.ui.viewmodel.BaseViewModel
import com.bobbyesp.sona.messaging.domain.CameraCaptures
import com.bobbyesp.sona.messaging.domain.ConversationId
import com.bobbyesp.sona.messaging.domain.ConversationSummary
import com.bobbyesp.sona.messaging.domain.Message
import com.bobbyesp.sona.messaging.domain.MessageId
import com.bobbyesp.sona.messaging.domain.Messenger
import com.bobbyesp.sona.messaging.domain.PeerKind
import com.bobbyesp.sona.messaging.domain.SendOutcome
import com.bobbyesp.sona.messaging.ui.FailureSubject
import com.bobbyesp.sona.messaging.ui.SendProblem
import com.bobbyesp.sona.messaging.ui.problem
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Who the conversation is with, as its top bar shows it.
 *
 * @property title The peer's name, or its user (`102`).
 * @property address Its whole address, in the monospace face SIP is read in.
 * @property account The account it is on; [severalAccounts] says whether to mark it.
 */
@Immutable
internal data class ConversationHeader(
    val title: String,
    val address: String,
    val peerKind: PeerKind,
    val account: Account?,
    val severalAccounts: Boolean,
) {
    /** Receipts, typing and files need the other phone to be Sona too: said, once, when not. */
    val plainPeer: Boolean
        get() = peerKind != PeerKind.Sona
}

internal data class ConversationState(
    val loaded: Boolean = false,
    val header: ConversationHeader? = null,
    /** The conversation as drawn, oldest first. */
    val items: List<ThreadItem> = emptyList(),
    val typing: Boolean = false,
    /** What is being written. Kept here so it survives a rotation and drives typing. */
    val draft: String = "",
    /** Why the last thing sent was not added, until the user does something else. */
    val problem: SendProblem? = null,
    /** A file is being read and prepared (a picture resized) before it goes. */
    val preparing: Boolean = false,
    /** The message whose details are open. */
    val detailsOf: MessageId? = null,
    /** The conversation was deleted (here, or from the list beside): the screen leaves. */
    val gone: Boolean = false,
    /** What failures are about, for their sentences. */
    val subject: FailureSubject = FailureSubject("", "", ""),
) {
    val details: Message?
        get() = detailsOf?.let { id ->
            items.firstNotNullOfOrNull {
                (it as? ThreadItem.Bubble)?.message?.takeIf { m -> m.id == id }
            }
        }
}

internal sealed interface ConversationIntent {
    /** The screen is shown (resumed) or left (paused): what arrives while shown is read. */
    data class Visible(val visible: Boolean) : ConversationIntent

    data class Draft(val text: String) : ConversationIntent

    data object Send : ConversationIntent

    /** A file or a picture was chosen: its `content://` address. */
    data class Attach(val uri: String) : ConversationIntent

    /** *Camera* was chosen, and the permission is there. */
    data object TakePicture : ConversationIntent

    /** The camera came back, with the picture or without. */
    data class PictureTaken(val uri: String, val taken: Boolean) : ConversationIntent

    data class Retry(val message: MessageId) : ConversationIntent

    data class Delete(val message: MessageId) : ConversationIntent

    data class Download(val message: MessageId) : ConversationIntent

    data class Decline(val message: MessageId) : ConversationIntent

    data class Cancel(val message: MessageId) : ConversationIntent

    data class Details(val message: MessageId?) : ConversationIntent

    data object DismissProblem : ConversationIntent
}

internal sealed interface ConversationEffect {
    /** Opens the camera, to write its picture to [uri]. */
    data class OpenCamera(val uri: String) : ConversationEffect

    /** No place could be made for the camera's picture. */
    data object CameraUnavailable : ConversationEffect
}

/**
 * One conversation: its messages, what is being written, and everything done to a message. It tells
 * the messenger while it is on screen, so what arrives is read at once and not notified.
 */
internal class ConversationViewModel(
    conversationId: Long,
    private val messenger: Messenger,
    accounts: AccountsRepository,
    private val captures: CameraCaptures,
    dispatchers: DispatcherProvider,
) : BaseViewModel<ConversationIntent, ConversationState, ConversationEffect>(ConversationState()) {

    private val id = ConversationId(conversationId)
    private var visible = false

    init {
        launch {
            combine(messenger.conversation(id), accounts.accounts) { summary, all ->
                    summary to all
                }
                .collect { (summary, all) ->
                    if (summary == null) {
                        setState { copy(gone = true, loaded = true) }
                        return@collect
                    }
                    val header = summary.toHeader(all)
                    setState {
                        copy(
                            loaded = true,
                            header = header,
                            subject =
                                FailureSubject(
                                    account = header.account?.label.orEmpty(),
                                    peer = summary.conversation.peer.user ?: header.address,
                                    server = header.account?.server?.toString().orEmpty(),
                                ),
                        )
                    }
                }
        }
        launch {
            messenger
                .messages(id)
                .map { messages ->
                    messages.toThread(
                        uriOf = { message ->
                            message.attachment?.takeIf { it.file != null }?.let(messenger::uriOf)
                        },
                    )
                }
                .flowOn(dispatchers.default)
                .collect { items -> setState { copy(items = items) } }
        }
        launch { messenger.typing(id).collect { typing -> setState { copy(typing = typing) } } }
    }

    override fun onHandleIntent(intent: ConversationIntent) {
        when (intent) {
            is ConversationIntent.Visible -> {
                visible = intent.visible
                messenger.setVisibleConversation(if (intent.visible) id else null)
            }
            is ConversationIntent.Draft -> {
                val grew = intent.text.isNotBlank() && intent.text != currentState.draft
                setState { copy(draft = intent.text, problem = null) }
                if (grew) messenger.notifyTyping(id)
            }
            ConversationIntent.Send -> send()
            is ConversationIntent.Attach -> attach(intent.uri)
            ConversationIntent.TakePicture ->
                launch {
                    val uri = captures.newCapture()
                    sendEffect(
                        if (uri != null) ConversationEffect.OpenCamera(uri)
                        else ConversationEffect.CameraUnavailable,
                    )
                }
            is ConversationIntent.PictureTaken -> if (intent.taken) attach(intent.uri)
            is ConversationIntent.Retry -> launch { messenger.retry(intent.message) }
            is ConversationIntent.Delete -> {
                if (currentState.detailsOf == intent.message) setState { copy(detailsOf = null) }
                launch { messenger.delete(listOf(intent.message)) }
            }
            is ConversationIntent.Download -> launch { messenger.download(intent.message) }
            is ConversationIntent.Decline -> launch { messenger.decline(intent.message) }
            is ConversationIntent.Cancel -> launch { messenger.cancel(intent.message) }
            is ConversationIntent.Details -> setState { copy(detailsOf = intent.message) }
            ConversationIntent.DismissProblem -> setState { copy(problem = null) }
        }
    }

    private fun send() {
        val text = currentState.draft
        if (text.isBlank()) return
        setState { copy(draft = "", problem = null) }
        launch {
            val outcome = messenger.sendText(id, text)
            if (outcome !is SendOutcome.Queued) {
                // What was written is not lost: it is back in the field, with why.
                setState { copy(draft = draft.ifEmpty { text }, problem = outcome.problem()) }
            }
        }
    }

    private fun attach(uri: String) {
        setState { copy(preparing = true, problem = null) }
        launch(onError = { setState { copy(preparing = false) } }) {
            val outcome = messenger.sendFile(id, uri)
            setState { copy(preparing = false, problem = outcome.problem()) }
        }
    }

    override fun onCleared() {
        if (visible) messenger.setVisibleConversation(null)
        super.onCleared()
    }
}

internal fun ConversationSummary.toHeader(accounts: List<Account>): ConversationHeader {
    val peer = conversation.peer
    return ConversationHeader(
        title = name ?: peer.user ?: peer.uri,
        address = peer.uri,
        peerKind = conversation.peerKind,
        account = accounts.firstOrNull { it.id == conversation.account },
        severalAccounts = accounts.size > 1,
    )
}
