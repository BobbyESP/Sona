/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.call.video

import com.bobbyesp.sona.calling.domain.Call
import com.bobbyesp.sona.calling.domain.CallQuality
import com.bobbyesp.sona.calling.domain.CallState
import com.bobbyesp.sona.core.model.CallId
import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.sip.api.CallDirection
import com.bobbyesp.sona.sip.api.CallVideo
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchesTest {

    @Test
    fun `a weak connection is said after five Poor seconds in a row, in a video call only`() {
        val watch = WeakConnectionWatch()
        val seen = List(5) { watch.update(CallQuality.Poor, isVideo = true) }
        assertEquals(listOf(false, false, false, false, true), seen)
        assertTrue(watch.update(CallQuality.Poor, isVideo = true))

        val audio = WeakConnectionWatch()
        assertFalse((1..10).map { audio.update(CallQuality.Poor, isVideo = false) }.any { it })
    }

    @Test
    fun `one better second starts the weak count again`() {
        val watch = WeakConnectionWatch()
        repeat(4) { watch.update(CallQuality.Poor, isVideo = true) }
        assertFalse(watch.update(CallQuality.Fair, isVideo = true))
        repeat(4) { assertFalse(watch.update(CallQuality.Poor, isVideo = true)) }
        assertTrue(watch.update(CallQuality.Poor, isVideo = true))
        assertFalse(watch.update(null, isVideo = true))
    }

    @Test
    fun `video freezes after three seconds without a frame, and the first frame lifts it`() {
        val watch = FrozenVideoWatch()
        assertFalse(watch.update(expected = true, receivedFps = 24f))
        assertFalse(watch.update(expected = true, receivedFps = 0f))
        assertFalse(watch.update(expected = true, receivedFps = null))
        assertTrue(watch.update(expected = true, receivedFps = 0f))
        assertTrue(watch.update(expected = true, receivedFps = 0f))
        assertFalse(watch.update(expected = true, receivedFps = 1f))
    }

    @Test
    fun `no picture expected is never frozen`() {
        val watch = FrozenVideoWatch()
        repeat(10) { assertFalse(watch.update(expected = false, receivedFps = 0f)) }
        // Expected again: three seconds from then.
        assertFalse(watch.update(expected = true, receivedFps = 0f))
        assertFalse(watch.update(expected = true, receivedFps = 0f))
        assertTrue(watch.update(expected = true, receivedFps = 0f))
    }

    @Test
    fun `the other side turning its camera on is told when this side's camera is off`() {
        val audio = call(CallState.Active, video = null)
        val theirs =
            call(CallState.Active, CallVideo(false, receiving = true, remoteSending = true))

        assertTrue(remoteTurnedOnVideo(audio, theirs))
        assertFalse(remoteTurnedOnVideo(null, theirs))
        assertFalse(remoteTurnedOnVideo(theirs, theirs))
        // Answering a video offer audio only shows them, but they turned nothing on.
        assertFalse(remoteTurnedOnVideo(call(CallState.Incoming, null), theirs))
        // With this side's camera on there is nothing to offer.
        assertFalse(remoteTurnedOnVideo(audio, theirs.copy(cameraOn = true)))
        // Another call.
        assertFalse(remoteTurnedOnVideo(audio.copy(id = CallId("other")), theirs))
    }

    @Test
    fun `the ring buffer keeps the last values, oldest first`() {
        val buffer = RingBuffer(3)
        assertArrayEquals(floatArrayOf(), buffer.toFloatArray(), 0f)
        buffer.add(1f)
        buffer.add(2f)
        assertArrayEquals(floatArrayOf(1f, 2f), buffer.toFloatArray(), 0f)
        buffer.add(3f)
        buffer.add(4f)
        buffer.add(5f)
        assertEquals(3, buffer.size)
        assertArrayEquals(floatArrayOf(3f, 4f, 5f), buffer.toFloatArray(), 0f)
        assertEquals(3f, buffer[0])
        buffer.clear()
        assertEquals(0, buffer.size)
        buffer.add(6f)
        assertArrayEquals(floatArrayOf(6f), buffer.toFloatArray(), 0f)
    }

    @Test
    fun `a minute of history holds sixty seconds`() {
        val buffer = RingBuffer(StatsHistory.SECONDS)
        repeat(90) { buffer.add(it.toFloat()) }
        val kept = buffer.toFloatArray()
        assertEquals(60, kept.size)
        assertEquals(30f, kept.first())
        assertEquals(89f, kept.last())
    }

    private fun call(state: CallState, video: CallVideo?) =
        Call(
            id = CallId("c1"),
            account = null,
            direction = CallDirection.Outgoing,
            remote = SipAddress("101", "lab.local"),
            remoteName = "Alice",
            state = state,
            video = video,
        )
}
