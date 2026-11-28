/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.api

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.SipAddress
import kotlinx.coroutines.flow.Flow

/**
 * SIP `MESSAGE` (RFC 3428) out of a dialog, through the account's server. What a body means (text,
 * file chunk, receipt) is the messaging domain's, which also keeps it within length limits.
 *
 * Every body is `text/plain`, the only type Asterisk relays (docs/messaging.md). Without
 * [Capability.Messaging], [send] answers [MessageSendResult.Failed] and nothing is received.
 */
interface SipMessaging {
    /**
     * Sends [body] and suspends until the server's final answer or the engine's timeout; never
     * throws for what the network does. For Asterisk, `202 Accepted` means *taken*, not
     * *delivered*. From an account that is not registered nothing is sent:
     * [MessageSendResult.AccountUnavailable].
     */
    suspend fun send(account: AccountId, to: SipAddress, body: String): MessageSendResult

    /**
     * Every `MESSAGE` received, in order, buffered until collected. It takes a single collector
     * (the messaging coordinator, started before the engine): a second one would split them.
     */
    val received: Flow<ReceivedMessage>
}

sealed interface MessageSendResult {
    /** The server took it (`200 OK`, `202 Accepted`); [status] when the engine knows which. */
    data class Accepted(val status: SipStatus? = null) : MessageSendResult

    /**
     * Not taken. Without a [status], the server did not answer or nothing could be sent; [detail]
     * says which.
     */
    data class Failed(val status: SipStatus?, val detail: String? = null) : MessageSendResult

    /** The account is unknown, disabled or not registered: nothing was sent. */
    data object AccountUnavailable : MessageSendResult
}

/**
 * A `MESSAGE` that arrived.
 *
 * @property account The account it came to; `null` when none matched its address.
 * @property from The `From` header's address, without parameters.
 * @property contentType As the sender declared it.
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
