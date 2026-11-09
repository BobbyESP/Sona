/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.model.settings

import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingTest {

    private enum class Fruit {
        Apple,
        Pear,
    }

    @Test
    fun `nothing stored reads as the default`() {
        assertEquals(ThemeMode.System, AppearanceSettings.Theme.read(null))
        assertEquals(true, AppearanceSettings.DynamicColor.read(null))
    }

    @Test
    fun `every kind reads back what it wrote`() {
        val toggle = Setting.Toggle("t", default = false)
        val number = Setting.Number("n", default = 5, range = 0..10)
        val text = Setting.Text("s", default = "")
        val span = Setting.Span("d", default = 3.seconds)
        val choice = Setting.choice("c", Fruit.Apple)

        assertEquals(true, toggle.read(toggle.encode(true)))
        assertEquals(7, number.read(number.encode(7)))
        assertEquals("sip", text.read(text.encode("sip")))
        assertEquals(10.seconds, span.read(span.encode(10.seconds)))
        assertEquals(Fruit.Pear, choice.read(choice.encode(Fruit.Pear)))
    }

    @Test
    fun `a choice is stored by name, so reordering the enum changes nothing`() {
        assertEquals(StoredValue.Str("Pear"), Setting.choice("c", Fruit.Apple).encode(Fruit.Pear))
    }

    @Test
    fun `a removed choice reads as the default`() {
        val choice = Setting.choice("c", Fruit.Apple)

        assertNull(choice.decode(StoredValue.Str("Banana")))
        assertEquals(Fruit.Apple, choice.read(StoredValue.Str("Banana")))
    }

    @Test
    fun `a number out of today's range reads as the default`() {
        val number = Setting.Number("n", default = 5, range = 0..10)

        assertEquals(5, number.read(StoredValue.Int32(42)))
        assertFalse(number.accepts(42))
        assertTrue(number.accepts(10))
    }

    @Test
    fun `another kind of value under the same key reads as the default`() {
        assertEquals(false, Setting.Toggle("k", default = false).read(StoredValue.Str("true")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a default outside its range is refused at declaration`() {
        Setting.Number("n", default = 11, range = 0..10)
    }
}
