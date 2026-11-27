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
 * `MESSAGE` in memory, scripted by the test or the demo that owns it. Every body sent is kept in
 * [sent] and answered by [server], which by default takes it as Asterisk does (`202 Accepted`):
 * ```
 * fake.server = { message -> MessageSendResult.Failed(SipStatus(404, "Not Found")) }
 * fake.deliver(from = SipAddress("101", "lab.local"), body = "hello")
 * ```
 *
 * Two of them make a network: a test gives one's [server] a function that [deliver]s into the
 * other, and decides on the way what the server answers and what is lost.
 *
 * @param registered Whether an account can send: the fake engine's registrations.
 * @param clock The time a body received is stamped with, when it is not given.
 */
class FakeMessaging(
    private val registered: (AccountId) -> Boolean = { true },
    private val clock: () -> Long = { 0L },
) : SipMessaging {

    /** A body as it was sent. */
    data class Sent(val account: AccountId, val to: SipAddress, val body: String)

    /** Every body sent, oldest first, for asserting what went out. */
    val sent: MutableList<Sent> = mutableListOf()

    /** What the server answers to each body sent; it may suspend, as a real server takes time. */
    @Volatile var server: suspend (Sent) -> MessageSendResult = { ACCEPTED }

    private val inbox = Channel<ReceivedMessage>(Channel.UNLIMITED)

    override val received: Flow<ReceivedMessage> = inbox.receiveAsFlow()

    override suspend fun send(account: AccountId, to: SipAddress, body: String): MessageSendResult {
        if (!registered(account)) return MessageSendResult.AccountUnavailable
        val message = Sent(account, to, body)
        synchronized(sent) { sent += message }
        return server(message)
    }

    /** A `MESSAGE` arrives for [account] from [from]. */
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

    /** The bodies sent to [to], in order: what the other side was told. */
    fun sentTo(to: SipAddress): List<String> =
        synchronized(sent) { sent.filter { it.to == to }.map { it.body } }

    companion object {
        /** What Asterisk answers to a `MESSAGE` its dialplan took. */
        val ACCEPTED: MessageSendResult = MessageSendResult.Accepted(SipStatus(202, "Accepted"))
    }
}
