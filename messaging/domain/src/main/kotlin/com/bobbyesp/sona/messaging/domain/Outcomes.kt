/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.domain

/** What asking to send did: [Queued], or why nothing was added. */
sealed interface SendOutcome {
    data class Queued(val message: MessageId) : SendOutcome

    data object Empty : SendOutcome

    data object NoConversation : SendOutcome

    /**
     * The text needs more than [EnvelopeCodec.MAX_TEXT_PARTS] messages at the server's limit of
     * [limit] bytes; it is [bytes] long.
     */
    data class TooLong(val bytes: Int, val limit: Int) : SendOutcome

    /** The file is [size] bytes ready to send; the largest that can be is [limit]. */
    data class FileTooLarge(val size: Long, val limit: Long) : SendOutcome

    data class FileUnreadable(val detail: String) : SendOutcome
}

/** What happened, for notifications: the screens read the flows instead. */
sealed interface MessengerEvent {
    /**
     * A text or a file offer arrived. [visible]: its conversation is on screen, so it is already
     * read and needs no notification.
     */
    data class Received(
        val conversation: ConversationSummary,
        val message: Message,
        val visible: Boolean,
    ) : MessengerEvent
}
