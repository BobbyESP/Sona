/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.domain

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.SipAddress
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Conversations and messages in memory, ordered and counted as Room does: for tests and previews.
 */
class InMemoryMessageStore : MessageStore {
    private data class State(
        val conversations: Map<ConversationId, Conversation> = emptyMap(),
        val messages: Map<MessageId, Message> = emptyMap(),
        val received: Map<MessageId, ByteArray> = emptyMap(),
        val parts: Map<Triple<ConversationId, String, Int>, String> = emptyMap(),
    )

    private val state = MutableStateFlow(State())
    private val nextConversation = AtomicLong()
    private val nextMessage = AtomicLong()

    val all: Map<MessageId, Message>
        get() = state.value.messages

    override val conversations: Flow<List<ConversationSummary>> = state.map { now ->
        now.conversations.values
            .sortedWith(
                compareByDescending<Conversation> { it.lastActivityAt }
                    .thenByDescending { it.id.value },
            )
            .map { conversation ->
                val messages = now.messages.values.filter { it.conversation == conversation.id }
                ConversationSummary(
                    conversation = conversation,
                    last = messages.maxByOrNull { it.id.value },
                    unread = messages.count { !it.outgoing && !it.read },
                )
            }
    }

    override fun messages(conversation: ConversationId): Flow<List<Message>> =
        state
            .map { now ->
                now.messages.values
                    .filter { it.conversation == conversation }
                    .sortedWith(compareBy<Message> { it.at }.thenBy { it.id.value })
            }
            .distinctUntilChanged()

    override val unread: Flow<Int> =
        state
            .map { now -> now.messages.values.count { !it.outgoing && !it.read } }
            .distinctUntilChanged()

    override suspend fun conversation(id: ConversationId): Conversation? =
        state.value.conversations[id]

    override suspend fun findConversation(account: AccountId, peer: SipAddress): Conversation? =
        state.value.conversations.values.firstOrNull { it.account == account && it.peer == peer }

    override suspend fun openConversation(
        account: AccountId,
        peer: SipAddress,
        peerName: String?,
        at: Long,
    ): Conversation {
        findConversation(account, peer)?.let {
            return it
        }
        val made =
            Conversation(
                id = ConversationId(nextConversation.incrementAndGet()),
                account = account,
                peer = peer,
                peerName = peerName,
                lastActivityAt = at,
            )
        state.update { it.copy(conversations = it.conversations + (made.id to made)) }
        return made
    }

    override suspend fun updateConversation(conversation: Conversation) = state.update {
        if (conversation.id !in it.conversations) it
        else it.copy(conversations = it.conversations + (conversation.id to conversation))
    }

    override suspend fun deleteConversation(id: ConversationId) = state.update { now ->
        val gone = now.messages.values.filter { it.conversation == id }.map { it.id }.toSet()
        now.copy(
            conversations = now.conversations - id,
            messages = now.messages - gone,
            received = now.received - gone,
            parts = now.parts.filterKeys { it.first != id },
        )
    }

    override suspend fun add(message: Message): Message {
        val added = message.copy(id = MessageId(nextMessage.incrementAndGet()))
        state.update { now ->
            val conversation = now.conversations[message.conversation]
            now.copy(
                messages = now.messages + (added.id to added),
                conversations =
                    if (conversation == null) now.conversations
                    else
                        now.conversations +
                            (conversation.id to
                                conversation.copy(
                                    lastActivityAt = maxOf(conversation.lastActivityAt, message.at),
                                )),
            )
        }
        return added
    }

    override suspend fun message(id: MessageId): Message? = state.value.messages[id]

    override suspend fun messageByEnvelope(
        conversation: ConversationId,
        direction: MessageDirection,
        envelopeId: String,
    ): Message? =
        state.value.messages.values.firstOrNull {
            it.conversation == conversation &&
                it.direction == direction &&
                it.envelopeId == envelopeId
        }

    override suspend fun update(message: Message) = state.update { now ->
        if (message.id !in now.messages) now
        else now.copy(messages = now.messages + (message.id to message))
    }

    override suspend fun delete(ids: Collection<MessageId>) = state.update { now ->
        now.copy(messages = now.messages - ids.toSet(), received = now.received - ids.toSet())
    }

    override suspend fun markRead(conversation: ConversationId): List<Message> {
        val unread =
            state.value.messages.values.filter {
                it.conversation == conversation && !it.outgoing && !it.read
            }
        state.update { now ->
            now.copy(messages = now.messages + unread.associate { it.id to it.copy(read = true) })
        }
        return unread
    }

    override suspend fun unfinished(): List<Message> =
        state.value.messages.values
            .filter { message ->
                (message.outgoing && message.status == MessageStatus.Sending) ||
                    message.attachment?.state in Unfinished
            }
            .sortedBy { it.id.value }

    override suspend fun receivedChunks(message: MessageId): ByteArray? =
        state.value.received[message]

    override suspend fun saveReceivedChunks(message: MessageId, bits: ByteArray, count: Int) =
        state.update { now ->
            val known = now.messages[message] ?: return@update now
            now.copy(
                received = now.received + (message to bits.copyOf()),
                messages =
                    now.messages +
                        (message to known.copy(attachment = known.attachment?.copy(done = count))),
            )
        }

    override suspend fun addPart(
        conversation: ConversationId,
        envelopeId: String,
        index: Int,
        total: Int,
        text: String,
        at: Long,
    ): Map<Int, String> {
        state.update {
            it.copy(parts = it.parts + (Triple(conversation, envelopeId, index) to text))
        }
        return state.value.parts
            .filterKeys { it.first == conversation && it.second == envelopeId }
            .mapKeys { it.key.third }
    }

    override suspend fun completeText(message: Message): Message {
        val added = add(message)
        state.update { now ->
            now.copy(
                parts =
                    now.parts.filterKeys {
                        it.first != message.conversation || it.second != message.envelopeId
                    },
            )
        }
        return added
    }

    val partsKept: Int
        get() = state.value.parts.size

    companion object {
        /** The transfer states a restart resumes, as Room's query has them. */
        val Unfinished =
            setOf(
                TransferState.Offered,
                TransferState.Sending,
                TransferState.Confirming,
                TransferState.Receiving,
                TransferState.Verifying,
            )
    }
}

/** Files in memory, for tests and previews; [pick] stands in for the user picking one. */
class InMemoryAttachmentFiles : AttachmentFiles {
    val stored = ConcurrentHashMap<String, ByteArray>()
    private val incoming = ConcurrentHashMap<String, ByteArray>()
    private val picked = ConcurrentHashMap<String, Pair<String, ByteArray>>()
    private val names = AtomicLong()

    /** Whether writing fails, as a full disk would. */
    @Volatile var full = false

    val partial: Map<String, ByteArray>
        get() = incoming

    override suspend fun openIncoming(key: String, size: Long): Boolean {
        if (full || size > Int.MAX_VALUE) return false
        incoming.putIfAbsent(key, ByteArray(size.toInt()))
        return true
    }

    override suspend fun write(key: String, offset: Long, bytes: ByteArray): Boolean {
        val file = incoming[key] ?: return false
        if (full || offset + bytes.size > file.size) return false
        bytes.copyInto(file, offset.toInt())
        return true
    }

    override suspend fun sha256(key: String): String? = incoming[key]?.let(::sha256Of)

    override suspend fun finish(key: String, name: String): String? {
        val bytes = incoming.remove(key) ?: return null
        val path = "received/$key/$name"
        stored[path] = bytes
        return path
    }

    override suspend fun discard(key: String) {
        incoming.remove(key)
    }

    override suspend fun read(file: String, offset: Long, length: Int): ByteArray? {
        val bytes = stored[file] ?: return null
        if (offset > bytes.size) return null
        return bytes.copyOfRange(
            offset.toInt(),
            minOf(bytes.size.toLong(), offset + length).toInt(),
        )
    }

    override suspend fun delete(file: String) {
        stored.remove(file)
    }

    override fun uriOf(file: String): String = "memory://messages/$file"

    fun pick(source: String, name: String, bytes: ByteArray) {
        picked[source] = name to bytes
    }

    val preparer = AttachmentPreparer { source, _, limit ->
        val (name, bytes) =
            picked[source] ?: return@AttachmentPreparer PreparedAttachment.Unreadable("No $source")
        if (bytes.size > limit)
            return@AttachmentPreparer PreparedAttachment.TooLarge(bytes.size.toLong(), limit)
        val path = "sent/${names.incrementAndGet()}/$name"
        stored[path] = bytes
        PreparedAttachment.Ready(
            file = path,
            name = name,
            mime = if (name.endsWith(".jpg")) "image/jpeg" else "application/octet-stream",
            size = bytes.size.toLong(),
            sha256 = sha256Of(bytes),
        )
    }

    companion object {
        fun sha256Of(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                "%02x".format(it)
            }
    }
}
