/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.accounts.domain

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.Secret
import com.bobbyesp.sona.credentials.api.InMemoryCredentialVault
import com.bobbyesp.sona.sip.api.EngineConfig
import com.bobbyesp.sona.sip.api.RegistrationState
import com.bobbyesp.sona.sip.api.ServerEndpoint
import com.bobbyesp.sona.sip.api.SipStatus
import com.bobbyesp.sona.sip.api.Transport
import com.bobbyesp.sona.sip.fake.FakeSipEngine
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsTest {

    private val repository = InMemoryAccountsRepository()
    private val vault = InMemoryCredentialVault()
    private var next = 0
    private val save = SaveAccount(repository, vault) { AccountId("account-${++next}") }

    private val lab = AccountDraft(server = "lab.local", username = "102")

    @Test
    fun `a server, an extension and a password are a whole account`() = runTest {
        val saved = save(lab, Secret("secret")) as SaveOutcome.Saved

        with(saved.account) {
            assertEquals("lab.local · 102", label)
            assertEquals("sip:102@lab.local", identity.uri)
            assertEquals(ServerEndpoint("lab.local", 5060, Transport.Udp), server)
            assertEquals(Account.DefaultExpiry, expiry)
            assertTrue(isDefault)
        }
        assertEquals("secret", vault.read(saved.account.id)?.reveal())
    }

    @Test
    fun `a new account needs a password, an edited one keeps its own`() = runTest {
        assertEquals(SaveOutcome.PasswordRequired, save(lab, null))

        val saved = (save(lab, Secret("secret")) as SaveOutcome.Saved).account
        val edited = save(AccountDraft.of(saved).copy(label = "Lab"), null) as SaveOutcome.Saved

        assertEquals("Lab", edited.account.label)
        assertEquals("secret", vault.read(saved.id)?.reveal())
        assertEquals(saved.revision + 1, edited.account.revision)
    }

    @Test
    fun `only the first account becomes the default, and each takes its own color`() = runTest {
        val first = (save(lab, Secret("a")) as SaveOutcome.Saved).account
        val second =
            (save(AccountDraft(server = "home.lan", username = "201"), Secret("b"))
                    as SaveOutcome.Saved)
                .account

        assertTrue(first.isDefault)
        assertFalse(second.isDefault)
        assertTrue(first.color != second.color)
    }

    @Test
    fun `deleting the default makes the next one the default, and forgets its password`() =
        runTest {
            val first = (save(lab, Secret("a")) as SaveOutcome.Saved).account
            val second =
                (save(AccountDraft(server = "home.lan", username = "201"), Secret("b"))
                        as SaveOutcome.Saved)
                    .account

            DeleteAccount(repository, vault)(first.id)

            assertEquals(listOf(second.id), repository.accounts.first().map { it.id })
            assertTrue(repository.get(second.id)!!.isDefault)
            assertEquals(null, vault.read(first.id))
        }

    @Test
    fun `the engine follows the stored accounts, with their passwords`() = runTest {
        val engine = FakeSipEngine(this)
        engine.start(EngineConfig())
        val sync = launch { AccountEngineSync(repository, vault, engine).run() }

        val account = (save(lab, Secret("secret")) as SaveOutcome.Saved).account
        advanceUntilIdle()
        assertEquals("secret", engine.accounts.single().password.reveal())
        assertEquals(RegistrationState.Registered, engine.registrations.value[account.id])

        SetAccountEnabled(repository)(account.id, enabled = false)
        advanceUntilIdle()
        assertEquals(RegistrationState.Unregistered, engine.registrations.value[account.id])

        DeleteAccount(repository, vault)(account.id)
        advanceUntilIdle()
        assertTrue(engine.accounts.isEmpty())
        sync.cancel()
    }

    @Test
    fun `a password that cannot be opened keeps the account from registering`() = runTest {
        val engine = FakeSipEngine(this)
        engine.start(EngineConfig())
        val account = (save(lab, Secret("secret")) as SaveOutcome.Saved).account
        vault.remove(account.id)

        val sync = launch { AccountEngineSync(repository, vault, engine).run() }
        advanceUntilIdle()

        assertFalse(engine.accounts.single().enabled)
        sync.cancel()
    }

    @Test
    fun `statuses pair each account with its registration`() = runTest {
        val engine = FakeSipEngine(this)
        engine.start(EngineConfig())
        val account = (save(lab, Secret("secret")) as SaveOutcome.Saved).account

        val before = observeAccountStatuses(repository, engine).first()
        assertEquals(RegistrationState.Unregistered, before.single().registration)
        assertEquals(account, before.single().account)
    }

    @Test
    fun `failures are explained by their status`() {
        fun explain(code: Int?) =
            RegistrationProblem.of(
                RegistrationState.Failed(code?.let { SipStatus(it, "x") }, detail = "no answer"),
            )

        assertTrue(explain(401) is RegistrationProblem.WrongCredentials)
        assertTrue(explain(407) is RegistrationProblem.WrongCredentials)
        assertTrue(explain(403) is RegistrationProblem.Refused)
        assertTrue(explain(404) is RegistrationProblem.UnknownExtension)
        assertTrue(explain(503) is RegistrationProblem.ServerError)
        assertTrue(explain(null) is RegistrationProblem.NoAnswer)
        assertTrue(explain(408) is RegistrationProblem.NoAnswer)
        assertTrue(explain(488) is RegistrationProblem.Other)
    }

    @Test
    fun `an expiry saved in seconds reads back`() = runTest {
        val saved = save(lab.copy(expirySeconds = "120"), Secret("x")) as SaveOutcome.Saved
        assertEquals(120.seconds, saved.account.expiry)
        assertEquals("120", AccountDraft.of(saved.account).expirySeconds)
    }
}
