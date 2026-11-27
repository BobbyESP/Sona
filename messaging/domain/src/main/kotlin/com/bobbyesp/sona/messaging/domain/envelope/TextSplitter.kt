/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.domain.envelope

/**
 * Cuts a text into parts of at most a number of UTF-8 bytes, for a server that relays short bodies
 * only. Put together in order, the parts are the text, every character kept: a Sona peer joins them
 * back, and a plain peer reads them one after another.
 *
 * A part ends after a space or a line break when one fits; a word longer than a part is cut where a
 * character begins. Never inside a UTF-8 sequence or a surrogate pair, and, where it can, not
 * inside what reads as one character: before a combining mark, a variation selector, an emoji
 * modifier or a zero-width joiner, after a joiner, between the two letters of a flag or within a
 * `\r\n`.
 */
object TextSplitter {

    /** The parts of [text], each at most [maxBytes] bytes of UTF-8; none for an empty text. */
    fun split(text: String, maxBytes: Int): List<String> {
        require(maxBytes >= MAX_CODE_POINT_BYTES) { "A part must hold any character" }
        val parts = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var bytes = 0
            var index = start
            var lastSafe = -1
            var lastBreak = -1
            while (index < text.length) {
                val codePoint = text.codePointAt(index)
                val size = utf8Size(codePoint)
                if (bytes + size > maxBytes) break
                bytes += size
                index += Character.charCount(codePoint)
                if (canCut(text, index)) {
                    lastSafe = index
                    if (Character.isWhitespace(codePoint)) lastBreak = index
                }
            }
            if (index >= text.length) {
                parts += text.substring(start)
                break
            }
            val end =
                when {
                    lastBreak > start -> lastBreak
                    lastSafe > start -> lastSafe
                    // One cluster longer than a part: cut it where a character begins.
                    else -> index
                }
            parts += text.substring(start, end)
            start = end
        }
        return parts
    }

    /** The UTF-8 size of [text], as it goes on the wire. */
    fun utf8Size(text: String): Int {
        var total = 0
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            total += utf8Size(codePoint)
            index += Character.charCount(codePoint)
        }
        return total
    }

    // A lone surrogate is written as a replacement character, three bytes.
    private fun utf8Size(codePoint: Int): Int =
        when {
            codePoint < 0x80 -> 1
            codePoint < 0x800 -> 2
            codePoint < 0x10000 -> 3
            else -> 4
        }

    /** Whether a part may end before [index] without breaking what reads as one character. */
    private fun canCut(text: String, index: Int): Boolean {
        if (index <= 0 || index >= text.length) return true
        val before = text.codePointBefore(index)
        val after = text.codePointAt(index)
        if (before == '\r'.code && after == '\n'.code) return false
        if (before == ZWJ || after == ZWJ) return false
        if (isRegionalIndicator(before) && isRegionalIndicator(after)) {
            // Flags are pairs: a cut may fall between two of them, never inside one.
            return regionalIndicatorsBefore(text, index) % 2 == 0
        }
        return !extendsPrevious(after)
    }

    private fun regionalIndicatorsBefore(text: String, index: Int): Int {
        var count = 0
        var at = index
        while (at > 0) {
            val codePoint = text.codePointBefore(at)
            if (!isRegionalIndicator(codePoint)) break
            count++
            at -= Character.charCount(codePoint)
        }
        return count
    }

    private fun extendsPrevious(codePoint: Int): Boolean =
        when (Character.getType(codePoint)) {
            Character.NON_SPACING_MARK.toInt(),
            Character.ENCLOSING_MARK.toInt(),
            Character.COMBINING_SPACING_MARK.toInt() -> true
            else ->
                codePoint in 0xFE00..0xFE0F ||
                    codePoint in 0xE0100..0xE01EF ||
                    codePoint in 0x1F3FB..0x1F3FF ||
                    codePoint in 0xE0020..0xE007F
        }

    private fun isRegionalIndicator(codePoint: Int) = codePoint in 0x1F1E6..0x1F1FF

    private const val ZWJ = 0x200D
    private const val MAX_CODE_POINT_BYTES = 4
}
