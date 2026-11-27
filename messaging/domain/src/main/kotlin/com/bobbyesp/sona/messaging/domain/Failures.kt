/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.domain

import com.bobbyesp.sona.sip.api.MessageSendResult
import com.bobbyesp.sona.sip.api.SipStatus

/**
 * Why a message was not sent, or a file did not arrive, with what the server said: the sentence
 * comes from [reason] (in `:messaging:ui`), the technical line for *Show SIP details* from
 * [technical].
 */
data class MessageFailure(
    val reason: FailureReason,
    val status: SipStatus? = null,
    val detail: String? = null,
) {
    /** `404 Not Found`, or what was tried when nothing answered; `null` when there is no more. */
    val technical: String?
        get() = listOfNotNull(status?.toString(), detail).joinToString(" · ").ifEmpty { null }
}

/** See docs/messaging.md, Failures, in words and in SIP. */
enum class FailureReason {
    /** The account is not registered: nothing was sent. */
    AccountOffline,

    /** `404`, `484`, `604`: there is no such extension on the server. */
    NoSuchPeer,

    /** `415`: the server does not relay this kind of message. */
    Unsupported,

    /** `401`, `403`, `407`: the server refused it; the account's credentials are in question. */
    Refused,

    /** `408`, or no answer at all from the server. */
    NoAnswer,

    /** `480`: the peer is not online right now. */
    PeerOffline,

    /** `503`, or nothing could be sent: the server cannot be reached. */
    ServerUnreachable,

    /** `413`, `513`: the server takes smaller messages than the account's size setting. */
    TooLarge,

    /** Any other answer. */
    Rejected,

    /** A file stopped arriving: five requests for what was missing went unanswered. */
    TimedOut,

    /** A file arrived, but its SHA-256 was not the one offered. */
    Corrupted,

    /** The other phone could not take the file ([MessageFailure.detail] has its reason). */
    PeerFailed,

    /** This phone could not write the file. */
    Storage,

    /** The file to send is no longer in the app's storage. */
    FileMissing,
}

/** The failure for a send that was not taken; `null` when it was. */
internal fun MessageSendResult.toFailure(): MessageFailure? =
    when (this) {
        is MessageSendResult.Accepted -> null
        MessageSendResult.AccountUnavailable -> MessageFailure(FailureReason.AccountOffline)
        is MessageSendResult.Failed -> {
            val reason =
                when (status?.code) {
                    null ->
                        if (detail.isNoAnswer()) FailureReason.NoAnswer
                        else FailureReason.ServerUnreachable
                    404,
                    484,
                    604 -> FailureReason.NoSuchPeer
                    415 -> FailureReason.Unsupported
                    401,
                    403,
                    407 -> FailureReason.Refused
                    408 -> FailureReason.NoAnswer
                    480 -> FailureReason.PeerOffline
                    503 -> FailureReason.ServerUnreachable
                    413,
                    513 -> FailureReason.TooLarge
                    else -> FailureReason.Rejected
                }
            MessageFailure(reason, status, detail)
        }
    }

/**
 * Whether trying again later may work: no answer, nothing could be sent, or a server that says so
 * (`408`, `500`, `503`, `504`). A refusal for what the message is (`404`, `415`) is final.
 */
internal val MessageSendResult.isTransient: Boolean
    get() =
        when (this) {
            is MessageSendResult.Accepted -> false
            MessageSendResult.AccountUnavailable -> true
            is MessageSendResult.Failed -> status?.code?.let { it in TransientCodes } ?: true
        }

private val TransientCodes = setOf(408, 500, 503, 504)

private fun String?.isNoAnswer(): Boolean = this?.startsWith("No answer", ignoreCase = true) == true
