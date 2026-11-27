/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.call

import com.bobbyesp.sona.accounts.domain.AccountDraft
import com.bobbyesp.sona.accounts.domain.AccountEngineSync
import com.bobbyesp.sona.accounts.domain.InMemoryAccountsRepository
import com.bobbyesp.sona.accounts.domain.SaveAccount
import com.bobbyesp.sona.calling.domain.CallCoordinator
import com.bobbyesp.sona.calling.domain.CallQuality
import com.bobbyesp.sona.calling.domain.DialOutcome
import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.CallId
import com.bobbyesp.sona.core.model.Secret
import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.core.model.settings.VideoSettings
import com.bobbyesp.sona.core.testing.InMemorySettingsStore
import com.bobbyesp.sona.core.testing.MainDispatcherRule
import com.bobbyesp.sona.credentials.api.InMemoryCredentialVault
import com.bobbyesp.sona.sip.api.EngineConfig
import com.bobbyesp.sona.sip.fake.FakeNetwork
import com.bobbyesp.sona.sip.fake.FakeSipEngine
import com.bobbyesp.sona.telecom.api.FakeSystemCalls
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The call screen's state against the fake engine and a real coordinator, second by second. */
@OptIn(ExperimentalCoroutinesApi::class)
class CallViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val accounts = InMemoryAccountsRepository()
    private val settings = InMemorySettingsStore()

    private class World(
        val engine: FakeSipEngine,
        val coordinator: CallCoordinator,
        val screen: CallViewModel,
    ) {
        val state: CallUiState
            get() = screen.state.value

        fun send(intent: CallIntent) = screen.onSendIntent(intent)
    }

    private suspend fun TestScope.world(): World {
        val engine = FakeSipEngine(backgroundScope)
        engine.start(EngineConfig())
        val vault = InMemoryCredentialVault()
        SaveAccount(accounts, vault) { AccountId("lab") }(
            AccountDraft(server = "lab.local", username = "102"),
            Secret("x"),
        )
        backgroundScope.launch {
            AccountEngineSync(accounts, vault, engine).run()
        }
        advanceTimeBy(1.seconds)
        val coordinator =
            CallCoordinator(engine, FakeSystemCalls(), accounts, settings, backgroundScope)
        coordinator.start()
        runCurrent()
        val screen = CallViewModel(coordinator, accounts, settings, engine.capabilities)
        runCurrent()
        return World(engine, coordinator, screen)
    }

    /** A call to 600, answered; with video, both ways. */
    private suspend fun TestScope.answeredCall(world: World, video: Boolean): CallId {
        val id = (world.coordinator.dial("600", video = video) as DialOutcome.Started).id
        world.engine.remoteAnswers(id)
        runCurrent()
        return id
    }

    /** Stops measuring, so the test ends with nothing left running on the main dispatcher. */
    private fun TestScope.leave(world: World) {
        world.send(CallIntent.Shown(false))
        runCurrent()
    }

    @Test
    fun `controls fade four seconds after the last touch in a video call, and a tap brings them`() =
        runTest(main.dispatcher) {
            val world = world()
            answeredCall(world, video = true)
            assertTrue(world.state.call!!.isVideo)
            assertTrue(world.state.controlsVisible)

            advanceTimeBy(3.seconds)
            world.send(CallIntent.ToggleMute)
            advanceTimeBy(3.seconds)
            assertTrue("a touch starts the count again", world.state.controlsVisible)
            advanceTimeBy(1100.milliseconds)
            assertFalse(world.state.controlsVisible)

            world.send(CallIntent.TapScreen)
            assertTrue(world.state.controlsVisible)
            world.send(CallIntent.TapScreen)
            assertFalse("a tap on the picture hides them again", world.state.controlsVisible)
        }

    @Test
    fun `controls stay in an audio call, and while a panel or the menu is open`() =
        runTest(main.dispatcher) {
            val world = world()
            answeredCall(world, video = false)
            advanceTimeBy(10.seconds)
            assertTrue(world.state.controlsVisible)
            world.send(CallIntent.TapScreen)
            assertTrue("nothing to uncover in an audio call", world.state.controlsVisible)

            val video = world()
            answeredCall(video, video = true)
            video.send(CallIntent.ToggleMore)
            advanceTimeBy(10.seconds)
            assertTrue(video.state.controlsVisible)
            video.send(CallIntent.ToggleStatistics)
            assertFalse("the panel closes the menu", video.state.moreOpen)
            advanceTimeBy(10.seconds)
            assertTrue(video.state.controlsVisible)
            video.send(CallIntent.ToggleStatistics)
            advanceTimeBy(4100.milliseconds)
            assertFalse(video.state.controlsVisible)
            leave(video)
        }

    @Test
    fun `statistics are measured only while shown, and kept in the state only while open`() =
        runTest(main.dispatcher) {
            val world = world()
            answeredCall(world, video = true)
            advanceTimeBy(5.seconds)
            assertEquals(0, world.engine.statisticsSamples)

            world.send(CallIntent.Shown(true))
            advanceTimeBy(2500.milliseconds)
            assertEquals(3, world.engine.statisticsSamples)
            assertEquals(CallQuality.Good, world.state.quality)
            assertNull("a closed panel keeps nothing", world.state.statistics)
            assertEquals(0, world.state.history.up.size)

            world.send(CallIntent.ToggleStatistics)
            assertNotNull("the last numbers at once", world.state.statistics)
            assertEquals(3, world.state.history.up.size)
            advanceTimeBy(2.seconds)
            assertEquals(5, world.state.history.receivedFps.size)
            assertNotNull(world.state.statistics!!.video)

            world.send(CallIntent.ToggleStatistics)
            assertNull(world.state.statistics)

            world.send(CallIntent.Shown(false))
            val measured = world.engine.statisticsSamples
            advanceTimeBy(5.seconds)
            assertEquals("not shown, not measured", measured, world.engine.statisticsSamples)
        }

    @Test
    fun `a weak connection in a video call says so after a while, and Turn off video keeps the voice`() =
        runTest(main.dispatcher) {
            val world = world()
            val id = answeredCall(world, video = true)
            world.engine.network(id, FakeNetwork.Weak)
            world.send(CallIntent.Shown(true))
            advanceTimeBy(2500.milliseconds)
            assertEquals(CallQuality.Poor, world.state.quality)
            assertFalse("a moment of Poor is not worth a banner", world.state.weakConnection)
            advanceTimeBy(3.seconds)
            assertTrue(world.state.weakConnection)

            world.send(CallIntent.TurnOffVideo)
            runCurrent()
            assertFalse(world.state.weakConnection)
            assertNull("the voice goes on alone", world.state.call!!.video)
            assertFalse(world.state.call!!.isVideo)
            leave(world)
        }

    @Test
    fun `a dismissed weak connection banner stays away while the connection stays weak`() =
        runTest(main.dispatcher) {
            val world = world()
            val id = answeredCall(world, video = true)
            world.engine.network(id, FakeNetwork.Weak)
            world.send(CallIntent.Shown(true))
            advanceTimeBy(5500.milliseconds)
            assertTrue(world.state.weakConnection)

            world.send(CallIntent.DismissBanner)
            advanceTimeBy(5.seconds)
            assertFalse(world.state.weakConnection)
            assertTrue("the video goes on", world.state.call!!.isVideo)
            leave(world)
        }

    @Test
    fun `the other side turning its camera on offers this side's, for a while`() =
        runTest(main.dispatcher) {
            val world = world()
            val id = answeredCall(world, video = false)
            world.engine.remoteStartsVideo(id)
            runCurrent()
            assertTrue(world.state.call!!.showsRemoteVideo)
            assertFalse(world.state.call!!.cameraOn)
            assertTrue(world.state.remoteTurnedOnVideo)

            advanceTimeBy(11.seconds)
            assertFalse(world.state.remoteTurnedOnVideo)
        }

    @Test
    fun `turning the camera on from the banner sends it, and the banner goes`() =
        runTest(main.dispatcher) {
            val world = world()
            val id = answeredCall(world, video = false)
            world.engine.remoteStartsVideo(id)
            runCurrent()
            assertTrue(world.state.remoteTurnedOnVideo)

            world.send(CallIntent.Camera(true))
            runCurrent()
            assertFalse(world.state.remoteTurnedOnVideo)
            assertTrue(world.state.call!!.cameraOn)
            assertTrue(world.state.call!!.video!!.sending)
        }

    @Test
    fun `an audio call turns video on with the camera, when video calls are on`() =
        runTest(main.dispatcher) {
            val world = world()
            answeredCall(world, video = false)
            assertTrue(world.state.videoAvailable)
            world.send(CallIntent.Camera(true))
            runCurrent()
            assertTrue(world.state.call!!.isVideo)

            settings.set(VideoSettings.Enabled, false)
            runCurrent()
            assertFalse(world.state.videoAvailable)
            advanceTimeBy(5.seconds)
        }

    @Test
    fun `an incoming video call is answered with video or audio only`() =
        runTest(main.dispatcher) {
            val world = world()
            world.engine.incomingCall(SipAddress("101", "lab.local"), video = true)
            runCurrent()
            assertTrue(world.state.call!!.videoOffered)
            world.send(CallIntent.AnswerWithVideo)
            runCurrent()
            assertTrue(world.state.call!!.cameraOn)
            assertTrue(world.state.call!!.video!!.sending)

            val other = world()
            other.engine.incomingCall(SipAddress("103", "lab.local"), video = true)
            runCurrent()
            other.send(CallIntent.Answer)
            runCurrent()
            assertFalse(other.state.call!!.cameraOn)
            assertTrue("the caller is still seen", other.state.call!!.showsRemoteVideo)
        }

    @Test
    fun `the display's turn is told to the engine as it changes`() =
        runTest(main.dispatcher) {
            val world = world()
            world.send(CallIntent.Rotation(90))
            runCurrent()
            assertEquals(90, world.engine.rotation)
            world.send(CallIntent.Rotation(270))
            runCurrent()
            assertEquals(270, world.engine.rotation)
        }
}
