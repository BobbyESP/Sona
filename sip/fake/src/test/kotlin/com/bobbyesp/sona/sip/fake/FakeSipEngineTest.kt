/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.fake

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.Secret
import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.sip.api.AccountSpec
import com.bobbyesp.sona.sip.api.EngineConfig
import com.bobbyesp.sona.sip.api.RegistrationState
import com.bobbyesp.sona.sip.api.ServerEndpoint
import com.bobbyesp.sona.sip.api.SipStatus
import com.bobbyesp.sona.sip.api.Transport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeSipEngineTest {

    private val lab = account("lab", "102")
    private val home = account("home", "201")

    private fun account(id: String, user: String, enabled: Boolean = true) =
        AccountSpec(
            id = AccountId(id),
            label = id,
            identity = SipAddress(user, "$id.local"),
            password = Secret("secret"),
            registrar = ServerEndpoint("$id.local", 5060, Transport.Udp),
            enabled = enabled,
        )

    // The test's own scope, not its background one: advanceUntilIdle skips background work.
    private fun TestScope.engine() = FakeSipEngine(this)

    private fun FakeSipEngine.stateOf(account: AccountSpec) = registrations.value[account.id]

    @Test
    fun `an account registers after a moment`() = runTest {
        val engine = engine()
        engine.start(EngineConfig())
        engine.setAccounts(listOf(lab))

        assertEquals(RegistrationState.Registering, engine.stateOf(lab))
        advanceUntilIdle()
        assertEquals(RegistrationState.Registered, engine.stateOf(lab))
    }

    @Test
    fun `accounts given before starting register once it starts`() = runTest {
        val engine = engine()
        engine.setAccounts(listOf(lab))
        advanceUntilIdle()
        assertEquals(null, engine.stateOf(lab))

        engine.start(EngineConfig())
        advanceUntilIdle()
        assertEquals(RegistrationState.Registered, engine.stateOf(lab))
    }

    @Test
    fun `the engine keeps exactly the accounts it was last given`() = runTest {
        val engine = engine()
        engine.start(EngineConfig())
        engine.setAccounts(listOf(lab, home))
        advanceUntilIdle()

        engine.setAccounts(listOf(home))
        advanceUntilIdle()

        assertEquals(setOf(home.id), engine.registrations.value.keys)
    }

    @Test
    fun `a disabled account stays unregistered`() = runTest {
        val engine = engine()
        val disabled = account("lab", "102", enabled = false)
        engine.start(EngineConfig())
        engine.setAccounts(listOf(disabled))
        advanceUntilIdle()

        assertEquals(RegistrationState.Unregistered, engine.stateOf(disabled))
    }

    @Test
    fun `a scripted rejection fails with the server's status, and acceptance recovers`() = runTest {
        val engine = engine()
        engine.start(EngineConfig())
        engine.setAccounts(listOf(lab))
        engine.failRegistration(lab.id, SipStatus(401, "Unauthorized"))
        advanceUntilIdle()

        assertEquals(
            RegistrationState.Failed(SipStatus(401, "Unauthorized")),
            engine.stateOf(lab),
        )

        engine.acceptRegistration(lab.id)
        advanceUntilIdle()
        assertEquals(RegistrationState.Registered, engine.stateOf(lab))
    }

    @Test
    fun `a server that does not answer says what was tried`() = runTest {
        val engine = engine()
        engine.start(EngineConfig())
        engine.setAccounts(listOf(lab))
        engine.failRegistration(lab.id, status = null)
        advanceUntilIdle()

        assertEquals(
            RegistrationState.Failed(status = null, detail = "No answer from lab.local:5060 (UDP)"),
            engine.stateOf(lab),
        )
    }

    @Test
    fun `losing the network takes accounts offline, and they register when it is back`() = runTest {
        val engine = engine()
        engine.start(EngineConfig())
        engine.setAccounts(listOf(lab))
        advanceUntilIdle()

        engine.goOffline()
        assertEquals(RegistrationState.Offline, engine.stateOf(lab))

        engine.goOnline()
        advanceUntilIdle()
        assertEquals(RegistrationState.Registered, engine.stateOf(lab))
    }

    @Test
    fun `stopping unregisters every account`() = runTest {
        val engine = engine()
        engine.start(EngineConfig())
        engine.setAccounts(listOf(lab, home))
        advanceUntilIdle()

        engine.stop()

        assertEquals(
            mapOf(
                lab.id to RegistrationState.Unregistered,
                home.id to RegistrationState.Unregistered,
            ),
            engine.registrations.value,
        )
    }
}
