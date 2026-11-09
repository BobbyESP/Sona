/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.directory.data

import com.bobbyesp.sona.core.database.ContactDao
import com.bobbyesp.sona.core.database.ContactEntity
import com.bobbyesp.sona.core.database.ContactNumberEntity
import com.bobbyesp.sona.core.database.ContactWithNumbers
import com.bobbyesp.sona.core.database.SonaDatabase
import com.bobbyesp.sona.directory.domain.Contact
import com.bobbyesp.sona.directory.domain.ContactNumber
import com.bobbyesp.sona.directory.domain.ContactSource
import com.bobbyesp.sona.directory.domain.LocalContacts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val PREFIX = "local:"

/** The contacts made in Sona, on Room. */
fun SonaDatabase.localContacts(): LocalContacts = RoomLocalContacts(contactDao())

internal class RoomLocalContacts(private val dao: ContactDao) : LocalContacts {
    override val contacts: Flow<List<Contact>> =
        dao.observeAll().map { rows -> rows.map { it.toContact() } }

    override suspend fun get(id: String): Contact? = id.rowId()?.let { dao.get(it) }?.toContact()

    override suspend fun save(contact: Contact): Contact {
        val rowId = contact.id.rowId() ?: 0L
        val name = contact.name.trim()
        val saved =
            dao.save(
                ContactEntity(id = rowId, name = name, favorite = contact.favorite),
                contact.numbers.mapIndexed { index, number ->
                    ContactNumberEntity(
                        contactId = rowId,
                        label = number.label,
                        value = number.value.trim(),
                        position = index,
                    )
                },
            )
        return contact.copy(id = PREFIX + saved, name = name)
    }

    override suspend fun delete(id: String) {
        id.rowId()?.let { dao.delete(it) }
    }

    override suspend fun setFavorite(id: String, favorite: Boolean) {
        id.rowId()?.let { dao.setFavorite(it, favorite) }
    }
}

private fun String.rowId(): Long? =
    if (startsWith(PREFIX)) removePrefix(PREFIX).toLongOrNull() else null

internal fun ContactWithNumbers.toContact() =
    Contact(
        id = PREFIX + contact.id,
        name = contact.name,
        numbers = numbers.sortedBy { it.position }.map { ContactNumber(it.label, it.value) },
        favorite = contact.favorite,
        source = ContactSource.Local,
    )
