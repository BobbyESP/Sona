/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A SIP account as stored. No password: that is in the vault. */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val label: String,
    val color: Int,
    val username: String,
    val domain: String,
    val serverHost: String,
    val serverPort: Int?,
    /** `Udp`, `Tcp` or `Tls`, by name. */
    val transport: String,
    val displayName: String?,
    val authUsername: String?,
    val proxyHost: String?,
    val proxyPort: Int?,
    val expirySeconds: Long,
    val voicemailNumber: String,
    val enabled: Boolean,
    val isDefault: Boolean,
    val revision: Long,
    /** When it was added, in epoch milliseconds: the order accounts are listed in. */
    val createdAt: Long,
)

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY isDefault DESC, createdAt ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id") suspend fun get(id: String): AccountEntity?

    /** Inserts or replaces [entity], keeping when it was first added. */
    @Transaction
    suspend fun save(entity: AccountEntity) {
        upsert(entity.copy(createdAt = get(entity.id)?.createdAt ?: entity.createdAt))
    }

    @Upsert suspend fun upsert(entity: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id") suspend fun delete(id: String)

    @Transaction
    suspend fun setDefault(id: String) {
        clearDefault()
        markDefault(id)
    }

    @Query("UPDATE accounts SET isDefault = 0") suspend fun clearDefault()

    @Query("UPDATE accounts SET isDefault = 1 WHERE id = :id") suspend fun markDefault(id: String)
}
