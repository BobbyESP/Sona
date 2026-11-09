/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.calling.domain

import com.bobbyesp.sona.core.model.SipAddress

/** What the user typed or tapped, as an address, or why it cannot be one. */
sealed interface AddressResolution {
    data class Resolved(val address: SipAddress) : AddressResolution

    data class Invalid(val reason: Reason) : AddressResolution

    enum class Reason {
        Empty,
        NotAnAddress,
    }
}

/**
 * Turns what the user typed into the address to call, on the account it is called from:
 *
 * |Input                              |Becomes                                                        |
 * |-----------------------------------|---------------------------------------------------------------|
 * |`sip:alice@lab.local`, `sips:…`    |itself                                                         |
 * |`alice@lab.local`                  |`sip:alice@lab.local`                                          |
 * |`102`, `*97`, `600#`               |`sip:102@<domain>`: the Asterisk extension case, the common one|
 * |`+34 600 11 22 33`, `(600) 112-233`|`sip:+34600112233@<domain>`, without spaces or punctuation     |
 * |`tel:+34600112233`                 |as a number                                                    |
 *
 * Pure, and tested: the dialer shows its result under the field as the user types.
 */
object AddressResolver {

    fun resolve(input: String, domain: String): AddressResolution {
        val text = input.trim()
        if (text.isEmpty()) return AddressResolution.Invalid(AddressResolution.Reason.Empty)

        if (
            text.startsWith("sip:", ignoreCase = true) ||
                text.startsWith("sips:", ignoreCase = true)
        ) {
            return SipAddress.parse(text)?.let(AddressResolution::Resolved) ?: invalid()
        }
        if (text.startsWith("tel:", ignoreCase = true)) return number(text.substring(4), domain)
        if ('@' in text) {
            return SipAddress.parse("sip:$text")?.let(AddressResolution::Resolved) ?: invalid()
        }
        return number(text, domain)
    }

    /** A number or an extension: digits, `*` and `#`, a leading `+`, and punctuation to drop. */
    private fun number(text: String, domain: String): AddressResolution {
        val kept = text.filterIndexed { index, char ->
            char.isDigit() ||
                char == '*' ||
                char == '#' ||
                (char == '+' && index == text.indexOfFirst { !it.isWhitespace() })
        }
        val dropped = text.filterNot { it.isDigit() || it == '*' || it == '#' || it == '+' }
        val onlyPunctuation = dropped.all { it.isWhitespace() || it in Punctuation }
        if (kept.isEmpty() || kept == "+" || !onlyPunctuation) return invalid()
        return AddressResolution.Resolved(SipAddress(user = kept, host = domain))
    }

    private fun invalid() = AddressResolution.Invalid(AddressResolution.Reason.NotAnAddress)

    private const val Punctuation = "-()./"
}
