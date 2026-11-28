/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.calling.domain

import com.bobbyesp.sona.sip.api.CallStatistics
import com.bobbyesp.sona.sip.api.StreamStatistics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallQualityTest {

    /** A stream that carries packets, good unless told otherwise. */
    private fun stream(
        loss: Float = 0.2f,
        remoteLoss: Float = 0.1f,
        jitter: Float = 5f,
        roundTrip: Float? = 40f,
    ) =
        StreamStatistics(
            packetsSent = 500,
            packetsReceived = 500,
            localLossPercent = loss,
            remoteLossPercent = remoteLoss,
            jitterMs = jitter,
            roundTripMs = roundTrip,
        )

    private fun of(audio: StreamStatistics? = stream(), video: StreamStatistics? = null) =
        CallQuality.of(CallStatistics(audio = audio, video = video))

    @Test
    fun `nothing measured has no grade yet`() {
        assertNull(of(audio = null))
        assertNull(of(audio = StreamStatistics()))
    }

    @Test
    fun `each number is graded on its own limits`() {
        val table =
            listOf(
                stream() to CallQuality.Good,
                stream(loss = 1.9f) to CallQuality.Good,
                stream(loss = 2f) to CallQuality.Fair,
                stream(loss = 5.9f) to CallQuality.Fair,
                stream(loss = 6f) to CallQuality.Poor,
                stream(remoteLoss = 7f) to CallQuality.Poor,
                stream(jitter = 29f) to CallQuality.Good,
                stream(jitter = 40f) to CallQuality.Fair,
                stream(jitter = 60f) to CallQuality.Poor,
                stream(roundTrip = 299f) to CallQuality.Good,
                stream(roundTrip = 400f) to CallQuality.Fair,
                stream(roundTrip = 600f) to CallQuality.Poor,
            )
        table.forEach { (audio, expected) -> assertEquals("$audio", expected, of(audio)) }
    }

    @Test
    fun `the worst number decides`() {
        assertEquals(CallQuality.Poor, of(stream(loss = 3f, roundTrip = 900f)))
        assertEquals(CallQuality.Fair, of(stream(loss = 3f, jitter = 35f, roundTrip = 350f)))
    }

    @Test
    fun `a round trip not measured yet is left out`() {
        assertEquals(CallQuality.Good, of(stream(roundTrip = null)))
        assertEquals(CallQuality.Good, of(stream(roundTrip = 0f)))
    }

    @Test
    fun `the video's loss counts, its jitter does not`() {
        assertEquals(CallQuality.Poor, of(video = stream(loss = 8f)))
        assertEquals(CallQuality.Good, of(video = stream(jitter = 200f)))
    }

    @Test
    fun `a call with only video measured is graded on it`() {
        assertEquals(CallQuality.Fair, of(audio = null, video = stream(roundTrip = 450f)))
    }
}
