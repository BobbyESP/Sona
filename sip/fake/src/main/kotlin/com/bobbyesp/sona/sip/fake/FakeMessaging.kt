/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.fake

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.sip.api.MessageSendResult
import com.bobbyesp.sona.sip.api.ReceivedMessage
import com.bobbyesp.sona.sip.api.SipMessaging
import com.bobbyesp.sona.sip.api.SipStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * In-memory `MESSAGE`, scripted by a test or the demo. Bodies are kept in [sent] and answered by
 * [server], by default as Asterisk does (`202 Accepted`); [deliver] makes one arrive. A [server]
 * that delivers into another fake makes a network, and can lose bodies.
 *
 * @param registered Whether an account can send.
 * @param clock Stamps a received body given without a time.
 */
class FakeMessaging(
    private val registered: (AccountId) -> Boolean = { true },
    private val clock: () -> Long = { 0L },
) : SipMessaging {

    data class Sent(val account: AccountId, val to: SipAddress, val body: String)

    val sent: MutableList<Sent> = mutableListOf()

    /** May suspend, as a real server takes time. */
    @Volatile var server: suspend (Sent) -> MessageSendResult = { ACCEPTED }

    private val inbox = Channel<ReceivedMessage>(Channel.UNLIMITED)

    override val received: Flow<ReceivedMessage> = inbox.receiveAsFlow()

    override suspend fun send(account: AccountId, to: SipAddress, body: String): MessageSendResult {
        if (!registered(account)) return MessageSendResult.AccountUnavailable
        val message = Sent(account, to, body)
        synchronized(sent) { sent += message }
        return server(message)
    }

    fun deliver(
        from: SipAddress,
        body: String,
        account: AccountId? = null,
        name: String? = null,
        to: SipAddress? = null,
        contentType: String = "text/plain",
        atMillis: Long = clock(),
    ) {
        inbox.trySend(
            ReceivedMessage(
                account = account,
                from = from,
                fromName = name,
                to = to,
                body = body,
                contentType = contentType,
                atMillis = atMillis,
            ),
        )
    }

    fun sentTo(to: SipAddress): List<String> =
        synchronized(sent) { sent.filter { it.to == to }.map { it.body } }

    companion object {
        /** What Asterisk answers to a `MESSAGE` its dialplan took. */
        val ACCEPTED: MessageSendResult = MessageSendResult.Accepted(SipStatus(202, "Accepted"))
    }
}
