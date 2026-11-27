/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.domain

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.SipAddress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Where conversations and messages are kept: Room in the app (schema 4), maps in tests. The only
 * record there is: the engine keeps nothing (docs/messaging.md, Storage).
 *
 * A message is written whole ([add], [update]); what changes many times a second (which chunks of a
 * file arrived) has calls of its own, so a transfer does not rewrite its message each time.
 */
interface MessageStore {
    /** Every conversation, the most recent first, with its last message and unread count. */
    val conversations: Flow<List<ConversationSummary>>

    /** The messages of [conversation], oldest first. */
    fun messages(conversation: ConversationId): Flow<List<Message>>

    /** Every message received and not yet read, in every conversation. */
    val unread: Flow<Int>

    suspend fun conversation(id: ConversationId): Conversation?

    suspend fun findConversation(account: AccountId, peer: SipAddress): Conversation?

    /** The conversation of [account] with [peer], made (with [peerName]) when there is none. */
    suspend fun openConversation(
        account: AccountId,
        peer: SipAddress,
        peerName: String?,
        at: Long,
    ): Conversation

    /** Writes what a conversation knows of its peer: its kind, its name. */
    suspend fun updateConversation(conversation: Conversation)

    /** Deletes [id] and every message in it. */
    suspend fun deleteConversation(id: ConversationId)

    /**
     * Adds [message] (and its attachment); it comes back with its id. Its conversation moves up.
     */
    suspend fun add(message: Message): Message

    suspend fun message(id: MessageId): Message?

    /** The message with the Sona id [envelopeId] in [conversation], in that direction. */
    suspend fun messageByEnvelope(
        conversation: ConversationId,
        direction: MessageDirection,
        envelopeId: String,
    ): Message?

    /** Writes [message] and its attachment as they are now. */
    suspend fun update(message: Message)

    suspend fun delete(ids: Collection<MessageId>)

    /** Marks every message received in [conversation] read; gives the ones that were not. */
    suspend fun markRead(conversation: ConversationId): List<Message>

    /**
     * What was left unfinished: texts still sending, and files offered, going out or coming in. A
     * restart resumes them.
     */
    suspend fun unfinished(): List<Message>

    /** Which chunks of an incoming file arrived, one bit each, as last saved. */
    suspend fun receivedChunks(message: MessageId): ByteArray?

    /** Saves which chunks of an incoming file arrived, and how many. */
    suspend fun saveReceivedChunks(message: MessageId, bits: ByteArray, count: Int)

    /**
     * Keeps part [index] (from 1) of [total] of the incoming text [envelopeId]; gives every part
     * kept so far, by index.
     */
    suspend fun addPart(
        conversation: ConversationId,
        envelopeId: String,
        index: Int,
        total: Int,
        text: String,
        at: Long,
    ): Map<Int, String>

    /** Adds the text put together from its parts, and forgets the parts, at once. */
    suspend fun completeText(message: Message): Message
}

/**
 * The files of messages, in the app's own storage (`files/messages/`). A file is named by a path
 * relative to it, as an [Attachment.file] keeps it. An incoming one is written in place, chunk by
 * chunk, in a file of its own named by a [key] until it is whole. Nothing here reads a whole file
 * into memory.
 *
 * Every function answers a failure (no space, a file gone) with `false` or `null`, never throws.
 */
interface AttachmentFiles {
    /** Makes the file for an incoming transfer, of [size] bytes; keeps what is already there. */
    suspend fun openIncoming(key: String, size: Long): Boolean

    /** Writes [bytes] at [offset] of the incoming file [key]. */
    suspend fun write(key: String, offset: Long, bytes: ByteArray): Boolean

    /** The SHA-256 of the incoming file [key], lowercase hex. */
    suspend fun sha256(key: String): String?

    /** Moves the incoming file [key] into storage as [name]; gives its path there. */
    suspend fun finish(key: String, name: String): String?

    /** Forgets the incoming file [key]. */
    suspend fun discard(key: String)

    /** [length] bytes of [file] from [offset] (fewer at its end). */
    suspend fun read(file: String, offset: Long, length: Int): ByteArray?

    suspend fun delete(file: String)

    /** A `content://` address another app can open [file] at, once granted. */
    fun uriOf(file: String): String
}

/** What a file to send became, once ready. */
sealed interface PreparedAttachment {
    /**
     * Copied into the app's storage at [file] (relative to it), compressed if it is a picture and
     * that was asked. [sha256] in lowercase hex.
     */
    data class Ready(
        val file: String,
        val name: String,
        val mime: String,
        val size: Long,
        val sha256: String,
        val width: Int? = null,
        val height: Int? = null,
    ) : PreparedAttachment

    /** Larger than [limit] bytes, as ready to send. */
    data class TooLarge(val size: Long, val limit: Long) : PreparedAttachment

    /** It could not be read; [detail] is for *Show SIP details*. */
    data class Unreadable(val detail: String) : PreparedAttachment
}

/** Makes a file the user picked ready to send. Android's content resolver in the app. */
fun interface AttachmentPreparer {
    /**
     * Reads [source] (a `content://` address), copies it into the app's storage, resizing a picture
     * to 2048 px and compressing it unless [originalQuality], and works out its size and SHA-256.
     */
    suspend fun prepare(source: String, originalQuality: Boolean, limit: Long): PreparedAttachment
}

/** Who an address is, for a conversation to say "Ana" instead of "102". The directory answers. */
fun interface PeerNames {
    /** A way to name addresses, again each time the people known change. */
    fun lookups(): Flow<(SipAddress) -> String?>

    companion object {
        /** Nobody is known: conversations show what the server says, or the user part. */
        val None = PeerNames { flowOf { _ -> null } }
    }
}

/**
 * The words above a file offer, for a person reading it on a phone that is not Sona: "📎
 * holiday.jpg · 184 KB — sent with Sona". In the user's language in the app.
 */
fun interface OfferCaption {
    fun caption(name: String, size: Long): String

    companion object {
        val English = OfferCaption { name, size ->
            "📎 $name · ${readableSize(size)} — sent with Sona"
        }
    }
}

/** A size as people read it: `512 B`, `184 KB`, `1.2 MB`. */
fun readableSize(bytes: Long): String =
    when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${(bytes + 512) / 1024} KB"
        else -> {
            val tenths = (bytes * 10 + 512 * 1024) / (1024 * 1024)
            if (tenths % 10 == 0L || tenths >= 100) "${(tenths + 5) / 10} MB"
            else "${tenths / 10}.${tenths % 10} MB"
        }
    }

/**
 * Where the camera writes a picture to send: a new place each time, which [AttachmentPreparer] can
 * read afterwards like any picked file.
 */
fun interface CameraCaptures {
    /** A `content://` address the camera app may write to, or `null` when none could be made. */
    suspend fun newCapture(): String?
}
