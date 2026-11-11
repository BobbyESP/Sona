/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.dialer

import androidx.lifecycle.SavedStateHandle
import com.bobbyesp.sona.accounts.domain.Account
import com.bobbyesp.sona.accounts.domain.InMemoryAccountsRepository
import com.bobbyesp.sona.calling.domain.CallCoordinator
import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.Secret
import com.bobbyesp.sona.core.testing.InMemorySettingsStore
import com.bobbyesp.sona.core.testing.MainDispatcherRule
import com.bobbyesp.sona.directory.domain.Contact
import com.bobbyesp.sona.directory.domain.ContactNumber
import com.bobbyesp.sona.directory.domain.Directory
import com.bobbyesp.sona.directory.domain.FakeDeviceContacts
import com.bobbyesp.sona.directory.domain.InMemoryCallHistory
import com.bobbyesp.sona.directory.domain.InMemoryLocalContacts
import com.bobbyesp.sona.sip.api.AccountSpec
import com.bobbyesp.sona.sip.api.EngineConfig
import com.bobbyesp.sona.sip.api.ServerEndpoint
import com.bobbyesp.sona.sip.api.Transport
import com.bobbyesp.sona.sip.fake.FakeSipEngine
import com.bobbyesp.sona.telecom.api.FakeSystemCalls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class KeypadTest {

    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val accounts = InMemoryAccountsRepository()

    private val lab =
        Account(
            id = AccountId("lab"),
            label = "Lab",
            color = 0,
            username = "102",
            domain = "lab.local",
            server = ServerEndpoint("lab.local", 5060, Transport.Udp),
        )
    private lateinit var engine: FakeSipEngine

    /** A keypad; with an [account], one that is registered and can call. */
    private fun keypad(
        saved: SavedStateHandle,
        contacts: List<Contact> = emptyList(),
        account: Account? = null,
    ): KeypadViewModel {
        val scope = CoroutineScope(mainDispatcher.dispatcher)
        engine = FakeSipEngine(scope)
        if (account != null) {
            scope.launch {
                accounts.upsert(account.copy(isDefault = true))
                engine.start(EngineConfig())
                engine.setAccounts(
                    listOf(
                        AccountSpec(
                            account.id,
                            account.label,
                            account.identity,
                            Secret("secret"),
                            account.server,
                        ),
                    ),
                )
            }
        }
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
    fun `a SIP address is typed, a space is not`() {
        val typed = "alice @lab.local:5070".fold("") { input, key -> DialInput.type(input, key) }

        assertEquals("alice@lab.local:5070", typed)
        assertEquals("a+b", DialInput.type("a+", 'b'))
        assertEquals("102@192.168.1.10", DialInput.typed("102 @192.168.1.10"))
        assertEquals("192.168.", DialInput.typed("192.168."))
    }

    @Test
    fun `a pasted number loses how it was written, a pasted address keeps itself`() {
        assertEquals("+34600112233", DialInput.clean(" +34 600 11-22-33 "))
        assertEquals("600112233", DialInput.clean("(600) 112.233"))
        assertEquals("sip:alice@lab.local", DialInput.clean("Alice <sip:alice@lab.local>"))
        assertEquals("alice@lab.local", DialInput.clean("alice@lab.local\n"))
        assertEquals("", DialInput.clean("   "))
        assertEquals(DialInput.MAX_LENGTH, DialInput.clean("a".repeat(500)).length)
    }

    @Test
    fun `an address typed with a keyboard shows what it calls, and pasting replaces it`() {
        val viewModel = keypad(SavedStateHandle(), account = lab)
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        viewModel.onSendIntent(KeypadIntent.Edit("alice@home.lan"))
        assertEquals("sip:alice@home.lan", viewModel.state.value.preview)

        viewModel.onSendIntent(KeypadIntent.Paste("Luna <sip:103@lab.local>"))
        assertEquals("sip:103@lab.local", viewModel.state.value.input)

        viewModel.onSendIntent(KeypadIntent.Paste("  "))
        assertEquals("sip:103@lab.local", viewModel.state.value.input)
    }

    @Test
    fun `a long press on 1 calls the account's voicemail, and a suggestion's phone calls it`() {
        val viewModel = keypad(SavedStateHandle(), account = lab)
        val scheduler = mainDispatcher.dispatcher.scheduler
        scheduler.advanceUntilIdle()
        assertEquals("*97", viewModel.state.value.voicemail)

        viewModel.onSendIntent(KeypadIntent.Voicemail)
        scheduler.advanceUntilIdle()
        assertEquals("", viewModel.state.value.input)

        viewModel.onSendIntent(KeypadIntent.CallNumber("103"))
        scheduler.advanceUntilIdle()

        assertEquals("103", viewModel.state.value.input)
        assertEquals(listOf("*97", "103"), engine.calls.value.map { it.remote.user })
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
