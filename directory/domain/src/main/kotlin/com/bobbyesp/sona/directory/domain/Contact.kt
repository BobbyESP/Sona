/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.directory.domain

import com.bobbyesp.sona.core.model.SipAddress

enum class ContactSource {
    /** Made in Sona; stored with it. */
    Local,

    /** The phone's contacts; read, never written. */
    Device,
}

/** A way to reach a contact: an extension (`102`), a phone number, or a SIP address. */
data class ContactNumber(val label: String, val value: String) {
    /** Whether a call from or to [address] is this number. */
    fun matches(address: SipAddress): Boolean {
        val own = SipAddress.parse(value)
        if (own != null) {
            return own.user == address.user && own.host.equals(address.host, ignoreCase = true)
        }
        val digits = value.dialable()
        return digits.isNotEmpty() && digits == address.user?.dialable()
    }
}

/** Someone Sona can call. [id] is unique across both sources (`local:7`, `device:312`). */
data class Contact(
    val id: String,
    val name: String,
    val numbers: List<ContactNumber>,
    val favorite: Boolean = false,
    val source: ContactSource = ContactSource.Local,
) {
    val editable: Boolean
        get() = source == ContactSource.Local

    fun matches(address: SipAddress): Boolean = numbers.any { it.matches(address) }
}

/** Digits and a leading plus: what a dialer would send, without spaces or dashes. */
internal fun String.dialable(): String = filter { it.isDigit() || it == '+' }

/** The contact [address] belongs to, preferring Sona's own over the phone's. */
fun List<Contact>.find(address: SipAddress): Contact? = filter {
    it.matches(address)
}
    .minByOrNull { it.source.ordinal }
