/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.fake

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.CallId
import com.bobbyesp.sona.core.model.Secret
import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.sip.api.AccountSpec
import com.bobbyesp.sona.sip.api.CallVideo
import com.bobbyesp.sona.sip.api.CameraFacing
import com.bobbyesp.sona.sip.api.DialResult
import com.bobbyesp.sona.sip.api.EngineCall
import com.bobbyesp.sona.sip.api.EngineConfig
import com.bobbyesp.sona.sip.api.ServerEndpoint
import com.bobbyesp.sona.sip.api.Transport
import com.bobbyesp.sona.sip.api.VideoConfig
import com.bobbyesp.sona.sip.api.VideoSize
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeVideoTest {

    private val lab =
        AccountSpec(
            id = AccountId("lab"),
            label = "Lab",
            identity = SipAddress("102", "lab.local"),
            password = Secret("secret"),
            registrar = ServerEndpoint("lab.local", 5060, Transport.Tcp),
        )
    private val alice = SipAddress("101", "lab.local")
    private val echo = SipAddress("600", "lab.local")

    private suspend fun TestScope.engine(
        video: VideoConfig = VideoConfig(),
        demo: Boolean = false,
    ): FakeSipEngine {
        val engine = FakeSipEngine(this, demoCalls = demo)
        engine.start(EngineConfig(video = video))
        engine.setAccounts(listOf(lab))
        advanceUntilIdle()
        return engine
    }

    private fun FakeSipEngine.call(id: CallId): EngineCall = calls.value.single { it.id == id }

    private fun FakeSipEngine.videoOf(id: CallId): CallVideo? = call(id).video

    private suspend fun FakeSipEngine.dialed(video: Boolean = false): CallId =
        (dial(lab.id, echo, video) as DialResult.Started).id

    @Test
    fun `a call dialed with video sends the camera, and the answer brings the other side's`() =
        runTest {
            val engine = engine()
            val id = engine.dialed(video = true)

            val ringing = engine.videoOf(id)!!
            assertTrue(ringing.sending)
            assertEquals(false, ringing.remoteSending)
            assertEquals(CameraFacing.Front, ringing.camera)
            // 720p from the configuration, upright as a phone is held.
            assertEquals(VideoSize(720, 1280), ringing.sentSize)

            engine.remoteAnswers(id)
            assertTrue(engine.videoOf(id)!!.showsRemote)
            assertEquals(VideoSize(720, 1280), engine.videoOf(id)!!.receivedSize)
        }

    @Test
    fun `an answer that declines the video leaves an audio call`() = runTest {
        val engine = engine()
        val id = engine.dialed(video = true)

        engine.remoteAnswers(id, video = false)

        assertNull(engine.videoOf(id))
    }

    @Test
    fun `offered video is taken receive-only when answered without the camera`() = runTest {
        val engine = engine()
        val id = engine.incomingCall(alice, video = true)
        assertTrue(engine.call(id).videoOffered)

        engine.answer(id, video = false)

        assertEquals(
            CallVideo(sending = false, receiving = true, remoteSending = true),
            engine.videoOf(id)!!.copy(receivedSize = null, codec = null),
        )
    }

    @Test
    fun `answered with the camera, offered video is sent both ways`() = runTest {
        val engine = engine()
        val id = engine.incomingCall(alice, video = true)

        engine.answer(id, video = true)

        assertTrue(engine.videoOf(id)!!.sending)
        assertTrue(engine.videoOf(id)!!.showsRemote)
    }

    @Test
    fun `with video off nothing is offered or taken`() = runTest {
        val engine = engine(video = VideoConfig(enabled = false))
        val dialed = engine.dialed(video = true)
        val incoming = engine.incomingCall(alice, video = true)
        engine.answer(incoming, video = true)

        assertNull(engine.videoOf(dialed))
        assertEquals(false, engine.call(incoming).videoOffered)
        assertNull(engine.videoOf(incoming))
    }

    @Test
    fun `video the other side adds is taken receive-only, or declined when not accepted`() =
        runTest {
            val accepting = engine()
            val id = accepting.dialed()
            accepting.remoteAnswers(id)

            accepting.remoteStartsVideo(id)

            val video = accepting.videoOf(id)!!
            assertEquals(false, video.sending)
            assertTrue(video.showsRemote)

            val refusing = engine(video = VideoConfig(acceptRemoteVideo = false))
            val other = refusing.dialed()
            refusing.remoteAnswers(other)
            refusing.remoteStartsVideo(other)
            assertNull(refusing.videoOf(other))
        }

    @Test
    fun `the other side's camera off ends a video this side does not send`() = runTest {
        val engine = engine()
        val watching = engine.dialed()
        engine.remoteAnswers(watching)
        engine.remoteStartsVideo(watching)
        engine.remoteStopsVideo(watching)
        assertNull(engine.videoOf(watching))

        val sending = engine.dialed(video = true)
        engine.remoteAnswers(sending)
        engine.remoteStopsVideo(sending)
        assertEquals(false, engine.videoOf(sending)!!.remoteSending)
        assertTrue(engine.videoOf(sending)!!.sending)
    }

    @Test
    fun `directions are what was asked, and both off drops the video`() = runTest {
        val engine = engine()
        val id = engine.dialed(video = true)
        engine.remoteAnswers(id)

        engine.setVideo(id, send = false, receive = true)
        assertEquals(false, engine.videoOf(id)!!.sending)
        assertNull(engine.videoOf(id)!!.sentSize)

        engine.setVideo(id, send = false, receive = false)
        assertNull(engine.videoOf(id))
        assertEquals(
            listOf(
                FakeSipEngine.VideoRequest(id, send = false, receive = true),
                FakeSipEngine.VideoRequest(id, send = false, receive = false),
            ),
            engine.videoRequests,
        )
    }

    @Test
    fun `hold stops the video, and resuming leaves it stopped`() = runTest {
        val engine = engine()
        val id = engine.dialed(video = true)
        engine.remoteAnswers(id)

        engine.hold(id, onHold = true)
        assertEquals(false, engine.videoOf(id)!!.isActive)

        engine.hold(id, onHold = false)
        assertEquals(false, engine.videoOf(id)!!.isActive)
    }

    @Test
    fun `switching the camera swaps front and back, and the rotation turns what is sent`() =
        runTest {
            val engine = engine()
            val id = engine.dialed(video = true)

            engine.switchCamera(id)
            assertEquals(CameraFacing.Back, engine.videoOf(id)!!.camera)
            engine.switchCamera(id)
            assertEquals(CameraFacing.Front, engine.videoOf(id)!!.camera)

            engine.setDeviceRotation(90)
            assertEquals(90, engine.rotation)
            assertEquals(VideoSize.Hd720, engine.videoOf(id)!!.sentSize)
        }

    @Test
    fun `statistics are measured once a second, only while collected`() = runTest {
        val engine = engine()
        val id = engine.dialed(video = true)
        engine.remoteAnswers(id)
        advanceTimeBy(10.seconds)
        assertEquals(0, engine.statisticsSamples)

        val samples = engine.statistics(id).take(3).toList()

        assertEquals(3, engine.statisticsSamples)
        assertEquals(listOf(50L, 100L, 150L), samples.map { it.audio!!.packetsSent })
        assertEquals(Transport.Tcp, samples.first().transport)
        assertTrue(samples.all { it.video!!.uploadKbps > 0f })
        advanceTimeBy(10.seconds)
        assertEquals(3, engine.statisticsSamples)
    }

    @Test
    fun `the numbers are the same every run, and a weak network shows`() = runTest {
        val engine = engine()
        val id = engine.dialed()
        engine.remoteAnswers(id)

        val first = engine.statistics(id).take(5).toList()
        val again = engine.statistics(id).take(5).toList()
        assertEquals(first.map { it.audio!!.jitterMs }, again.map { it.audio!!.jitterMs })
        assertTrue(first.all { it.audio!!.localLossPercent < 1f })

        engine.network(id, FakeNetwork.Weak)
        val weak = engine.statistics(id).take(5).toList()
        assertTrue(weak.all { it.audio!!.localLossPercent > 4f })
        assertTrue(weak.all { it.audio!!.roundTripMs!! > 300f })
    }

    @Test
    fun `statistics end with the call`() = runTest {
        val engine = engine()
        val id = engine.dialed()
        engine.remoteAnswers(id)
        engine.hangUp(id)

        assertTrue(engine.statistics(id).toList().isEmpty())
        assertTrue(engine.statistics(CallId("nobody")).toList().isEmpty())
    }

    @Test
    fun `the demo answers a video call with video, and 555 turns its camera on later`() = runTest {
        val engine = engine(demo = true)
        val video = engine.dialed(video = true)
        val later = (engine.dial(lab.id, SipAddress("555", "lab.local")) as DialResult.Started).id
        advanceTimeBy(3.seconds)

        assertTrue(engine.videoOf(video)!!.showsRemote)
        assertNull(engine.videoOf(later))

        advanceUntilIdle()
        assertTrue(engine.videoOf(later)!!.showsRemote)
        assertEquals(false, engine.videoOf(later)!!.sending)
    }
}
