/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.dialer

import androidx.lifecycle.SavedStateHandle
import com.bobbyesp.sona.accounts.domain.InMemoryAccountsRepository
import com.bobbyesp.sona.calling.domain.CallCoordinator
import com.bobbyesp.sona.core.testing.InMemorySettingsStore
import com.bobbyesp.sona.core.testing.MainDispatcherRule
import com.bobbyesp.sona.directory.domain.Contact
import com.bobbyesp.sona.directory.domain.ContactNumber
import com.bobbyesp.sona.directory.domain.Directory
import com.bobbyesp.sona.directory.domain.FakeDeviceContacts
import com.bobbyesp.sona.directory.domain.InMemoryCallHistory
import com.bobbyesp.sona.directory.domain.InMemoryLocalContacts
import com.bobbyesp.sona.sip.fake.FakeSipEngine
import com.bobbyesp.sona.telecom.api.FakeSystemCalls
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class KeypadTest {

    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val accounts = InMemoryAccountsRepository()

    private fun keypad(
        saved: SavedStateHandle,
        contacts: List<Contact> = emptyList(),
    ): KeypadViewModel {
        val scope = CoroutineScope(mainDispatcher.dispatcher)
        val engine = FakeSipEngine(scope)
        val coordinator =
            CallCoordinator(engine, FakeSystemCalls(), accounts, InMemorySettingsStore(), scope)
        val directory =
            Directory(
                InMemoryLocalContacts(contacts),
                FakeDeviceContacts(),
                InMemoryCallHistory(),
                InMemorySettingsStore(),
            )
        return KeypadViewModel(saved, coordinator, directory, accounts)
    }

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
        val viewModel = keypad(saved)
        "102".forEach { viewModel.onSendIntent(KeypadIntent.Type(it)) }

        val restored = keypad(SavedStateHandle(mapOf("input" to saved.get<String>("input"))))

        assertEquals("102", restored.state.value.input)
    }

    @Test
    fun `clear empties the field`() {
        val viewModel = keypad(SavedStateHandle(mapOf("input" to "600")))

        viewModel.onSendIntent(KeypadIntent.Clear)

        assertEquals("", viewModel.state.value.input)
    }

    @Test
    fun `letters typed on the keys suggest the contact, and picking one fills the field`() {
        val luna = Contact("local:1", "Luna", listOf(ContactNumber("Lab", "103")))
        val viewModel = keypad(SavedStateHandle(), contacts = listOf(luna))

        "5862".forEach { viewModel.onSendIntent(KeypadIntent.Type(it)) }
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("103"), viewModel.state.value.suggestions.map { it.number })

        viewModel.onSendIntent(KeypadIntent.Pick("103"))
        assertEquals("103", viewModel.state.value.input)
    }
}
