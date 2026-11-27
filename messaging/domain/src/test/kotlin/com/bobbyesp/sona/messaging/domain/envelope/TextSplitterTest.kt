/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.domain.envelope

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSplitterTest {

    private fun split(text: String, max: Int): List<String> {
        val parts = TextSplitter.split(text, max)
        assertEquals("every character is kept", text, parts.joinToString(""))
        assertTrue("every part fits", parts.all { it.toByteArray().size <= max })
        assertTrue("no part is empty", parts.none { it.isEmpty() })
        return parts
    }

    @Test
    fun `a short text is one part, an empty one none`() {
        assertEquals(listOf("hola"), split("hola", 10))
        assertEquals(emptyList<String>(), split("", 10))
    }

    @Test
    fun `a part ends after a space when one fits`() {
        assertEquals(listOf("one two ", "three"), split("one two three", 10))
        assertEquals(listOf("line\n", "next"), split("line\nnext", 8))
    }

    @Test
    fun `a word longer than a part is cut where a character begins`() {
        assertEquals(listOf("abcd", "efgh", "ij"), split("abcdefghij", 4))
        // Two-byte letters: never half of one.
        val parts = split("ñññññ", 5)
        assertEquals(listOf("ññ", "ññ", "ñ"), parts)
    }

    @Test
    fun `a surrogate pair is never cut`() {
        val penguins = "🐧".repeat(5)
        val parts = split(penguins, 6)
        assertTrue(parts.all { part -> part.all { !it.isSurrogate() } || part.length % 2 == 0 })
        assertEquals(listOf("🐧", "🐧", "🐧", "🐧", "🐧"), parts)
    }

    @Test
    fun `what reads as one character stays together when it can`() {
        // A family (joined by ZWJ), a flag, a thumbs up with a skin tone, an accent combined.
        val family = "👨‍👩‍👧"
        val flag = "🇪🇸"
        val thumb = "👍🏽"
        val accent = "é"
        for (cluster in listOf(family, flag, thumb, accent)) {
            val text = "ab$cluster$cluster"
            val size = cluster.toByteArray().size
            val parts = split(text, size + 2)
            assertTrue(parts.toString(), parts.drop(1).all { it == cluster })
        }
        assertEquals(listOf("abc", "\r\nd"), split("abc\r\nd", 4))
    }

    @Test
    fun `a lone surrogate is counted as the character that replaces it`() {
        val text = "a\uD800b"
        assertEquals(5, TextSplitter.utf8Size(text))
        split(text, 4)
    }

    @Test
    fun `utf8 size is the size on the wire`() {
        val text = "Año 🐧 ok"
        assertEquals(text.toByteArray().size, TextSplitter.utf8Size(text))
    }
}
