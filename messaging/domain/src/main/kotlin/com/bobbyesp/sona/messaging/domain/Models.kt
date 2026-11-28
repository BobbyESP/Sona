/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.domain

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.SipAddress

@JvmInline
value class ConversationId(val value: Long) {
    override fun toString(): String = value.toString()
}

/** [None] until the store gives it one. */
@JvmInline
value class MessageId(val value: Long) {
    override fun toString(): String = value.toString()

    companion object {
        val None = MessageId(0)
    }
}

/** What Sona knows of the phone at the other end. See docs/messaging.md, Two kinds of peer. */
enum class PeerKind {
    /** Nothing was heard from it yet. Spoken to as a plain peer. */
    Unknown,

    /** It sent plain text, never a Sona envelope: any SIP phone, or Asterisk's dialplan. */
    Plain,

    /** It sent a Sona envelope: texts carry ids, and receipts, typing and files work. */
    Sona,
}

/**
 * The messages between an account and one peer.
 *
 * @property peer The other side's address, as conversations tell peers apart.
 * @property peerName The last `From` display name; [ConversationSummary.name] prefers the
 *   directory's.
 * @property lastActivityAt In epoch milliseconds.
 */
data class Conversation(
    val id: ConversationId,
    val account: AccountId,
    val peer: SipAddress,
    val peerName: String? = null,
    val peerKind: PeerKind = PeerKind.Unknown,
    val lastActivityAt: Long = 0,
)

/**
 * A conversation as a list shows it.
 *
 * @property name The directory's name, else the server's, else `null`.
 * @property unread Received and not yet read.
 */
data class ConversationSummary(
    val conversation: Conversation,
    val name: String? = conversation.peerName,
    val last: Message? = null,
    val unread: Int = 0,
) {
    val id: ConversationId
        get() = conversation.id
}

enum class MessageDirection {
    Incoming,
    Outgoing,
}

/**
 * An outgoing message only moves forward: sending, sent (the server's `200`/`202`), then delivered
 * and read, which only a Sona peer reports; or failed. An incoming one is received.
 */
enum class MessageStatus {
    Sending,
    Sent,
    Delivered,
    Read,
    Failed,
    Received,
}

/**
 * A text or a file, sent or received.
 *
 * @property text Empty for a file.
 * @property at When it was written here or arrived, in epoch milliseconds.
 * @property read An outgoing message always is.
 * @property envelopeId The Sona id of a text or a file; `null` for a plain text.
 * @property failure Set when [status] is [MessageStatus.Failed], or when an incoming file's
 *   transfer failed.
 * @property parts A text longer than the server's limit goes in parts.
 * @property sizeLimit The limit [parts] were cut for: a resume cuts them the same, even if the
 *   setting changed.
 */
data class Message(
    val id: MessageId = MessageId.None,
    val conversation: ConversationId,
    val direction: MessageDirection,
    val text: String,
    val at: Long,
    val status: MessageStatus,
    val read: Boolean = direction == MessageDirection.Outgoing,
    val envelopeId: String? = null,
    val failure: MessageFailure? = null,
    val attachment: Attachment? = null,
    val parts: Int = 1,
    val partsSent: Int = 0,
    val sizeLimit: Int? = null,
) {
    val outgoing: Boolean
        get() = direction == MessageDirection.Outgoing
}

/**
 * A file in a conversation, and its transfer.
 *
 * @property transferId The Sona id the offer and every chunk name.
 * @property sha256 Lowercase hex.
 * @property chunkSize The bytes in every chunk but the last.
 * @property done Chunks the server took (sent) or that arrived (received).
 * @property peerReceived For a sent file, the chunks the receiver says it has.
 * @property file Relative to `files/messages/`: the copy sent, or the received file once whole.
 */
data class Attachment(
    val transferId: String,
    val name: String,
    val mime: String,
    val size: Long,
    val sha256: String,
    val chunkSize: Int,
    val chunks: Int,
    val width: Int? = null,
    val height: Int? = null,
    val state: TransferState,
    val done: Int = 0,
    val peerReceived: Int = 0,
    val file: String? = null,
) {
    val isPicture: Boolean
        get() = mime.startsWith("image/")

    /** From 0 to 1. */
    val progress: Float
        get() {
            if (chunks == 0) return if (state == TransferState.Done) 1f else 0f
            val count = if (peerReceived > 0) peerReceived else done
            return (count.toFloat() / chunks).coerceIn(0f, 1f)
        }
}

/**
 * Where a file's transfer is. Sent: [Offering], [WaitingForPeer], [Sending], [Confirming], then
 * [Done]. Received: [Offered], [Receiving], [Verifying], then [Done]. Either may end [Declined],
 * [Cancelled] or [Failed].
 */
enum class TransferState {
    Offering,

    /** The server took the offer; the other phone has not accepted it yet. */
    WaitingForPeer,
    Sending,

    /** Every chunk went out; waiting for the receiver to say the file arrived whole. */
    Confirming,

    /** A file offered here, waiting for *Download* (it is larger than the automatic limit). */
    Offered,
    Receiving,
    Verifying,

    /** Arrived whole: its SHA-256 matched. */
    Done,
    Declined,
    Cancelled,
    Failed;

    val isFinished: Boolean
        get() = this == Done || this == Declined || this == Cancelled || this == Failed
}
