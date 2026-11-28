/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/* Messages (docs/messaging.md, Storage). Enums are kept by name, addresses as SIP URIs. */

/** One per account and peer. */
@Entity(
    tableName = "conversations",
    indices = [Index(value = ["accountId", "peerUri"], unique = true), Index("lastActivityAt")],
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: String,
    val peerUri: String,
    val peerName: String?,
    /** `Unknown`, `Plain` or `Sona`. */
    val peerKind: String,
    val lastActivityAt: Long,
)

@Entity(
    tableName = "messages",
    foreignKeys =
        [
            ForeignKey(
                entity = ConversationEntity::class,
                parentColumns = ["id"],
                childColumns = ["conversationId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices =
        [
            Index(value = ["conversationId", "at"]),
            Index(value = ["conversationId", "direction", "envelopeId"]),
            Index("status"),
        ],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    /** `Incoming` or `Outgoing`. */
    val direction: String,
    val text: String,
    val at: Long,
    val status: String,
    val read: Boolean,
    val envelopeId: String?,
    val failureReason: String?,
    val failureCode: Int?,
    val failurePhrase: String?,
    val failureDetail: String?,
    val parts: Int,
    val partsSent: Int,
    val sizeLimit: Int?,
)

@Entity(
    tableName = "attachments",
    foreignKeys =
        [
            ForeignKey(
                entity = MessageEntity::class,
                parentColumns = ["id"],
                childColumns = ["messageId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices = [Index("state")],
)
data class AttachmentEntity(
    @PrimaryKey val messageId: Long,
    val transferId: String,
    val name: String,
    val mime: String,
    val size: Long,
    val sha256: String,
    val chunkSize: Int,
    val chunks: Int,
    val width: Int?,
    val height: Int?,
    val state: String,
    val done: Int,
    val peerReceived: Int,
    /** Relative to `files/messages/`. */
    val file: String?,
)

/**
 * Which chunks of an incoming file arrived, one bit each. A table of its own so that listing a
 * conversation never reads them.
 */
@Entity(
    tableName = "transfer_chunks",
    foreignKeys =
        [
            ForeignKey(
                entity = MessageEntity::class,
                parentColumns = ["id"],
                childColumns = ["messageId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
)
data class TransferChunksEntity(@PrimaryKey val messageId: Long, val bits: ByteArray) {
    override fun equals(other: Any?) =
        other is TransferChunksEntity &&
            other.messageId == messageId &&
            other.bits.contentEquals(bits)

    override fun hashCode() = messageId.hashCode() * 31 + bits.contentHashCode()
}

/** A part of a long incoming text, kept until every part came. */
@Entity(
    tableName = "message_parts",
    primaryKeys = ["conversationId", "envelopeId", "position"],
    foreignKeys =
        [
            ForeignKey(
                entity = ConversationEntity::class,
                parentColumns = ["id"],
                childColumns = ["conversationId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
)
data class MessagePartEntity(
    val conversationId: Long,
    val envelopeId: String,
    val position: Int,
    val total: Int,
    val text: String,
    val at: Long,
)

data class MessageWithAttachment(
    @Embedded val message: MessageEntity,
    @Relation(parentColumn = "id", entityColumn = "messageId") val attachment: AttachmentEntity?,
)

data class ConversationRow(
    @Embedded val conversation: ConversationEntity,
    val unread: Int,
    val lastMessageId: Long?,
)

@Dao
interface MessageDao {
    @Query(
        "SELECT c.*, " +
            "(SELECT COUNT(*) FROM messages m WHERE m.conversationId = c.id " +
            "AND m.direction = 'Incoming' AND m.read = 0) AS unread, " +
            "(SELECT MAX(m.id) FROM messages m WHERE m.conversationId = c.id) AS lastMessageId " +
            "FROM conversations c ORDER BY c.lastActivityAt DESC, c.id DESC",
    )
    fun observeConversations(): Flow<List<ConversationRow>>

    @Transaction
    @Query(
        "SELECT * FROM messages WHERE id IN (SELECT MAX(id) FROM messages GROUP BY conversationId)",
    )
    fun observeLastMessages(): Flow<List<MessageWithAttachment>>

    @Transaction
    @Query("SELECT * FROM messages WHERE conversationId = :conversation ORDER BY at, id")
    fun observeMessages(conversation: Long): Flow<List<MessageWithAttachment>>

    @Query("SELECT COUNT(*) FROM messages WHERE direction = 'Incoming' AND read = 0")
    fun observeUnread(): Flow<Int>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun conversation(id: Long): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE accountId = :account AND peerUri = :peer")
    suspend fun findConversation(account: String, peer: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertConversation(entity: ConversationEntity): Long

    @Query("UPDATE conversations SET peerName = :name, peerKind = :kind WHERE id = :id")
    suspend fun updateConversation(id: Long, name: String?, kind: String)

    @Query("DELETE FROM conversations WHERE id = :id") suspend fun deleteConversation(id: Long)

    @Query(
        "UPDATE conversations SET lastActivityAt = MAX(lastActivityAt, :at) WHERE id = :id",
    )
    suspend fun touch(id: Long, at: Long)

    @Insert suspend fun insertMessage(entity: MessageEntity): Long

    @Update suspend fun updateMessage(entity: MessageEntity)

    @Upsert suspend fun upsertAttachment(entity: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE messageId = :id") suspend fun deleteAttachment(id: Long)

    @Transaction
    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun message(id: Long): MessageWithAttachment?

    @Transaction
    @Query(
        "SELECT * FROM messages WHERE conversationId = :conversation AND direction = :direction " +
            "AND envelopeId = :envelopeId ORDER BY id LIMIT 1",
    )
    suspend fun messageByEnvelope(
        conversation: Long,
        direction: String,
        envelopeId: String,
    ): MessageWithAttachment?

    @Query("DELETE FROM messages WHERE id IN (:ids)") suspend fun deleteMessages(ids: List<Long>)

    @Transaction
    @Query(
        "SELECT * FROM messages WHERE conversationId = :conversation " +
            "AND direction = 'Incoming' AND read = 0",
    )
    suspend fun unreadIn(conversation: Long): List<MessageWithAttachment>

    @Query(
        "UPDATE messages SET read = 1 WHERE conversationId = :conversation " +
            "AND direction = 'Incoming' AND read = 0",
    )
    suspend fun markRead(conversation: Long)

    @Transaction
    @Query(
        "SELECT m.* FROM messages m LEFT JOIN attachments a ON a.messageId = m.id " +
            "WHERE (m.direction = 'Outgoing' AND m.status = 'Sending') OR a.state IN (:states) " +
            "ORDER BY m.id",
    )
    suspend fun unfinished(states: List<String>): List<MessageWithAttachment>

    @Query("SELECT bits FROM transfer_chunks WHERE messageId = :id")
    suspend fun chunks(id: Long): ByteArray?

    @Upsert suspend fun upsertChunks(entity: TransferChunksEntity)

    @Query("UPDATE attachments SET done = :count WHERE messageId = :id")
    suspend fun setDone(id: Long, count: Int)

    @Upsert suspend fun upsertPart(entity: MessagePartEntity)

    @Query("SELECT * FROM message_parts WHERE conversationId = :conversation AND envelopeId = :id")
    suspend fun parts(conversation: Long, id: String): List<MessagePartEntity>

    @Query("DELETE FROM message_parts WHERE conversationId = :conversation AND envelopeId = :id")
    suspend fun deleteParts(conversation: Long, id: String)

    @Transaction
    suspend fun openConversation(entity: ConversationEntity): ConversationEntity {
        findConversation(entity.accountId, entity.peerUri)?.let {
            return it
        }
        val id = insertConversation(entity)
        return conversation(id) ?: findConversation(entity.accountId, entity.peerUri)!!
    }

    /** Its conversation moves up. Returns the message's id. */
    @Transaction
    suspend fun add(message: MessageEntity, attachment: AttachmentEntity?): Long {
        val id = insertMessage(message.copy(id = 0))
        attachment?.let { upsertAttachment(it.copy(messageId = id)) }
        touch(message.conversationId, message.at)
        return id
    }

    @Transaction
    suspend fun update(message: MessageEntity, attachment: AttachmentEntity?) {
        updateMessage(message)
        if (attachment != null) upsertAttachment(attachment) else deleteAttachment(message.id)
    }

    @Transaction
    suspend fun readAll(conversation: Long): List<MessageWithAttachment> {
        val unread = unreadIn(conversation)
        if (unread.isNotEmpty()) markRead(conversation)
        return unread
    }

    @Transaction
    suspend fun saveChunks(id: Long, bits: ByteArray, count: Int) {
        upsertChunks(TransferChunksEntity(id, bits))
        setDone(id, count)
    }

    @Transaction
    suspend fun addPart(part: MessagePartEntity): List<MessagePartEntity> {
        upsertPart(part)
        return parts(part.conversationId, part.envelopeId)
    }

    @Transaction
    suspend fun complete(message: MessageEntity): Long {
        val id = add(message, null)
        deleteParts(message.conversationId, message.envelopeId.orEmpty())
        return id
    }
}
