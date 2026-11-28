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
import com.bobbyesp.sona.core.model.settings.VideoSettings
import com.bobbyesp.sona.core.testing.InMemorySettingsStore
import com.bobbyesp.sona.credentials.api.InMemoryCredentialVault
import com.bobbyesp.sona.sip.api.AudioDeviceKind
import com.bobbyesp.sona.sip.api.CameraFacing
import com.bobbyesp.sona.sip.api.EngineConfig
import com.bobbyesp.sona.sip.api.SipStatus
import com.bobbyesp.sona.sip.fake.FakeNetwork
import com.bobbyesp.sona.sip.fake.FakeSipEngine
import com.bobbyesp.sona.telecom.api.AudioRoute
import com.bobbyesp.sona.telecom.api.AudioRouteKind
import com.bobbyesp.sona.telecom.api.FakeSystemCall
import com.bobbyesp.sona.telecom.api.FakeSystemCalls
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
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
    /** The system the next world uses: replaced before `world()` for a headset plugged in. */
    private var system = FakeSystemCalls()
    private val alice = SipAddress("101", "lab.local")

    private class World(val engine: FakeSipEngine, val calls: CallCoordinator)

    /** A registered account, its engine, and a started coordinator. */
    private suspend fun TestScope.world(
        names: CallerNames = CallerNames.None,
        clock: () -> Long = { 1_000 },
        camera: CameraPermission = CameraPermission.Granted,
    ): World {
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
                clock = clock,
                names = names,
                camera = camera,
            )
        coordinator.start()
        runCurrent()
        return World(engine, coordinator)
    }

    private fun World.call(id: CallId) = calls.calls.value.single { it.id == id }

    private fun World.video(id: CallId) = call(id).video

    private fun systemCall(id: CallId): FakeSystemCall = system.calls.getValue(id)

    private suspend fun World.dial(input: String, video: Boolean = false): CallId =
        (calls.dial(input, video = video) as DialOutcome.Started).id

    private val World.foreground
        get() = calls.foreground.value

    private suspend fun TestScope.videoCall(world: World): CallId {
        val id = world.dial("600", video = true)
        world.engine.remoteAnswers(id)
        runCurrent()
        return id
    }

    private suspend fun TestScope.audioCall(world: World): CallId {
        val id = world.dial("600")
        world.engine.remoteAnswers(id)
        runCurrent()
        return id
    }

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
    fun `a server that answers and hangs up at once says so, a later hang-up is a normal end`() =
        runTest {
            var now = 1_000L
            val world = world(clock = { now })
            val quick = world.dial("*97")
            runCurrent()
            world.engine.remoteAnswers(quick)
            runCurrent()
            now += 300
            world.engine.remoteEnds(quick)
            runCurrent()
            assertEquals(CallState.Ended(EndReason.EndedAtOnce), world.call(quick).state)
            assertEquals("disconnect:Remote", systemCall(quick).log.last())

            advanceTimeBy(4.seconds)
            val long = world.dial("600")
            runCurrent()
            world.engine.remoteAnswers(long)
            runCurrent()
            now += 30_000
            world.engine.remoteEnds(long)
            runCurrent()
            assertEquals(CallState.Ended(EndReason.HungUp(byMe = false)), world.call(long).state)
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
    fun `a call refused before it rang is told, with how it was answered`() = runTest {
        settings.set(CallSettings.CallWaiting, false)
        val world = world()
        val refused = mutableListOf<RefusedCall>()
        val listening = launch { world.calls.refused.toList(refused) }
        runCurrent()

        // Busy during another call, which the user will want to hear about afterwards.
        world.engine.remoteAnswers(world.dial("600"))
        runCurrent()
        world.engine.incomingCall(alice, name = "Alice")
        runCurrent()

        // Do not disturb: written down quietly.
        settings.set(CallSettings.DoNotDisturb, true)
        settings.set(CallSettings.DeclineWith, DeclineMode.Declined)
        world.engine.incomingCall(alice)
        runCurrent()
        listening.cancel()

        assertEquals(listOf(alice, alice), refused.map { it.remote })
        assertEquals(listOf("Alice", null), refused.map { it.remoteName })
        assertEquals(listOf(486, 603), refused.map { it.status.code })
        assertEquals(listOf(false, true), refused.map { it.quiet })
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

    /**
     * Found on a phone: the speaker button moved the system's route, its own look and the proximity
     * lock, and the sound stayed in the earpiece, where the engine had opened its streams.
     */
    @Test
    fun `the engine's audio follows the route the system chose`() = runTest {
        val world = world()
        val id = world.dial("600")
        world.engine.remoteAnswers(id)
        runCurrent()
        assertEquals(AudioDeviceKind.Earpiece, world.engine.audioDevices[id])

        world.calls.selectRoute(id, FakeSystemCall.Speaker)
        runCurrent()
        assertEquals(AudioDeviceKind.Speaker, world.engine.audioDevices[id])

        // A headset plugged in: the system moves the call by itself, and the engine follows too.
        systemCall(id).route.value = AudioRoute("wired", AudioRouteKind.WiredHeadset, "Headset")
        runCurrent()
        assertEquals(AudioDeviceKind.WiredHeadset, world.engine.audioDevices[id])
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

    // Video: the decisions of docs/video.md, one scenario each.

    @Test
    fun `dialing with video makes a video call, with the camera on and the speaker chosen`() =
        runTest {
            val world = world()
            val id = world.dial("600", video = true)
            runCurrent()

            assertTrue(systemCall(id).request.video)
            assertTrue(world.call(id).cameraOn)
            assertTrue(world.video(id)!!.sending)
            assertTrue(world.call(id).isVideo)
            assertEquals(listOf("route:Speaker"), systemCall(id).log)
            assertEquals(FakeSystemCall.Speaker, world.call(id).route)
            assertEquals(CallForeground.Calls(microphone = true, camera = true), world.foreground)
        }

    @Test
    fun `a video call keeps a headset that is the route`() = runTest {
        system =
            FakeSystemCalls(
                route = FakeSystemCall.Headset,
                routes = listOf(FakeSystemCall.Speaker, FakeSystemCall.Headset),
            )
        val world = world()
        val id = world.dial("600", video = true)
        runCurrent()

        assertTrue(systemCall(id).log.none { it.startsWith("route:") })
        assertEquals(FakeSystemCall.Headset, world.call(id).route)
    }

    @Test
    fun `a call offering video rings as one, and answering with video turns the camera on`() =
        runTest {
            val world = world()
            val id = world.engine.incomingCall(alice, video = true)
            runCurrent()
            assertTrue(world.call(id).videoOffered)
            assertTrue(systemCall(id).request.video)
            assertEquals(CallForeground.Calls(microphone = false), world.foreground)

            world.calls.answer(id, video = true)
            runCurrent()

            assertEquals("answer:video", systemCall(id).log.first())
            assertTrue(world.call(id).cameraOn)
            assertTrue(world.video(id)!!.sending)
            assertTrue(world.call(id).showsRemoteVideo)
            assertEquals(CallForeground.Calls(microphone = true, camera = true), world.foreground)
        }

    @Test
    fun `answering audio only shows the caller with the camera off, and it can be turned on`() =
        runTest {
            val world = world()
            val id = world.engine.incomingCall(alice, video = true)
            runCurrent()

            world.calls.answer(id)
            runCurrent()

            assertEquals(false, world.call(id).cameraOn)
            assertEquals(false, world.video(id)!!.sending)
            assertTrue(world.call(id).showsRemoteVideo)
            assertEquals(CallForeground.Calls(microphone = true, camera = false), world.foreground)

            world.calls.setCameraOn(id, true)
            runCurrent()

            assertEquals(
                FakeSipEngine.VideoRequest(id, true, true),
                world.engine.videoRequests.last(),
            )
            assertTrue(world.video(id)!!.sending)
            assertEquals(CallForeground.Calls(microphone = true, camera = true), world.foreground)
        }

    @Test
    fun `video the other side adds is shown with the camera off, on the speaker`() = runTest {
        val world = world()
        val id = audioCall(world)

        world.engine.remoteStartsVideo(id)
        runCurrent()

        assertTrue(world.call(id).showsRemoteVideo)
        assertEquals(false, world.call(id).cameraOn)
        assertEquals(false, world.video(id)!!.sending)
        assertEquals(listOf("active", "video:true", "route:Speaker"), systemCall(id).log)
        assertEquals(CallForeground.Calls(microphone = true, camera = false), world.foreground)
    }

    /** Privacy does not rest on the engine: the camera is never on unless the user turned it on. */
    @Test
    fun `an engine that took added video with the camera on is told to turn it off`() = runTest {
        val world = world()
        val id = audioCall(world)

        world.engine.remoteStartsVideo(id, withCamera = true)
        runCurrent()

        assertEquals(
            listOf(FakeSipEngine.VideoRequest(id, send = false, receive = true)),
            world.engine.videoRequests,
        )
        assertEquals(false, world.video(id)!!.sending)
        assertTrue(world.call(id).showsRemoteVideo)
    }

    @Test
    fun `the camera off and on is a change of direction, and the service's camera follows`() =
        runTest {
            val world = world()
            val id = videoCall(world)

            world.calls.setCameraOn(id, false)
            runCurrent()
            assertEquals(
                FakeSipEngine.VideoRequest(id, false, true),
                world.engine.videoRequests.last(),
            )
            assertEquals(false, world.video(id)!!.sending)
            // The other side is still seen.
            assertTrue(world.call(id).isVideo)
            assertEquals(CallForeground.Calls(microphone = true, camera = false), world.foreground)

            world.calls.setCameraOn(id, true)
            runCurrent()
            assertEquals(
                FakeSipEngine.VideoRequest(id, true, true),
                world.engine.videoRequests.last(),
            )
            assertEquals(CallForeground.Calls(microphone = true, camera = true), world.foreground)
        }

    @Test
    fun `the camera turned off while ringing is asked once the call is up`() = runTest {
        val world = world()
        val id = world.dial("600", video = true)
        runCurrent()

        world.calls.setCameraOn(id, false)
        runCurrent()
        assertTrue(world.engine.videoRequests.isEmpty())

        world.engine.remoteAnswers(id)
        runCurrent()
        assertEquals(
            listOf(FakeSipEngine.VideoRequest(id, false, true)),
            world.engine.videoRequests,
        )
        assertEquals(false, world.video(id)!!.sending)
    }

    @Test
    fun `a video call answered without video turns the camera off`() = runTest {
        val world = world()
        val id = world.dial("600", video = true)
        runCurrent()

        world.engine.remoteAnswers(id, video = false)
        runCurrent()

        assertEquals(null, world.video(id))
        assertEquals(false, world.call(id).cameraOn)
        assertEquals("video:false", systemCall(id).log.last { it.startsWith("video:") })
        assertEquals(CallForeground.Calls(microphone = true, camera = false), world.foreground)
    }

    @Test
    fun `video that ends leaves the route as it is, and the next one looks at it again`() =
        runTest {
            val world = world()
            val id = audioCall(world)
            world.engine.remoteStartsVideo(id)
            runCurrent()

            world.engine.remoteStopsVideo(id)
            runCurrent()

            assertEquals(false, world.call(id).isVideo)
            assertEquals(FakeSystemCall.Speaker, world.call(id).route)
            assertEquals(
                listOf("active", "video:true", "route:Speaker", "video:false"),
                systemCall(id).log,
            )

            // The user goes back to the earpiece; video coming back moves to the speaker again.
            world.calls.selectRoute(id, FakeSystemCall.Earpiece)
            runCurrent()
            world.engine.remoteStartsVideo(id)
            runCurrent()
            assertEquals("route:Speaker", systemCall(id).log.last())
        }

    @Test
    fun `a route chosen during video is the user's`() = runTest {
        val world = world()
        val id = videoCall(world)

        world.calls.selectRoute(id, FakeSystemCall.Earpiece)
        runCurrent()
        world.calls.setCameraOn(id, false)
        runCurrent()

        assertEquals(FakeSystemCall.Earpiece, world.call(id).route)
        assertEquals("route:Earpiece", systemCall(id).log.last())
    }

    @Test
    fun `hold stops the video, and resuming brings back the directions held before`() = runTest {
        val world = world()
        val id = videoCall(world)

        world.calls.setOnHold(id, true)
        runCurrent()
        assertEquals(CallState.OnHold, world.call(id).state)
        assertEquals(false, world.call(id).isVideo)
        assertEquals(CallForeground.Calls(microphone = true, camera = false), world.foreground)

        world.calls.setOnHold(id, false)
        runCurrent()

        assertEquals(FakeSipEngine.VideoRequest(id, true, true), world.engine.videoRequests.last())
        assertTrue(world.video(id)!!.sending)
        assertTrue(world.call(id).showsRemoteVideo)
        assertEquals(CallForeground.Calls(microphone = true, camera = true), world.foreground)
        // The system kept it a video call throughout.
        assertTrue(systemCall(id).log.none { it == "video:false" })
    }

    @Test
    fun `the camera turned off on hold stays off on resume`() = runTest {
        val world = world()
        val id = videoCall(world)
        world.calls.setOnHold(id, true)
        runCurrent()

        world.calls.setCameraOn(id, false)
        world.calls.setOnHold(id, false)
        runCurrent()

        assertEquals(FakeSipEngine.VideoRequest(id, false, true), world.engine.videoRequests.last())
        assertEquals(false, world.video(id)!!.sending)
        assertEquals(false, world.call(id).cameraOn)
    }

    @Test
    fun `without the camera permission a video call is dialed as audio, and says why`() = runTest {
        val world = world(camera = { false })
        val id = world.dial("600", video = true)
        runCurrent()

        assertEquals(null, world.video(id))
        assertEquals(false, world.call(id).cameraOn)
        assertEquals(CameraBlocked.NoPermission, world.call(id).cameraBlocked)
        assertEquals(false, systemCall(id).request.video)

        world.engine.remoteAnswers(id)
        world.calls.setCameraOn(id, true)
        runCurrent()
        assertTrue(world.engine.videoRequests.isEmpty())
        assertEquals(CameraBlocked.NoPermission, world.call(id).cameraBlocked)
        assertEquals(CallForeground.Calls(microphone = true, camera = false), world.foreground)
    }

    @Test
    fun `without the camera permission answering with video answers with the camera off`() =
        runTest {
            val world = world(camera = { false })
            val id = world.engine.incomingCall(alice, video = true)
            runCurrent()

            world.calls.answer(id, video = true)
            runCurrent()

            assertEquals(CallState.Active, world.call(id).state)
            assertEquals(false, world.video(id)!!.sending)
            assertEquals(CameraBlocked.NoPermission, world.call(id).cameraBlocked)
        }

    @Test
    fun `another device's answer never turns the camera on`() = runTest {
        val world = world()
        val id = world.engine.incomingCall(alice, video = true)
        runCurrent()

        system.events.getValue(id).onAnswer()
        runCurrent()

        assertEquals(CallState.Active, world.call(id).state)
        assertEquals(false, world.call(id).cameraOn)
        assertEquals(false, world.video(id)!!.sending)
    }

    @Test
    fun `with video off in the settings, calls are audio both ways`() = runTest {
        settings.set(VideoSettings.Enabled, false)
        val world = world()

        val dialed = world.dial("600", video = true)
        val incoming = world.engine.incomingCall(alice, video = true)
        runCurrent()

        assertEquals(null, world.video(dialed))
        assertEquals(false, world.call(dialed).cameraOn)
        assertEquals(null, world.call(dialed).cameraBlocked)
        assertEquals(false, world.call(incoming).videoOffered)
        assertEquals(false, systemCall(incoming).request.video)
    }

    @Test
    fun `turning video off keeps the voice, and on again shows the other side, camera off`() =
        runTest {
            val world = world()
            val id = videoCall(world)

            world.calls.setVideoEnabled(id, false)
            runCurrent()
            assertEquals(null, world.video(id))
            assertEquals(CallState.Active, world.call(id).state)
            assertEquals(false, world.call(id).cameraOn)
            assertEquals("video:false", systemCall(id).log.last())

            world.calls.setVideoEnabled(id, true)
            runCurrent()
            assertEquals(
                FakeSipEngine.VideoRequest(id, false, true),
                world.engine.videoRequests.last(),
            )
            assertTrue(world.call(id).showsRemoteVideo)
            assertEquals(false, world.call(id).cameraOn)
        }

    @Test
    fun `switching the camera and the display's rotation reach the engine`() = runTest {
        val world = world()
        val id = videoCall(world)

        world.calls.switchCamera(id)
        world.calls.setDeviceRotation(268)
        runCurrent()

        assertEquals(CameraFacing.Back, world.video(id)!!.camera)
        assertEquals(270, world.engine.rotation)
    }

    @Test
    fun `statistics are measured only while looked at, and a weak network reads as poor`() =
        runTest {
            val world = world()
            val good = audioCall(world)
            advanceTimeBy(5.seconds)
            assertEquals(0, world.engine.statisticsSamples)

            assertEquals(CallQuality.Good, world.calls.quality(good).first())
            assertEquals(1, world.engine.statisticsSamples)

            world.engine.network(good, FakeNetwork.Weak)
            assertEquals(CallQuality.Poor, world.calls.quality(good).first())
            assertEquals(50L, world.calls.statistics(good).first().audio!!.packetsSent)
        }

    @Test
    fun `a rotation is taken to the nearest quarter turn`() {
        assertEquals(
            listOf(0, 0, 90, 90, 180, 270, 270, 0),
            listOf(0, 44, 45, 100, 200, 268, -90, 359).map(::quarterTurn),
        )
    }
}
