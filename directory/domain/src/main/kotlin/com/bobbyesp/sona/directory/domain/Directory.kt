/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.directory.domain

import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.core.model.settings.ContactsSettings
import com.bobbyesp.sona.core.model.settings.HistoryRetention
import com.bobbyesp.sona.core.model.settings.SettingsStore
import java.text.Normalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** Someone the keypad offers while a number is typed. */
data class Suggestion(
    val name: String?,
    /** What is dialed when it is chosen: the number as the contact has it. */
    val number: String,
    val fromContact: Boolean,
)

/**
 * Sona's people in one list: its own contacts and the phone's, sorted by name. The one place that
 * knows there are two sources; the screens ask it and never name where a contact came from.
 */
class Directory(
    private val local: LocalContacts,
    private val device: DeviceContacts,
    private val history: CallHistory,
    private val settings: SettingsStore,
) {
    /** Every contact, Sona's and the phone's, A to Z. */
    val contacts: Flow<List<Contact>> =
        combine(
            local.contacts,
            device.contacts,
            settings.observe(ContactsSettings.UseDeviceContacts),
        ) { mine, theirs, useDevice ->
            (mine + if (useDevice) theirs else emptyList()).sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            )
        }

    val deviceGranted: Flow<Boolean> = device.granted

    suspend fun refreshDevice() = device.refresh()

    suspend fun contactFor(address: SipAddress): Contact? = contacts.first().find(address)

    /** The name of whoever [address] belongs to, if it is a contact. */
    suspend fun nameFor(address: SipAddress): String? = contactFor(address)?.name

    /**
     * What [input] could be: contacts whose name the keys type (T9) or whose number holds them,
     * then numbers called before. At most [limit], each number once.
     */
    fun suggest(input: String, limit: Int = 4): Flow<List<Suggestion>> =
        combine(contacts, history.records) { people, records ->
            if (input.isEmpty()) emptyList() else suggestions(input, people, records, limit)
        }
}

internal fun suggestions(
    input: String,
    people: List<Contact>,
    records: List<CallRecord>,
    limit: Int,
): List<Suggestion> {
    val typed = input.dialable()
    val out = LinkedHashMap<String, Suggestion>()
    for (contact in people) {
        val nameMatch = T9.matchesName(typed, contact.name)
        for (number in contact.numbers) {
            val key = number.value.dialable()
            val numberMatch = typed.isNotEmpty() && key.contains(typed)
            if (nameMatch || numberMatch) {
                out.putIfAbsent(key, Suggestion(contact.name, number.value, fromContact = true))
            }
        }
    }
    for (record in records) {
        val user = record.address.user ?: continue
        val key = user.dialable()
        if (key.contains(typed) && key !in out) {
            out[key] = Suggestion(record.name, user, fromContact = false)
        }
    }
    return out.values.take(limit)
}

/** Whether [text] is in this name, ignoring case and accents. */
fun String.containsFolded(text: String): Boolean = fold().contains(text.fold())

private fun String.fold(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .filter { it.category != CharCategory.NON_SPACING_MARK }
        .lowercase()

/** Forgets the calls older than [retention] allows, as of [now]; forever forgets nothing. */
suspend fun CallHistory.prune(retention: HistoryRetention, now: Long) {
    val days = retention.days ?: return
    deleteBefore(now - days * 24L * 60 * 60 * 1000)
}
