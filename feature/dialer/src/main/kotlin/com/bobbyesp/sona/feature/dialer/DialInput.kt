/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.dialer

/**
 * What the keypad's field accepts, as rules rather than as a text field's behavior, so they are
 * tested on the JVM. Turning the input into an address is the calling domain's job (phase 2).
 */
internal object DialInput {
    /** Longer than any extension or number; a SIP address is pasted, not typed on a keypad. */
    const val MAX_LENGTH = 64

    /** [input] with [key] typed at its end, or unchanged when [key] cannot go there. */
    fun type(input: String, key: Char): String =
        when {
            input.length >= MAX_LENGTH -> input
            key.isDigit() || key == '*' || key == '#' -> input + key
            // A + only begins an international number.
            key == '+' && input.isEmpty() -> input + key
            else -> input
        }

    fun delete(input: String): String = input.dropLast(1)
}
