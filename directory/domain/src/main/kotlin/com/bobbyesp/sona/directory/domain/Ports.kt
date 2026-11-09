/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.directory.domain

import kotlinx.coroutines.flow.Flow

/** The calls made and received, newest first. Room in the app, a list in tests. */
interface CallHistory {
    val records: Flow<List<CallRecord>>

    /** The missed calls the user has not seen yet, newest first. */
    val unseenMissed: Flow<List<CallRecord>>

    suspend fun add(record: CallRecord)

    suspend fun delete(ids: Set<Long>)

    suspend fun clear()

    /** Every missed call is seen: Recents was opened. */
    suspend fun markSeen()

    /** Forgets what began before [epochMillis] (the retention setting). */
    suspend fun deleteBefore(epochMillis: Long)
}

/** The contacts made in Sona. */
interface LocalContacts {
    val contacts: Flow<List<Contact>>

    suspend fun get(id: String): Contact?

    /** Inserts [contact] (a blank id) or replaces it; returns it with its id. */
    suspend fun save(contact: Contact): Contact

    suspend fun delete(id: String)

    suspend fun setFavorite(id: String, favorite: Boolean)
}

/** The phone's contacts, read only, and only once the user allowed it. */
interface DeviceContacts {
    /** Every contact with a number; empty while [granted] is false. */
    val contacts: Flow<List<Contact>>

    /** Whether the user allowed reading the phone's contacts. */
    val granted: Flow<Boolean>

    /** Reads again: the permission changed, or the contacts did. */
    suspend fun refresh()
}
