/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.domain

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.SipAddress

/** A conversation, as stored: one per account and peer. */
@JvmInline
value class ConversationId(val value: Long) {
    override fun toString(): String = value.toString()
}

/** A message, as stored. [None] until the store gives it one. */
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
 * @property peer The other side's address, as conversations are told apart: an extension of the
 *   account's own server always on the account's domain, without a port.
 * @property peerName The name the peer's server gave last (`From`'s display name), if any; the
 *   directory's name for it is preferred, in [ConversationSummary.name].
 * @property lastActivityAt When the last message was written or arrived, in epoch milliseconds.
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
 * @property name Who the peer is: the directory's name, else the server's, else `null` (the UI then
 *   shows [Conversation.peer]'s user).
 * @property last The newest message, for the preview line.
 * @property unread The messages received and not yet read.
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
 * Where a message is. An outgoing one moves forward only: *Sending*, *Sent* (the server took it:
 * `200`/`202`), *Delivered* and *Read* (only a Sona peer says so); or *Failed*, with its
 * [Message.failure]. An incoming one is *Received*.
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
 * @property text What was written. For a file, empty (the [attachment] says what it is).
 * @property at When it was written here or arrived, in epoch milliseconds.
 * @property read For an incoming message, whether the user saw it; an outgoing one is read.
 * @property envelopeId The Sona id of a text (its receipts name it) or of a file (its transfer);
 *   `null` for a plain text.
 * @property failure Why it was not sent, or why its file did not arrive: set when [status] is
 *   [MessageStatus.Failed], or when an incoming file's transfer failed.
 * @property parts, [partsSent] An outgoing text longer than the server's limit goes in parts.
 * @property sizeLimit The limit its parts were cut for, so a text resumed after a restart is cut the
 *   same way even if the setting changed.
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
 * @property transferId Its Sona id: the offer and every chunk name it.
 * @property sha256 The SHA-256 of its bytes, in lowercase hex.
 * @property chunkSize, [chunks] How it is cut: every chunk is [chunkSize] bytes but the last.
 * @property width, [height] A picture's size in pixels.
 * @property done Chunks the server took (sent) or that arrived (received).
 * @property peerReceived For a file sent: the chunks the receiver said it has.
 * @property file Where it is in the app's storage (relative to `files/messages/`): the copy sent,
 *   or the file received once whole. Opened through [Messenger.uriOf].
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

    /** How far the transfer is, from 0 to 1: what arrived, as the receiver knows or says. */
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
    /** The offer is on its way to the server. */
    Offering,

    /** The server took the offer; the other phone has not accepted it yet. */
    WaitingForPeer,

    /** Chunks are going out. */
    Sending,

    /** Every chunk went out; waiting for the receiver to say the file arrived whole. */
    Confirming,

    /** A file offered here, waiting for *Download* (it is larger than the automatic limit). */
    Offered,

    /** Chunks are coming in. */
    Receiving,

    /** Every chunk came; its SHA-256 is being checked. */
    Verifying,

    /** Arrived whole: its SHA-256 matched. */
    Done,

    /** The receiver refused it (sent), or the user did (received). */
    Declined,

    /** One side stopped it. */
    Cancelled,

    /** It could not be sent or received; [Message.failure] says why. */
    Failed;

    val isFinished: Boolean
        get() = this == Done || this == Declined || this == Cancelled || this == Failed
}
