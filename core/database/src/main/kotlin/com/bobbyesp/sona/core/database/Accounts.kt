/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
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

/**
 * An account's own value for an option it may override (codecs, DTMF): no row, and the app's value
 * applies. [value] is the option's stored value as text with its kind (`s:G722,PCMU`), read back
 * through the option's declaration, so a value that means nothing any more is no override.
 */
@Entity(
    tableName = "account_overrides",
    primaryKeys = ["accountId", "key"],
    foreignKeys =
        [
            ForeignKey(
                entity = AccountEntity::class,
                parentColumns = ["id"],
                childColumns = ["accountId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
)
data class AccountOverrideEntity(val accountId: String, val key: String, val value: String)

/** An account with every option it overrides. */
data class AccountWithOverrides(
    @Embedded val account: AccountEntity,
    @Relation(parentColumn = "id", entityColumn = "accountId")
    val overrides: List<AccountOverrideEntity>,
)

@Dao
interface AccountDao {
    @Transaction
    @Query("SELECT * FROM accounts ORDER BY isDefault DESC, createdAt ASC")
    fun observeAll(): Flow<List<AccountWithOverrides>>

    @Transaction
    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: String): AccountWithOverrides?

    /**
     * Inserts or replaces [entity], keeping when it was first added, and makes its overrides
     * exactly [overrides].
     */
    @Transaction
    suspend fun save(entity: AccountEntity, overrides: List<AccountOverrideEntity>) {
        upsert(entity.copy(createdAt = get(entity.id)?.account?.createdAt ?: entity.createdAt))
        clearOverrides(entity.id)
        if (overrides.isNotEmpty()) insertOverrides(overrides)
    }

    @Upsert suspend fun upsert(entity: AccountEntity)

    @Query("DELETE FROM account_overrides WHERE accountId = :id")
    suspend fun clearOverrides(id: String)

    @Insert suspend fun insertOverrides(overrides: List<AccountOverrideEntity>)

    @Query("DELETE FROM accounts WHERE id = :id") suspend fun delete(id: String)

    @Transaction
    suspend fun setDefault(id: String) {
        clearDefault()
        markDefault(id)
    }

    @Query("UPDATE accounts SET isDefault = 0") suspend fun clearDefault()

    @Query("UPDATE accounts SET isDefault = 1 WHERE id = :id") suspend fun markDefault(id: String)
}
