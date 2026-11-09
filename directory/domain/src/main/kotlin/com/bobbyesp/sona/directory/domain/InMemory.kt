/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.directory.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** The history in memory: for tests and the demo flavor. */
class InMemoryCallHistory(initial: List<CallRecord> = emptyList()) : CallHistory {
    private val all = MutableStateFlow(initial.sortedByDescending { it.startedAt })
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    override val records: Flow<List<CallRecord>> = all
    override val unseenMissed: Flow<List<CallRecord>> = all.map { list ->
        list.filter { it.isMissed && !it.seen }
    }

    override suspend fun add(record: CallRecord) {
        val stored = record.copy(id = nextId++)
        all.update { (it + stored).sortedByDescending(CallRecord::startedAt) }
    }

    override suspend fun delete(ids: Set<Long>) = all.update { list ->
        list.filter { it.id !in ids }
    }

    override suspend fun clear() = all.update { emptyList() }

    override suspend fun markSeen() = all.update { list -> list.map { it.copy(seen = true) } }

    override suspend fun deleteBefore(epochMillis: Long) = all.update { list ->
        list.filter { it.startedAt >= epochMillis }
    }
}

/** Contacts in memory: for tests and the demo flavor. */
class InMemoryLocalContacts(initial: List<Contact> = emptyList()) : LocalContacts {
    private val all = MutableStateFlow(initial)
    private var nextId = initial.size + 1L

    override val contacts: Flow<List<Contact>> = all

    override suspend fun get(id: String): Contact? = all.value.firstOrNull { it.id == id }

    override suspend fun save(contact: Contact): Contact {
        val stored = if (contact.id.isBlank()) contact.copy(id = "local:${nextId++}") else contact
        all.update { list -> list.filter { it.id != stored.id } + stored }
        return stored
    }

    override suspend fun delete(id: String) = all.update { list -> list.filter { it.id != id } }

    override suspend fun setFavorite(id: String, favorite: Boolean) = all.update { list ->
        list.map { if (it.id == id) it.copy(favorite = favorite) else it }
    }
}

/** The phone's contacts, as a test says they are. */
class FakeDeviceContacts(contacts: List<Contact> = emptyList(), granted: Boolean = true) :
    DeviceContacts {
    private val shown = MutableStateFlow(contacts)
    private val allowed = MutableStateFlow(granted)

    override val contacts: Flow<List<Contact>> =
        combine(shown, allowed) { list, ok -> if (ok) list else emptyList() }
    override val granted: Flow<Boolean> = allowed

    fun allow(value: Boolean) {
        allowed.value = value
    }

    override suspend fun refresh() = Unit
}
