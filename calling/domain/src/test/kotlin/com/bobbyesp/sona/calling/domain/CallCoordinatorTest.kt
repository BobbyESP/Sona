/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.calling.domain

import com.bobbyesp.sona.accounts.domain.AccountDraft
import com.bobbyesp.sona.accounts.domain.AccountEngineSync
import com.bobbyesp.sona.accounts.domain.InMemoryAccountsRepository
import com.bobbyesp.sona.accounts.domain.SaveAccount
import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.CallId
import com.bobbyesp.sona.core.model.Secret
import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.core.model.settings.CallSettings
import com.bobbyesp.sona.core.model.settings.DeclineMode
import com.bobbyesp.sona.core.testing.InMemorySettingsStore
import com.bobbyesp.sona.credentials.api.InMemoryCredentialVault
import com.bobbyesp.sona.sip.api.EngineConfig
import com.bobbyesp.sona.sip.api.SipStatus
import com.bobbyesp.sona.sip.fake.FakeSipEngine
import com.bobbyesp.sona.telecom.api.FakeSystemCall
import com.bobbyesp.sona.telecom.api.FakeSystemCalls
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The coordinator against a scripted engine and a recording system: calls as scenarios. */
@OptIn(ExperimentalCoroutinesApi::class)
class CallCoordinatorTest {

    private val accounts = InMemoryAccountsRepository()
    private val vault = InMemoryCredentialVault()
    private val settings = InMemorySettingsStore()
    private val system = FakeSystemCalls()
    private val alice = SipAddress("101", "lab.local")

    private class World(val engine: FakeSipEngine, val calls: CallCoordinator)

    /** A registered account, its engine, and a started coordinator. */
    private suspend fun TestScope.world(names: CallerNames = CallerNames.None): World {
        val engine = FakeSipEngine(backgroundScope)
        engine.start(EngineConfig())
        SaveAccount(accounts, vault) { AccountId("lab") }(
            AccountDraft(server = "lab.local", username = "102"),
            Secret("x"),
        )
        backgroundScope.launch { AccountEngineSync(accounts, vault, engine).run() }
        advanceTimeBy(1.seconds)
        val coordinator =
            CallCoordinator(
                engine,
                system,
                accounts,
                settings,
                backgroundScope,
                clock = { 1_000 },
                names = names,
            )
        coordinator.start()
        runCurrent()
        return World(engine, coordinator)
    }

    private fun World.call(id: CallId) = calls.calls.value.single { it.id == id }

    private fun systemCall(id: CallId): FakeSystemCall = system.calls.getValue(id)

    private suspend fun World.dial(input: String): CallId =
        (calls.dial(input) as DialOutcome.Started).id

    @Test
    fun `dialing an extension calls it on the account's domain, and the system knows`() = runTest {
        val world = world()
        val id = world.dial("600")
        runCurrent()

        assertEquals(SipAddress("600", "lab.local"), world.call(id).remote)
        assertEquals(CallState.Dialing, world.call(id).state)
        assertEquals("sip:600@lab.local", systemCall(id).request.address)
    }

    @Test
    fun `a call to a contact shows the contact name, here and in the system`() = runTest {
        val world = world(names = CallerNames { if (it.user == "600") "Echo test" else null })
        val id = world.dial("600")
        runCurrent()

        assertEquals("Echo test", world.call(id).title)
        assertEquals("Echo test", systemCall(id).request.displayName)
    }

    @Test
    fun `an answered outgoing call becomes active here and in the system, with its start time`() =
        runTest {
            val world = world()
            val id = world.dial("600")
            world.engine.remoteRings(id)
            runCurrent()
            assertEquals(CallState.RingingRemote, world.call(id).state)

            world.engine.remoteAnswers(id)
            runCurrent()

            assertEquals(CallState.Active, world.call(id).state)
            assertEquals(1_000L, world.call(id).connectedAt)
            assertEquals(listOf("active"), systemCall(id).log)
            assertEquals(CallForeground.Calls(microphone = true), world.calls.foreground.value)
        }

    @Test
    fun `a busy extension ends the call with its reason, then the call goes`() = runTest {
        val world = world()
        val id = world.dial("486")
        runCurrent()
        world.engine.remoteEnds(id, SipStatus(486, "Busy Here"))
        runCurrent()

        assertEquals(
            CallState.Ended(EndReason.Busy(SipStatus(486, "Busy Here"))),
            world.call(id).state,
        )
        assertEquals(listOf("disconnect:Busy"), systemCall(id).log)

        advanceTimeBy(4.seconds)
        runCurrent()
        assertTrue(world.calls.calls.value.isEmpty())
        assertEquals(CallForeground.None, world.calls.foreground.value)
    }

    /** Found by this test: a call that failed before it was first seen was dropped unexplained. */
    @Test
    fun `a call that fails at once is still shown with its reason`() = runTest {
        val world = world()
        val id = world.dial("404")
        world.engine.remoteEnds(id, SipStatus(404, "Not Found"))
        runCurrent()

        assertEquals(
            CallState.Ended(EndReason.NotFound(SipStatus(404, "Not Found"))),
            world.call(id).state,
        )
        advanceTimeBy(4.seconds)
        runCurrent()
        assertTrue(world.calls.calls.value.isEmpty())
    }

    @Test
    fun `answering an incoming call tells the system first, then the engine`() = runTest {
        val world = world()
        val id = world.engine.incomingCall(alice, name = "Alice")
        runCurrent()
        assertEquals(CallState.Incoming, world.call(id).state)
        assertEquals("Alice", world.call(id).title)
        assertEquals(CallForeground.Calls(microphone = false), world.calls.foreground.value)

        world.calls.answer(id)
        runCurrent()

        assertEquals(CallState.Active, world.call(id).state)
        assertEquals("answer", systemCall(id).log.first())
    }

    @Test
    fun `a headset's answer and hang-up reach the engine`() = runTest {
        val world = world()
        val id = world.engine.incomingCall(alice)
        runCurrent()

        system.events.getValue(id).onAnswer()
        runCurrent()
        assertEquals(CallState.Active, world.call(id).state)

        system.events.getValue(id).onDisconnect()
        runCurrent()
        assertEquals(CallState.Ended(EndReason.HungUp(byMe = true)), world.call(id).state)
    }

    @Test
    fun `an unanswered incoming call the caller gives up on is missed`() = runTest {
        val world = world()
        val id = world.engine.incomingCall(alice)
        runCurrent()

        world.engine.remoteEnds(id)
        runCurrent()

        assertEquals(CallState.Ended(EndReason.Missed), world.call(id).state)
        assertEquals("disconnect:Missed", systemCall(id).log.last())
    }

    @Test
    fun `answering a second call holds the first`() = runTest {
        val world = world()
        val first = world.dial("600")
        world.engine.remoteAnswers(first)
        runCurrent()

        val second = world.engine.incomingCall(alice)
        runCurrent()
        world.calls.answer(second)
        runCurrent()

        assertEquals(CallState.OnHold, world.call(first).state)
        assertEquals(CallState.Active, world.call(second).state)
        assertTrue("inactive" in systemCall(first).log)
    }

    @Test
    fun `without call waiting a second call is declined as busy and never shown`() = runTest {
        settings.set(CallSettings.CallWaiting, false)
        val world = world()
        val first = world.dial("600")
        world.engine.remoteAnswers(first)
        runCurrent()

        world.engine.incomingCall(alice)
        runCurrent()

        assertEquals(listOf(first), world.calls.calls.value.map { it.id })
    }

    @Test
    fun `do not disturb refuses an incoming call before it rings, as the user chose`() = runTest {
        settings.set(CallSettings.DoNotDisturb, true)
        settings.set(CallSettings.DeclineWith, DeclineMode.Declined)
        val world = world()

        val id = world.engine.incomingCall(alice)
        runCurrent()

        assertTrue(world.calls.calls.value.isEmpty())
        assertEquals(listOf(id to false), world.engine.declined)
    }

    @Test
    fun `do not disturb refuses as busy by default`() = runTest {
        settings.set(CallSettings.DoNotDisturb, true)
        val world = world()

        val id = world.engine.incomingCall(alice)
        runCurrent()

        assertEquals(listOf(id to true), world.engine.declined)
    }

    @Test
    fun `dialing without a registered account explains why`() = runTest {
        val world = world()
        world.engine.failRegistration(AccountId("lab"), SipStatus(401, "Unauthorized"))
        advanceTimeBy(1.seconds)

        assertTrue(world.calls.dial("600") is DialOutcome.NotRegistered)
        assertTrue(world.calls.dial("call alice") is DialOutcome.InvalidAddress)
    }

    @Test
    fun `mute, hold and DTMF reach the engine`() = runTest {
        val world = world()
        val id = world.dial("600")
        world.engine.remoteAnswers(id)
        runCurrent()

        world.calls.setMuted(id, true)
        world.calls.sendDtmf(id, '1')
        world.calls.setOnHold(id, true)
        runCurrent()

        assertTrue(world.call(id).muted)
        assertEquals(listOf(id to '1'), world.engine.dtmfSent)
        assertEquals(CallState.OnHold, world.call(id).state)
    }

    @Test
    fun `the audio route comes from the system, and choosing one asks it`() = runTest {
        val world = world()
        val id = world.dial("600")
        runCurrent()
        assertEquals(FakeSystemCall.Earpiece, world.call(id).route)

        world.calls.selectRoute(id, FakeSystemCall.Speaker)
        runCurrent()

        assertEquals(FakeSystemCall.Speaker, world.call(id).route)
        assertEquals("route:Speaker", systemCall(id).log.last())
    }

    @Test
    fun `a system that refuses a call does not stop it`() = runTest {
        val refusing = FakeSystemCalls(refuse = true)
        val engine = FakeSipEngine(backgroundScope)
        engine.start(EngineConfig())
        SaveAccount(accounts, vault) { AccountId("lab") }(
            AccountDraft(server = "lab.local", username = "102"),
            Secret("x"),
        )
        backgroundScope.launch { AccountEngineSync(accounts, vault, engine).run() }
        advanceTimeBy(1.seconds)
        val calls = CallCoordinator(engine, refusing, accounts, settings, backgroundScope)
        calls.start()
        runCurrent()

        val id = (calls.dial("600") as DialOutcome.Started).id
        engine.remoteAnswers(id)
        runCurrent()

        assertEquals(CallState.Active, calls.calls.value.single().state)
    }
}
