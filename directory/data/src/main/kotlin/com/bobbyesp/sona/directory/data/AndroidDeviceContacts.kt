/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.directory.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.SipAddress as SipAddressKind
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import androidx.core.content.ContextCompat
import com.bobbyesp.sona.directory.domain.Contact
import com.bobbyesp.sona.directory.domain.ContactNumber
import com.bobbyesp.sona.directory.domain.ContactSource
import com.bobbyesp.sona.directory.domain.DeviceContacts
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** The phone's contacts, read through ContactsContract once the user allowed it. */
fun Context.deviceContacts(io: CoroutineDispatcher): DeviceContacts =
    AndroidDeviceContacts(applicationContext, io)

internal class AndroidDeviceContacts(
    private val context: Context,
    private val io: CoroutineDispatcher,
) : DeviceContacts {
    /** Bumped when there is something new to read: the permission, or the contacts. */
    private val version = MutableStateFlow(0)

    override val granted: Flow<Boolean> = version.map { context.canReadContacts() }

    override val contacts: Flow<List<Contact>> = version.map {
        if (context.canReadContacts()) read() else emptyList()
    }

    override suspend fun refresh() {
        version.value += 1
    }

    // The contacts change under us (a sync, an edit in the phone app): read again.
    init {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    version.value += 1
                }
            }
        runCatching {
            context.contentResolver.registerContentObserver(Contacts.CONTENT_URI, true, observer)
        }
    }

    private suspend fun read(): List<Contact> =
        withContext(io) {
            val byId = LinkedHashMap<Long, Pair<String, MutableList<ContactNumber>>>()
            val projection =
                arrayOf(
                    Data.CONTACT_ID,
                    Data.DISPLAY_NAME_PRIMARY,
                    Data.MIMETYPE,
                    Data.DATA1,
                    Data.DATA2,
                    Data.DATA3,
                )
            val selection = "${Data.MIMETYPE} IN (?, ?)"
            val args = arrayOf(Phone.CONTENT_ITEM_TYPE, SipAddressKind.CONTENT_ITEM_TYPE)
            runCatching {
                context.contentResolver
                    .query(
                        Data.CONTENT_URI,
                        projection,
                        selection,
                        args,
                        "${Data.DISPLAY_NAME_PRIMARY} COLLATE NOCASE",
                    )
                    ?.use { cursor ->
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(0)
                            val name = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                            val value = cursor.getString(3)?.takeIf { it.isNotBlank() } ?: continue
                            val isPhone = cursor.getString(2) == Phone.CONTENT_ITEM_TYPE
                            val label =
                                if (isPhone) {
                                    Phone.getTypeLabel(
                                            context.resources,
                                            cursor.getInt(4),
                                            cursor.getString(5),
                                        )
                                        .toString()
                                } else "SIP"
                            byId.getOrPut(id) { name to mutableListOf() }.second +=
                                ContactNumber(label, value)
                        }
                    }
            }
            byId.map { (id, entry) ->
                Contact(
                    id = "device:$id",
                    name = entry.first,
                    numbers = entry.second.distinctBy { it.value.dialableKey() },
                    source = ContactSource.Device,
                )
            }
        }
}

/** The same phone number written two ways ("600 11 22", "+34600…") is one number. */
private fun String.dialableKey(): String = filter { it.isDigit() || it == '+' }.ifEmpty { this }

private fun Context.canReadContacts() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED
