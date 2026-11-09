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
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** A call in the history. Addresses are kept as SIP URIs, enums by name. */
@Entity(tableName = "call_records", indices = [Index("startedAt")])
data class CallRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: String?,
    /** `Incoming` or `Outgoing`. */
    val direction: String,
    /** A `CallOutcome` by name. */
    val outcome: String,
    val uri: String,
    val name: String?,
    val startedAt: Long,
    val durationMillis: Long,
    val statusCode: Int?,
    val statusText: String?,
    val seen: Boolean,
)

@Dao
interface CallRecordDao {
    @Query("SELECT * FROM call_records ORDER BY startedAt DESC, id DESC")
    fun observeAll(): Flow<List<CallRecordEntity>>

    @Query(
        "SELECT * FROM call_records WHERE outcome = 'Missed' AND seen = 0 " +
            "ORDER BY startedAt DESC, id DESC",
    )
    fun observeUnseenMissed(): Flow<List<CallRecordEntity>>

    @Insert suspend fun insert(entity: CallRecordEntity): Long

    @Query("DELETE FROM call_records WHERE id IN (:ids)") suspend fun delete(ids: Set<Long>)

    @Query("DELETE FROM call_records") suspend fun clear()

    @Query("UPDATE call_records SET seen = 1 WHERE seen = 0") suspend fun markSeen()

    @Query("DELETE FROM call_records WHERE startedAt < :epochMillis")
    suspend fun deleteBefore(epochMillis: Long)
}

/** A contact made in Sona. */
@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val favorite: Boolean,
)

/** One way to reach a contact; goes with the contact. */
@Entity(
    tableName = "contact_numbers",
    foreignKeys =
        [
            ForeignKey(
                entity = ContactEntity::class,
                parentColumns = ["id"],
                childColumns = ["contactId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices = [Index("contactId")],
)
data class ContactNumberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val label: String,
    val value: String,
    /** Keeps the order the user gave them in. */
    val position: Int,
)

data class ContactWithNumbers(
    @Embedded val contact: ContactEntity,
    @Relation(parentColumn = "id", entityColumn = "contactId")
    val numbers: List<ContactNumberEntity>,
)

@Dao
interface ContactDao {
    @Transaction
    @Query("SELECT * FROM contacts ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ContactWithNumbers>>

    @Transaction
    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun get(id: Long): ContactWithNumbers?

    @Insert suspend fun insert(entity: ContactEntity): Long

    @Query("UPDATE contacts SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Insert suspend fun insertNumbers(numbers: List<ContactNumberEntity>)

    @Query("DELETE FROM contact_numbers WHERE contactId = :id") suspend fun deleteNumbers(id: Long)

    @Query("DELETE FROM contacts WHERE id = :id") suspend fun delete(id: Long)

    @Query("UPDATE contacts SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    /** A new contact with its numbers, or the numbers and name of an existing one replaced. */
    @Transaction
    suspend fun save(contact: ContactEntity, numbers: List<ContactNumberEntity>): Long {
        val id =
            if (contact.id == 0L) insert(contact)
            else {
                rename(contact.id, contact.name)
                setFavorite(contact.id, contact.favorite)
                deleteNumbers(contact.id)
                contact.id
            }
        insertNumbers(numbers.map { it.copy(id = 0, contactId = id) })
        return id
    }
}
