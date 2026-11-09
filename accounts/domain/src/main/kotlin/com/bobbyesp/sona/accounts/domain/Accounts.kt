/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.accounts.domain

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.Secret
import com.bobbyesp.sona.credentials.api.CredentialVault
import com.bobbyesp.sona.sip.api.RegistrationState
import com.bobbyesp.sona.sip.api.SipEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Where accounts are kept: Room in the app, a list in tests. */
interface AccountsRepository {
    /** Every account, the default first, then in the order they were added. */
    val accounts: Flow<List<Account>>

    suspend fun get(id: AccountId): Account?

    suspend fun upsert(account: Account)

    suspend fun delete(id: AccountId)

    /** Makes [id] the only default account. */
    suspend fun setDefault(id: AccountId)
}

/** The default account, or `null` when there is none. */
fun AccountsRepository.defaultAccount(): Flow<Account?> =
    accounts
        .map { list -> list.firstOrNull { it.isDefault } ?: list.firstOrNull() }
        .distinctUntilChanged()

/** An account with whether it can call right now. */
data class AccountStatus(val account: Account, val registration: RegistrationState)

/** Every account with its registration, as the engine reports it. */
fun observeAccountStatuses(
    repository: AccountsRepository,
    engine: SipEngine,
): Flow<List<AccountStatus>> =
    combine(repository.accounts, engine.registrations) { accounts, registrations ->
        accounts.map { account ->
            AccountStatus(
                account = account,
                registration = registrations[account.id] ?: RegistrationState.Unregistered,
            )
        }
    }

sealed interface SaveOutcome {
    data class Saved(val account: Account) : SaveOutcome

    data class Invalid(val problems: Map<DraftField, DraftProblem>) : SaveOutcome

    /** A new account needs its password; an existing one keeps its own when none is given. */
    data object PasswordRequired : SaveOutcome
}

/**
 * Saves what the editor holds. The password goes to the vault first, then the account to the
 * repository with its revision bumped, so the engine, which follows the repository, always finds
 * the password it needs. The first account becomes the default.
 */
class SaveAccount(
    private val repository: AccountsRepository,
    private val vault: CredentialVault,
    private val newId: () -> AccountId,
) {
    suspend operator fun invoke(draft: AccountDraft, password: Secret?): SaveOutcome {
        val all = repository.accounts.first()
        val existing = draft.id?.let { id -> all.firstOrNull { it.id == id } }
        val color = existing?.color ?: AccountColors.next(all.map { it.color })

        val account =
            when (val validation = draft.validate(id = draft.id ?: newId(), color = color)) {
                is DraftValidation.Invalid -> return SaveOutcome.Invalid(validation.problems)
                is DraftValidation.Valid -> validation.account
            }
        val newPassword = password?.takeUnless { it.isEmpty }
        if (existing == null && newPassword == null) return SaveOutcome.PasswordRequired

        if (newPassword != null) vault.store(account.id, newPassword)
        val saved =
            account.copy(
                isDefault = existing?.isDefault ?: all.isEmpty(),
                revision = (existing?.revision ?: 0) + 1,
            )
        repository.upsert(saved)
        return SaveOutcome.Saved(saved)
    }
}

/** Deletes an account and its password; the next account, if any, becomes the default. */
class DeleteAccount(
    private val repository: AccountsRepository,
    private val vault: CredentialVault,
) {
    suspend operator fun invoke(id: AccountId) {
        val wasDefault = repository.get(id)?.isDefault == true
        repository.delete(id)
        vault.remove(id)
        if (wasDefault)
            repository.accounts.first().firstOrNull()?.let { repository.setDefault(it.id) }
    }
}

/** Turns an account on or off: off, it unregisters and cannot call, but keeps everything. */
class SetAccountEnabled(private val repository: AccountsRepository) {
    suspend operator fun invoke(id: AccountId, enabled: Boolean) {
        val account = repository.get(id) ?: return
        if (account.enabled == enabled) return
        repository.upsert(account.copy(enabled = enabled, revision = account.revision + 1))
    }
}
