/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.domain

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.SipAddress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Where conversations and messages are kept, and their only record (docs/messaging.md, Storage).
 * What changes many times a second (a transfer's chunks) has calls of its own, so the message is
 * not rewritten each time.
 */
interface MessageStore {
    val conversations: Flow<List<ConversationSummary>>

    fun messages(conversation: ConversationId): Flow<List<Message>>

    val unread: Flow<Int>

    suspend fun conversation(id: ConversationId): Conversation?

    suspend fun findConversation(account: AccountId, peer: SipAddress): Conversation?

    suspend fun openConversation(
        account: AccountId,
        peer: SipAddress,
        peerName: String?,
        at: Long,
    ): Conversation

    suspend fun updateConversation(conversation: Conversation)

    suspend fun deleteConversation(id: ConversationId)

    /** Returns [message] with its id. Its conversation moves up. */
    suspend fun add(message: Message): Message

    suspend fun message(id: MessageId): Message?

    suspend fun messageByEnvelope(
        conversation: ConversationId,
        direction: MessageDirection,
        envelopeId: String,
    ): Message?

    suspend fun update(message: Message)

    suspend fun delete(ids: Collection<MessageId>)

    /** Returns the messages that were unread. */
    suspend fun markRead(conversation: ConversationId): List<Message>

    /**
     * What was left unfinished: texts still sending, and files offered, going out or coming in. A
     * restart resumes them.
     */
    suspend fun unfinished(): List<Message>

    /** Which chunks of an incoming file arrived, one bit each, as last saved. */
    suspend fun receivedChunks(message: MessageId): ByteArray?

    suspend fun saveReceivedChunks(message: MessageId, bits: ByteArray, count: Int)

    /**
     * Keeps part [index] (from 1) of the incoming text [envelopeId]; returns every part kept so
     * far, by index.
     */
    suspend fun addPart(
        conversation: ConversationId,
        envelopeId: String,
        index: Int,
        total: Int,
        text: String,
        at: Long,
    ): Map<Int, String>

    /** Adds the whole text and forgets its parts, atomically. */
    suspend fun completeText(message: Message): Message
}

/**
 * Message files under `files/messages/`, named by paths relative to it. An incoming one is written
 * in place, under a [key], until whole. It never reads a whole file into memory, and failures (no
 * space, a file gone) are `false` or `null`, never thrown.
 */
interface AttachmentFiles {
    /** Makes the file for an incoming transfer, of [size] bytes; keeps what is already there. */
    suspend fun openIncoming(key: String, size: Long): Boolean

    suspend fun write(key: String, offset: Long, bytes: ByteArray): Boolean

    /** In lowercase hex. */
    suspend fun sha256(key: String): String?

    /** Moves the incoming file [key] into storage as [name]; returns its path there. */
    suspend fun finish(key: String, name: String): String?

    suspend fun discard(key: String)

    /** [length] bytes of [file] from [offset] (fewer at its end). */
    suspend fun read(file: String, offset: Long, length: Int): ByteArray?

    suspend fun delete(file: String)

    fun uriOf(file: String): String
}

sealed interface PreparedAttachment {
    /** [file] is relative to the app's storage; [sha256] is in lowercase hex. */
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

fun interface AttachmentPreparer {
    /**
     * Copies [source], a `content://` address, into the app's storage. A picture is resized to 2048
     * px and compressed unless [originalQuality].
     */
    suspend fun prepare(source: String, originalQuality: Boolean, limit: Long): PreparedAttachment
}

/** Names peers, so a conversation says "Ana" rather than "102"; the directory answers. */
fun interface PeerNames {
    /** Emits again when the known people change. */
    fun lookups(): Flow<(SipAddress) -> String?>

    companion object {
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
 * Each call gives a new place for the camera to write a picture, which [AttachmentPreparer] can
 * then read.
 */
fun interface CameraCaptures {
    /** A `content://` address, or `null` when none could be made. */
    suspend fun newCapture(): String?
}
