/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.credentials.api

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.Secret

/**
 * Where SIP passwords live, and only there: never in the accounts' database, the settings, a log or
 * an export. The implementation decides how they are sealed (an Android Keystore key today).
 */
interface CredentialVault {
    suspend fun store(account: AccountId, secret: Secret)

    /**
     * The password of [account], or `null` when there is none or it can no longer be opened (the
     * key was lost, as after restoring a backup on another phone): the user types it again.
     */
    suspend fun read(account: AccountId): Secret?

    suspend fun remove(account: AccountId)
}

/** A vault in memory, for tests and previews. */
class InMemoryCredentialVault : CredentialVault {
    private val secrets = mutableMapOf<AccountId, Secret>()

    override suspend fun store(account: AccountId, secret: Secret) {
        secrets[account] = secret
    }

    override suspend fun read(account: AccountId): Secret? = secrets[account]

    override suspend fun remove(account: AccountId) {
        secrets.remove(account)
    }
}
