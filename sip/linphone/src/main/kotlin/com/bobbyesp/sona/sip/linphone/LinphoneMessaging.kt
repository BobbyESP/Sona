/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.linphone

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.sip.api.AccountSpec
import com.bobbyesp.sona.sip.api.MessageSendResult
import com.bobbyesp.sona.sip.api.ReceivedMessage
import com.bobbyesp.sona.sip.api.SipMessaging
import com.bobbyesp.sona.sip.api.TEXT_PLAIN
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.linphone.core.Account
import org.linphone.core.Address
import org.linphone.core.ChatMessage
import org.linphone.core.ChatMessageListenerStub
import org.linphone.core.ChatRoom
import org.linphone.core.Conference
import org.linphone.core.Core
import org.linphone.core.Factory
import org.linphone.core.Reason

/**
 * `MESSAGE` on liblinphone: a basic chat room per account and peer, `text/plain` bodies, no CPIM,
 * IMDN or is-composing (receipts and typing travel in the bodies; docs/messaging.md). All of it
 * runs on the Core thread ([dispatcher]).
 *
 * **The app's database is the only record.** Linphone's stays on (calls are tested with it), but
 * outgoing messages are not stored in it and incoming ones are deleted once handed over; only an
 * empty chat room per peer stays.
 *
 * @param later Runs a block on the Core thread once the current callback has returned.
 */
internal class LinphoneMessaging(
    private val dispatcher: CoroutineDispatcher,
    private val core: () -> Core?,
    private val lines: () -> Map<AccountId, Line>,
    private val registered: (AccountId) -> Boolean,
    private val later: (() -> Unit) -> Unit,
) : SipMessaging {

    class Line(val account: Account, val spec: AccountSpec)

    private val inbox = Channel<ReceivedMessage>(Channel.UNLIMITED)

    override val received: Flow<ReceivedMessage> = inbox.receiveAsFlow()

    // Core thread only. Messages awaiting an answer are held so their listener lives as long; the
    // last ones received, so one reported twice (alone and aggregated) is handed over once.
    private val inFlight = mutableMapOf<ChatMessage, CompletableDeferred<MessageSendResult>>()
    private val recent = ArrayDeque<String>()

    /** What the Core must be told before it starts: nothing of IMDN or is-composing. */
    fun prepare(core: Core) {
        core.imNotifPolicy?.clear()
        core.chatMessagesAggregationEnabled = false
    }

    override suspend fun send(account: AccountId, to: SipAddress, body: String): MessageSendResult {
        val answer = withContext(dispatcher) { sendNow(account, to, body) }
        return withTimeoutOrNull(ANSWER_TIMEOUT) { answer.await() }
            ?: withContext(dispatcher) {
                // Linphone answers an unanswered request itself (408, after 32 s): this covers a
                // message it never reports on.
                inFlight.entries
                    .firstOrNull { it.value === answer }
                    ?.let { inFlight.remove(it.key) }
                val server = lines()[account]?.spec?.registrar
                MessageSendResult.Failed(status = null, detail = "No answer from $server")
            }
    }

    private fun sendNow(
        account: AccountId,
        to: SipAddress,
        body: String,
    ): CompletableDeferred<MessageSendResult> {
        val answer = CompletableDeferred<MessageSendResult>()
        val current = core()
        val line = lines()[account]
        if (current == null || line == null || !registered(account)) {
            return answer.apply { complete(MessageSendResult.AccountUnavailable) }
        }
        val address =
            Factory.instance().createAddress(to.uri)
                ?: return answer.apply { complete(failed("Not a SIP address: $to")) }
        val room =
            roomFor(current, line.account, address)
                ?: return answer.apply { complete(failed("Linphone refused a chat room for $to")) }
        val message = room.createMessageFromUtf8(body)
        message.toBeStored = false
        val server = line.spec.registrar.toString()
        message.addListener(
            object : ChatMessageListenerStub() {
                override fun onMsgStateChanged(message: ChatMessage, state: ChatMessage.State) {
                    val error = message.errorInfo
                    val result =
                        messageResultOf(
                            state = state,
                            code = error.protocolCode,
                            phrase = error.phrase,
                            sendFailed = error.reason == Reason.IOError,
                            server = server,
                        ) ?: return
                    message.removeListener(this)
                    inFlight.remove(message)?.complete(result)
                }
            },
        )
        inFlight[message] = answer
        message.send()
        return answer
    }

    private fun failed(detail: String) = MessageSendResult.Failed(status = null, detail = detail)

    private fun roomFor(core: Core, account: Account, peer: Address): ChatRoom? {
        val params = core.createConferenceParams(null)
        params.isChatEnabled = true
        params.isGroupEnabled = false
        params.securityLevel = Conference.SecurityLevel.None
        params.account = account
        val chat = params.chatParams ?: return null
        chat.backend = ChatRoom.Backend.Basic
        chat.encryptionBackend = ChatRoom.EncryptionBackend.None
        chat.ephemeralLifetime = 0
        val participants = arrayOf(peer)
        return core.searchChatRoom(params, account.params.identityAddress, null, participants)
            ?: core.createChatRoom(params, participants)
    }

    fun onReceived(room: ChatRoom, message: ChatMessage) {
        if (message.isOutgoing) return
        val id = message.messageId
        if (!id.isNullOrEmpty()) {
            if (id in recent) return
            recent.addLast(id)
            if (recent.size > RECENT) recent.removeFirst()
        }
        val from = message.fromAddress
        val host = from.domain
        if (host.isNullOrBlank()) return forget(room, message)
        val bound = lines()
        val account =
            accountOfMessage(
                matched = bound.entries.firstOrNull { it.value.account == room.account }?.key,
                identities =
                    bound.mapValues { (_, line) ->
                        "${line.spec.identity.user.orEmpty()}@${line.spec.identity.host}"
                    },
                to = message.toAddress?.let { "${it.username.orEmpty()}@${it.domain}" },
            )
        inbox.trySend(
            ReceivedMessage(
                account = account,
                from =
                    SipAddress(
                        user = from.username?.takeIf { it.isNotBlank() },
                        host = host,
                        port = from.port.takeIf { it in 1..65535 },
                    ),
                fromName = from.displayName?.takeIf { it.isNotBlank() },
                to =
                    message.toAddress?.let { to ->
                        to.domain
                            ?.takeIf { it.isNotBlank() }
                            ?.let {
                                SipAddress(
                                    user = to.username?.takeIf { it.isNotBlank() },
                                    host = it,
                                )
                            }
                    },
                body = message.utf8Text ?: message.textContent.orEmpty(),
                contentType = message.contentType?.substringBefore(';')?.trim() ?: TEXT_PLAIN,
                atMillis = System.currentTimeMillis(),
            ),
        )
        forget(room, message)
    }

    /** Takes [message] out of Linphone's history, once the callback that brought it is over. */
    private fun forget(room: ChatRoom, message: ChatMessage) {
        later { room.deleteMessage(message) }
    }

    private companion object {
        /** Past Linphone's own 32 s timeout for a request nobody answers. */
        val ANSWER_TIMEOUT = 45.seconds
        const val RECENT = 64
    }
}
