/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.dialer

/**
 * What the keypad's field accepts, as rules rather than as a text field's behavior, so they are
 * tested on the JVM: an extension or a number from the keys, and a SIP address (`alice@lab.local`,
 * `sip:alice@lab.local:5070`) from a keyboard or the clipboard. Turning the input into the address
 * to call is the calling domain's job (`AddressResolver`).
 */
internal object DialInput {
    /** Room for a SIP address with its parameters. */
    const val MAX_LENGTH = 128

    /** [input] with [key] typed at its end, or unchanged when [key] cannot go there. */
    fun type(input: String, key: Char): String =
        when {
            input.length >= MAX_LENGTH -> input
            key.isDialKey() -> input + key
            // In a number a + only begins it (international); in an address it is a character.
            key == '+' -> if (input.isEmpty() || !input.isNumber()) input + key else input
            key.isAddressChar() -> input + key
            else -> input
        }

    fun delete(input: String): String = input.dropLast(1)

    /**
     * Text from elsewhere (the clipboard) as the field keeps it. A number loses the spaces and
     * punctuation it is written with (`+34 600 11-22-33`); an address written as `Alice
     * <sip:alice@lab.local>` keeps what is between the brackets; nothing the field could not have
     * been typed with stays.
     */
    fun clean(text: String): String {
        val trimmed = text.trim()
        val bare =
            if ('<' in trimmed && trimmed.endsWith('>')) {
                trimmed.substringAfterLast('<').dropLast(1).trim()
            } else {
                trimmed
            }
        val isWrittenNumber =
            bare.any { it.isDigit() } &&
                bare.all {
                    it.isDialKey() || it == '+' || it.isWhitespace() || it in NumberPunctuation
                }
        val kept =
            if (isWrittenNumber) {
                bare.filterIndexed { index, char ->
                    char.isDialKey() || (char == '+' && index == 0)
                }
            } else {
                bare.filter { it.isDialKey() || it == '+' || it.isAddressChar() }
            }
        return kept.take(MAX_LENGTH)
    }

    /**
     * The field as a keyboard left it: only what it could have been typed with stays, and nothing
     * is rewritten under the cursor (the dots of an address being typed are not a number's).
     */
    fun typed(text: String): String =
        text.filter { it.isDialKey() || it == '+' || it.isAddressChar() }.take(MAX_LENGTH)

    private fun Char.isDialKey() = this in '0'..'9' || this == '*' || this == '#'

    private fun String.isNumber() = all { it.isDialKey() || it == '+' }

    /** What a SIP address is written with, besides the dial keys: no spaces, no brackets. */
    private fun Char.isAddressChar() = isLetter() || this in AddressPunctuation

    private const val AddressPunctuation = "@.-_:;=~!$&'()?/[]%,"
    private const val NumberPunctuation = "-()./"
}
