/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.accounts.data

import com.bobbyesp.sona.accounts.domain.Account
import com.bobbyesp.sona.accounts.domain.AccountsRepository
import com.bobbyesp.sona.core.database.AccountDao
import com.bobbyesp.sona.core.database.AccountEntity
import com.bobbyesp.sona.core.database.AccountOverrideEntity
import com.bobbyesp.sona.core.database.AccountWithOverrides
import com.bobbyesp.sona.core.database.SonaDatabase
import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.settings.Overrides
import com.bobbyesp.sona.core.model.settings.storedValueOf
import com.bobbyesp.sona.core.model.settings.toText
import com.bobbyesp.sona.sip.api.ServerEndpoint
import com.bobbyesp.sona.sip.api.Transport
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The accounts repository the app uses. Room stays an implementation detail. */
fun SonaDatabase.accountsRepository(
    clock: () -> Long = System::currentTimeMillis,
): AccountsRepository = RoomAccountsRepository(accountDao(), clock)

internal class RoomAccountsRepository(
    private val dao: AccountDao,
    private val clock: () -> Long,
) : AccountsRepository {

    override val accounts: Flow<List<Account>> =
        dao.observeAll().map { rows -> rows.map { it.toAccount() } }

    override suspend fun get(id: AccountId): Account? = dao.get(id.value)?.toAccount()

    override suspend fun upsert(account: Account) =
        dao.save(account.toEntity(createdAt = clock()), account.overrideEntities())

    override suspend fun delete(id: AccountId) = dao.delete(id.value)

    override suspend fun setDefault(id: AccountId) = dao.setDefault(id.value)
}

internal fun AccountWithOverrides.toAccount(): Account =
    account
        .toAccount()
        .copy(
            overrides =
                Overrides(
                    overrides
                        .mapNotNull { row -> storedValueOf(row.value)?.let { row.key to it } }
                        .toMap(),
                ),
        )

internal fun Account.overrideEntities(): List<AccountOverrideEntity> =
    overrides.stored.map { (key, value) -> AccountOverrideEntity(id.value, key, value.toText()) }

internal fun AccountEntity.toAccount(): Account {
    val transport = Transport.entries.firstOrNull { it.name == transport } ?: Transport.Udp
    return Account(
        id = AccountId(id),
        label = label,
        color = color,
        username = username,
        domain = domain,
        server = ServerEndpoint(serverHost, serverPort, transport),
        displayName = displayName,
        authUsername = authUsername,
        outboundProxy = proxyHost?.let { ServerEndpoint(it, proxyPort, transport) },
        expiry = expirySeconds.seconds,
        voicemailNumber = voicemailNumber,
        enabled = enabled,
        isDefault = isDefault,
        revision = revision,
    )
}

internal fun Account.toEntity(createdAt: Long): AccountEntity =
    AccountEntity(
        id = id.value,
        label = label,
        color = color,
        username = username,
        domain = domain,
        serverHost = server.host,
        serverPort = server.port,
        transport = server.transport.name,
        displayName = displayName,
        authUsername = authUsername,
        proxyHost = outboundProxy?.host,
        proxyPort = outboundProxy?.port,
        expirySeconds = expiry.inWholeSeconds,
        voicemailNumber = voicemailNumber,
        enabled = enabled,
        isDefault = isDefault,
        revision = revision,
        createdAt = createdAt,
    )
