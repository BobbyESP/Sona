/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.data

import com.bobbyesp.sona.core.database.AttachmentEntity
import com.bobbyesp.sona.core.database.ConversationEntity
import com.bobbyesp.sona.core.database.MessageDao
import com.bobbyesp.sona.core.database.MessageEntity
import com.bobbyesp.sona.core.database.MessagePartEntity
import com.bobbyesp.sona.core.database.MessageWithAttachment
import com.bobbyesp.sona.core.database.SonaDatabase
import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.messaging.domain.Attachment
import com.bobbyesp.sona.messaging.domain.Conversation
import com.bobbyesp.sona.messaging.domain.ConversationId
import com.bobbyesp.sona.messaging.domain.ConversationSummary
import com.bobbyesp.sona.messaging.domain.FailureReason
import com.bobbyesp.sona.messaging.domain.Message
import com.bobbyesp.sona.messaging.domain.MessageDirection
import com.bobbyesp.sona.messaging.domain.MessageFailure
import com.bobbyesp.sona.messaging.domain.MessageId
import com.bobbyesp.sona.messaging.domain.MessageStatus
import com.bobbyesp.sona.messaging.domain.MessageStore
import com.bobbyesp.sona.messaging.domain.PeerKind
import com.bobbyesp.sona.messaging.domain.TransferState
import com.bobbyesp.sona.sip.api.SipStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

fun SonaDatabase.messageStore(): MessageStore = RoomMessageStore(messageDao())

internal class RoomMessageStore(private val dao: MessageDao) : MessageStore {

    override val conversations: Flow<List<ConversationSummary>> =
        combine(dao.observeConversations(), dao.observeLastMessages()) { rows, lasts ->
                val last = lasts.associateBy { it.message.id }
                rows.mapNotNull { row ->
                    val conversation = row.conversation.toConversation() ?: return@mapNotNull null
                    ConversationSummary(
                        conversation = conversation,
                        last = row.lastMessageId?.let(last::get)?.toMessage(),
                        unread = row.unread,
                    )
                }
            }
            .distinctUntilChanged()

    override fun messages(conversation: ConversationId): Flow<List<Message>> =
        dao.observeMessages(conversation.value).map { rows -> rows.map { it.toMessage() } }

    override val unread: Flow<Int> = dao.observeUnread().distinctUntilChanged()

    override suspend fun conversation(id: ConversationId): Conversation? =
        dao.conversation(id.value)?.toConversation()

    override suspend fun findConversation(account: AccountId, peer: SipAddress): Conversation? =
        dao.findConversation(account.value, peer.uri)?.toConversation()

    override suspend fun openConversation(
        account: AccountId,
        peer: SipAddress,
        peerName: String?,
        at: Long,
    ): Conversation =
        dao.openConversation(
                ConversationEntity(
                    accountId = account.value,
                    peerUri = peer.uri,
                    peerName = peerName,
                    peerKind = PeerKind.Unknown.name,
                    lastActivityAt = at,
                ),
            )
            .let { it.toConversation() ?: Conversation(ConversationId(it.id), account, peer) }

    override suspend fun updateConversation(conversation: Conversation) =
        dao.updateConversation(
            conversation.id.value,
            conversation.peerName,
            conversation.peerKind.name,
        )

    override suspend fun deleteConversation(id: ConversationId) = dao.deleteConversation(id.value)

    override suspend fun add(message: Message): Message {
        val id = dao.add(message.toEntity(), message.attachment?.toEntity(0))
        return message.copy(id = MessageId(id))
    }

    override suspend fun message(id: MessageId): Message? = dao.message(id.value)?.toMessage()

    override suspend fun messageByEnvelope(
        conversation: ConversationId,
        direction: MessageDirection,
        envelopeId: String,
    ): Message? = dao.messageByEnvelope(conversation.value, direction.name, envelopeId)?.toMessage()

    override suspend fun update(message: Message) =
        dao.update(message.toEntity(), message.attachment?.toEntity(message.id.value))

    override suspend fun delete(ids: Collection<MessageId>) {
        // SQLite takes at most 999 parameters at once.
        ids.map { it.value }.chunked(500).forEach { dao.deleteMessages(it) }
    }

    override suspend fun markRead(conversation: ConversationId): List<Message> =
        dao.readAll(conversation.value).map { it.toMessage() }

    override suspend fun unfinished(): List<Message> =
        dao.unfinished(Unfinished.map { it.name }).map { it.toMessage() }

    override suspend fun receivedChunks(message: MessageId): ByteArray? = dao.chunks(message.value)

    override suspend fun saveReceivedChunks(message: MessageId, bits: ByteArray, count: Int) =
        dao.saveChunks(message.value, bits, count)

    override suspend fun addPart(
        conversation: ConversationId,
        envelopeId: String,
        index: Int,
        total: Int,
        text: String,
        at: Long,
    ): Map<Int, String> =
        dao.addPart(MessagePartEntity(conversation.value, envelopeId, index, total, text, at))
            .associate { it.position to it.text }

    override suspend fun completeText(message: Message): Message =
        message.copy(id = MessageId(dao.complete(message.toEntity())))

    private companion object {
        /** What a restart resumes; the same as the in-memory store's. */
        val Unfinished =
            listOf(
                TransferState.Offered,
                TransferState.Sending,
                TransferState.Confirming,
                TransferState.Receiving,
                TransferState.Verifying,
            )
    }
}

private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: fallback

/** `null` for a row that cannot be read (an address that does not parse). */
internal fun ConversationEntity.toConversation(): Conversation? {
    val peer = SipAddress.parse(peerUri) ?: return null
    return Conversation(
        id = ConversationId(id),
        account = AccountId(accountId),
        peer = peer,
        peerName = peerName,
        peerKind = enumOr(peerKind, PeerKind.Unknown),
        lastActivityAt = lastActivityAt,
    )
}

internal fun MessageWithAttachment.toMessage(): Message {
    val row = message
    val direction = enumOr(row.direction, MessageDirection.Incoming)
    return Message(
        id = MessageId(row.id),
        conversation = ConversationId(row.conversationId),
        direction = direction,
        text = row.text,
        at = row.at,
        status = enumOr(row.status, MessageStatus.Failed),
        read = row.read,
        envelopeId = row.envelopeId,
        failure =
            row.failureReason?.let { reason ->
                MessageFailure(
                    reason = enumOr(reason, FailureReason.Rejected),
                    status = row.failureCode?.let { SipStatus(it, row.failurePhrase.orEmpty()) },
                    detail = row.failureDetail,
                )
            },
        attachment = attachment?.toAttachment(),
        parts = row.parts,
        partsSent = row.partsSent,
        sizeLimit = row.sizeLimit,
    )
}

internal fun AttachmentEntity.toAttachment() =
    Attachment(
        transferId = transferId,
        name = name,
        mime = mime,
        size = size,
        sha256 = sha256,
        chunkSize = chunkSize,
        chunks = chunks,
        width = width,
        height = height,
        state = enumOr(state, TransferState.Failed),
        done = done,
        peerReceived = peerReceived,
        file = file,
    )

internal fun Message.toEntity() =
    MessageEntity(
        id = id.value,
        conversationId = conversation.value,
        direction = direction.name,
        text = text,
        at = at,
        status = status.name,
        read = read,
        envelopeId = envelopeId,
        failureReason = failure?.reason?.name,
        failureCode = failure?.status?.code,
        failurePhrase = failure?.status?.phrase,
        failureDetail = failure?.detail,
        parts = parts,
        partsSent = partsSent,
        sizeLimit = sizeLimit,
    )

internal fun Attachment.toEntity(messageId: Long) =
    AttachmentEntity(
        messageId = messageId,
        transferId = transferId,
        name = name,
        mime = mime,
        size = size,
        sha256 = sha256,
        chunkSize = chunkSize,
        chunks = chunks,
        width = width,
        height = height,
        state = state.name,
        done = done,
        peerReceived = peerReceived,
        file = file,
    )
