/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.domain

/**
 * What asking to send did. [Queued] means the message is in the conversation and on its way; how it
 * goes on is its [Message.status]. The others say why nothing was added.
 */
sealed interface SendOutcome {
    data class Queued(val message: MessageId) : SendOutcome

    /** Nothing to send: the text was blank. */
    data object Empty : SendOutcome

    /** The conversation, or its account, is gone. */
    data object NoConversation : SendOutcome

    /**
     * The text needs more than [EnvelopeCodec.MAX_TEXT_PARTS] messages at the server's limit of
     * [limit] bytes; it is [bytes] long.
     */
    data class TooLong(val bytes: Int, val limit: Int) : SendOutcome

    /** The file is [size] bytes ready to send; the largest that can be is [limit]. */
    data class FileTooLarge(val size: Long, val limit: Long) : SendOutcome

    /** The file could not be read; [detail] is the technical line. */
    data class FileUnreadable(val detail: String) : SendOutcome
}

/** What happened, for notifications: the screens read the flows instead. */
sealed interface MessengerEvent {
    /**
     * A text or a file offer arrived. [visible] says its conversation is on screen (see
     * [Messenger.setVisibleConversation]): then it is already read, and needs no notification.
     */
    data class Received(
        val conversation: ConversationSummary,
        val message: Message,
        val visible: Boolean,
    ) : MessengerEvent
}
