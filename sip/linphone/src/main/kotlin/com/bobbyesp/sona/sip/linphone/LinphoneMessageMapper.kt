/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.linphone

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.sip.api.MessageSendResult
import com.bobbyesp.sona.sip.api.SipStatus
import org.linphone.core.ChatMessage.State as MessageState

/* What Linphone says about a `MESSAGE`, in Sona's words, as pure functions. */

/**
 * The final answer for a message in [state], or `null` while it is not final. Without IMDN, which
 * Sona never asks for, a `2xx` ends at `Delivered`; `DeliveredToUser` and `Displayed` would mean
 * the same here.
 *
 * @param code The SIP status Linphone recorded, `0` when none; [phrase] is its text.
 * @param sendFailed Linphone could not send at all (its local `503 io error`): no server answered,
 *   whatever the code says.
 * @param server Where the account sends, for the technical line.
 */
internal fun messageResultOf(
    state: MessageState,
    code: Int,
    phrase: String?,
    sendFailed: Boolean,
    server: String,
): MessageSendResult? =
    when (state) {
        MessageState.Delivered,
        MessageState.DeliveredToUser,
        MessageState.Displayed ->
            MessageSendResult.Accepted(
                status = if (code in 200..299) SipStatus(code, phrase.orDefault(code)) else null,
            )
        MessageState.NotDelivered,
        MessageState.FileTransferError ->
            when {
                sendFailed ->
                    MessageSendResult.Failed(status = null, detail = "Could not send to $server")
                code >= 300 -> MessageSendResult.Failed(SipStatus(code, phrase.orDefault(code)))
                else -> MessageSendResult.Failed(status = null, detail = "No answer from $server")
            }
        MessageState.Idle,
        MessageState.InProgress,
        MessageState.Queued,
        MessageState.PendingDelivery,
        MessageState.FileTransferInProgress,
        MessageState.FileTransferDone,
        MessageState.FileTransferCancelling -> null
    }

private fun String?.orDefault(code: Int): String =
    this?.takeIf { it.isNotBlank() } ?: if (code in 200..299) "OK" else "Error"

/**
 * The account a message came to: the one Linphone matched, else the one it was addressed to, else
 * the only one. `null` otherwise, and the messaging domain uses the default account.
 *
 * @param identities Each account as `user@domain`, without the scheme; [to] is written the same
 *   way.
 */
internal fun accountOfMessage(
    matched: AccountId?,
    identities: Map<AccountId, String>,
    to: String?,
): AccountId? {
    if (matched != null && matched in identities) return matched
    if (to != null) {
        identities.entries
            .firstOrNull { it.value.equals(to, ignoreCase = true) }
            ?.let {
                return it.key
            }
    }
    return identities.keys.singleOrNull()
}
