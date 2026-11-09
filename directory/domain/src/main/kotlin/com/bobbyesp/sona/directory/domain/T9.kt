/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.directory.domain

/**
 * Finding people from the keypad: digits type letters as on a phone's keys (2 is abc, 3 is def…),
 * so "5264" finds "Luna". Pure, so every case is a JVM test.
 */
object T9 {
    private val letters =
        mapOf(
            '2' to "abc",
            '3' to "def",
            '4' to "ghi",
            '5' to "jkl",
            '6' to "mno",
            '7' to "pqrs",
            '8' to "tuv",
            '9' to "wxyz",
        )

    private val digitOf: Map<Char, Char> =
        letters.flatMap { (digit, chars) -> chars.map { it to digit } }.toMap()

    /** The keys that type [text]: letters become their digit, digits stay, everything else goes. */
    fun keys(text: String): String = buildString {
        for (char in text.lowercase().map(::unaccent)) {
            when {
                char.isDigit() -> append(char)
                char in digitOf -> append(digitOf.getValue(char))
            }
        }
    }

    /**
     * Whether [name] is what [input] types: the keys are the start of the whole name or of any of
     * its words ("5264" finds "Luna Gómez", "466" finds "Ana Gómez" through "Gómez").
     */
    fun matchesName(input: String, name: String): Boolean {
        if (input.isEmpty() || input.any { !it.isDigit() }) return false
        return name
            .split(' ', '-', '.', '_')
            .filter { it.isNotEmpty() }
            .any { word ->
                keys(word).startsWith(input)
            } || keys(name).startsWith(input)
    }

    private fun unaccent(char: Char): Char =
        when (char) {
            'á',
            'à',
            'ä',
            'â' -> 'a'
            'é',
            'è',
            'ë',
            'ê' -> 'e'
            'í',
            'ì',
            'ï',
            'î' -> 'i'
            'ó',
            'ò',
            'ö',
            'ô' -> 'o'
            'ú',
            'ù',
            'ü',
            'û' -> 'u'
            'ñ' -> 'n'
            else -> char
        }
}
