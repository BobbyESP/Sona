/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.api

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.SipAddress
import kotlinx.coroutines.flow.Flow

/**
 * SIP `MESSAGE` (RFC 3428) out of a dialog: a body from an account to an address, through the
 * account's server, and the bodies the server brings. Nothing more: what a body means (a text, a
 * chunk of a file, a receipt) is the messaging domain's, which writes and reads it.
 *
 * Every body is `text/plain`, the only type Asterisk relays (see docs/messaging.md). How long one
 * may be is the domain's to respect: the engine sends what it is given.
 *
 * An engine without [Capability.Messaging] answers every [send] with [MessageSendResult.Failed] and
 * never receives anything.
 */
interface SipMessaging {
    /**
     * Sends [body] from [account] to [to] and suspends until the server's final answer: a `2xx`
     * (for Asterisk always `202 Accepted`, which means *taken*, not *delivered*), a failure, or
     * none (the engine's own timeout). Never throws for what the network does.
     *
     * An account that is not registered is answered [MessageSendResult.AccountUnavailable] at once:
     * nothing is sent.
     */
    suspend fun send(account: AccountId, to: SipAddress, body: String): MessageSendResult

    /**
     * Every `MESSAGE` received, in the order it came, from the moment the engine exists. Nothing is
     * lost while nobody collects: the bodies wait in a buffer for the one collector there is (the
     * messaging coordinator, started before the engine). A second collector would split them.
     */
    val received: Flow<ReceivedMessage>
}

/** The server's final answer to a `MESSAGE`. */
sealed interface MessageSendResult {
    /** The server took it (`200 OK`, `202 Accepted`); [status] when the engine knows which. */
    data class Accepted(val status: SipStatus? = null) : MessageSendResult

    /**
     * Not taken. [status] is the server's answer when there was one (`404`, `415`, `480`, `503`);
     * without one the server did not answer, or nothing could be sent, and [detail] says which.
     */
    data class Failed(val status: SipStatus?, val detail: String? = null) : MessageSendResult

    /** The account is unknown to the engine, disabled, or not registered: nothing was sent. */
    data object AccountUnavailable : MessageSendResult
}

/**
 * A `MESSAGE` that arrived.
 *
 * @property account The account it came to; `null` when the engine could not tell (no account
 *   matched its address).
 * @property from Who sent it, from its `From` header, without parameters.
 * @property fromName The `From` header's display name, when it had one.
 * @property to Whom it was addressed to, from its `To` header.
 * @property contentType As the sender declared it; `text/plain` for everything Asterisk relays.
 * @property atMillis When it arrived, in epoch milliseconds.
 */
data class ReceivedMessage(
    val account: AccountId?,
    val from: SipAddress,
    val fromName: String? = null,
    val to: SipAddress? = null,
    val body: String,
    val contentType: String = TEXT_PLAIN,
    val atMillis: Long,
)

/** The one content type Sona sends, and the one it reads. */
const val TEXT_PLAIN: String = "text/plain"
