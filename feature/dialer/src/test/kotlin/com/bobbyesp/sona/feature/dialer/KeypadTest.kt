/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.dialer

import androidx.lifecycle.SavedStateHandle
import com.bobbyesp.sona.core.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class KeypadTest {

    @get:Rule val mainDispatcher = MainDispatcherRule()

    @Test
    fun `digits, star and hash are typed`() {
        assertEquals("1*0#", "1*0#".fold("") { input, key -> DialInput.type(input, key) })
    }

    @Test
    fun `a plus only begins a number`() {
        assertEquals("+", DialInput.type("", '+'))
        assertEquals("34", DialInput.type("34", '+'))
    }

    @Test
    fun `letters are not typed`() {
        assertEquals("1", DialInput.type("1", 'a'))
    }

    @Test
    fun `the field stops at its length`() {
        val full = "1".repeat(DialInput.MAX_LENGTH)
        assertEquals(full, DialInput.type(full, '2'))
    }

    @Test
    fun `deleting from nothing leaves nothing`() {
        assertEquals("", DialInput.delete(""))
        assertEquals("10", DialInput.delete("102"))
    }

    @Test
    fun `what was typed survives process death`() {
        val saved = SavedStateHandle()
        val viewModel = KeypadViewModel(saved)
        "102".forEach { viewModel.onSendIntent(KeypadIntent.Type(it)) }

        val restored =
            KeypadViewModel(SavedStateHandle(mapOf("input" to saved.get<String>("input"))))

        assertEquals("102", restored.state.value.input)
    }

    @Test
    fun `clear empties the field`() {
        val viewModel = KeypadViewModel(SavedStateHandle(mapOf("input" to "600")))

        viewModel.onSendIntent(KeypadIntent.Clear)

        assertEquals("", viewModel.state.value.input)
    }
}
