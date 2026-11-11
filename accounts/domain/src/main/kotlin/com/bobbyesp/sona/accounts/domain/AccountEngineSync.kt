/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.accounts.domain

import com.bobbyesp.sona.core.model.Secret
import com.bobbyesp.sona.core.model.settings.AudioSettings
import com.bobbyesp.sona.core.model.settings.CallSettings
import com.bobbyesp.sona.core.model.settings.Override
import com.bobbyesp.sona.credentials.api.CredentialVault
import com.bobbyesp.sona.sip.api.AccountSpec
import com.bobbyesp.sona.sip.api.SipEngine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Keeps the engine's accounts equal to the stored ones: every change of the accounts (a save, a
 * deletion, turning one off) gives the engine the whole set again, with each password from the
 * vault. The engine keeps no account of its own, so this is the only way one reaches it.
 *
 * An account whose password cannot be opened is given to the engine disabled: it cannot register
 * without one, and the user is asked for it in the account's screen.
 */
class AccountEngineSync(
    private val repository: AccountsRepository,
    private val vault: CredentialVault,
    private val engine: SipEngine,
) {
    /** Runs until cancelled; start it once, in the application's scope. */
    suspend fun run() {
        repository.accounts.distinctUntilChanged().collect { accounts ->
            engine.setAccounts(accounts.map { it.toSpec(vault.read(it.id)) })
        }
    }
}

internal fun Account.toSpec(password: Secret?): AccountSpec =
    AccountSpec(
        id = id,
        label = label,
        identity = identity,
        password = password ?: Secret(""),
        registrar = server,
        displayName = displayName,
        authUsername = authUsername,
        outboundProxy = outboundProxy,
        expiry = expiry,
        enabled = enabled && password != null,
        codecs = overrides[AudioSettings.Codecs].valueOrNull()?.toCodecMimes(),
        dtmf = overrides[CallSettings.Dtmf].valueOrNull()?.toEngine(),
    )

private fun <T> Override<T>.valueOrNull(): T? = (this as? Override.Value)?.value
