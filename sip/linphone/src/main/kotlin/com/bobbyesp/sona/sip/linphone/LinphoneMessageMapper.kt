/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.linphone

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.sip.api.MessageSendResult
import com.bobbyesp.sona.sip.api.SipStatus
import org.linphone.core.ChatMessage.State as MessageState

/*
 * What Linphone says about a `MESSAGE`, in Sona's words. Pure functions, tested on the JVM, as
 * LinphoneMapper's are for calls and registrations.
 */

/**
 * The final answer for a message in [state], or `null` while it is not final (`Idle`,
 * `InProgress`, `Queued`, `PendingDelivery`): the sender keeps waiting.
 *
 * A basic chat room without IMDN ends at `Delivered` once the server answered `2xx` (Asterisk's
 * `202 Accepted`); `DeliveredToUser` and `Displayed` would need IMDN, which Sona never asks for,
 * and mean the same here.
 *
 * @param code, [phrase] The status Linphone recorded for the transaction; `0` when there was none.
 * @param sendFailed Linphone could not send at all (its local `503 io error`): no server answered,
 *   whatever the code says, as with registrations.
 * @param server Where the account sends, for the line that says what was tried.
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
                status =
                    if (code in 200..299) SipStatus(code, phrase.orDefault(code)) else null,
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
 * The account a message came to: the one Linphone matched it to, then the one whose identity it
 * was addressed to, then the only account there is. `null` when several accounts could be it and
 * none matched: the engine does not guess, and the messaging domain puts it on the default account.
 *
 * @param matched The account Linphone's chat room is on, if any of [identities].
 * @param identities Each account's user and domain, as `user@domain` without the scheme.
 * @param to The `To` header's user and domain, the same way.
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
