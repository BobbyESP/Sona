/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.accounts.domain

import com.bobbyesp.sona.core.model.AccountId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Accounts over a list, ordered as Room orders them (the default first): for tests and previews.
 */
class InMemoryAccountsRepository : AccountsRepository {
    private val state = MutableStateFlow<List<Account>>(emptyList())

    override val accounts = state

    override suspend fun get(id: AccountId) = state.value.firstOrNull { it.id == id }

    override suspend fun upsert(account: Account) = state.update { list ->
        val replaced = list.map { if (it.id == account.id) account else it }
        (if (list.any { it.id == account.id }) replaced else list + account).sortedForDisplay()
    }

    override suspend fun delete(id: AccountId) = state.update { list ->
        list.filter { it.id != id }
    }

    override suspend fun setDefault(id: AccountId) = state.update { list ->
        list.map { it.copy(isDefault = it.id == id) }.sortedForDisplay()
    }

    private fun List<Account>.sortedForDisplay() = sortedByDescending { it.isDefault }
}
