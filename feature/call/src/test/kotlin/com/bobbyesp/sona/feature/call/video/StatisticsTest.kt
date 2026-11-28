/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.call.video

import com.bobbyesp.sona.sip.api.CallStatistics
import com.bobbyesp.sona.sip.api.IceState
import com.bobbyesp.sona.sip.api.IpFamily
import com.bobbyesp.sona.sip.api.StreamStatistics
import com.bobbyesp.sona.sip.api.Transport
import com.bobbyesp.sona.sip.api.VideoFrames
import com.bobbyesp.sona.sip.api.VideoSize
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatisticsTest {

    @Test
    fun `the history adds both streams' bitrates and keeps the frame rates`() {
        val history = StatsHistory()
        history.add(
            CallStatistics(
                audio = StreamStatistics(uploadKbps = 40f, downloadKbps = 38f),
                video =
                    StreamStatistics(
                        uploadKbps = 500f,
                        downloadKbps = 1000f,
                        frames = VideoFrames(sentFps = 30f, receivedFps = 24f),
                    ),
            ),
        )
        history.add(CallStatistics(audio = StreamStatistics(uploadKbps = 6f, downloadKbps = 40f)))

        val series = history.snapshot()
        assertArrayEquals(floatArrayOf(540f, 6f), series.up, 0.001f)
        assertArrayEquals(floatArrayOf(1038f, 40f), series.down, 0.001f)
        assertArrayEquals(floatArrayOf(30f, 0f), series.sentFps, 0f)
        assertArrayEquals(floatArrayOf(24f, 0f), series.receivedFps, 0f)

        history.clear()
        assertEquals(0, history.snapshot().up.size)
    }

    @Test
    fun `a snapshot does not change as the history goes on`() {
        val history = StatsHistory()
        history.add(CallStatistics(audio = StreamStatistics(uploadKbps = 1f)))
        val before = history.snapshot()
        history.add(CallStatistics(audio = StreamStatistics(uploadKbps = 2f)))
        assertArrayEquals(floatArrayOf(1f), before.up, 0f)
    }

    @Test
    fun `bitrates read in kbit per second, megabits past a thousand`() {
        assertEquals("42 kbit/s", StatsFormat.kbps(41.6f))
        assertEquals("1.5 Mbit/s", StatsFormat.kbps(1500f))
        assertEquals("—", StatsFormat.kbps(null))
        assertEquals("512 kbit/s ↑ · 1.2 Mbit/s ↓", StatsFormat.upDown(512f, 1200f))
    }

    @Test
    fun `loss, times and frame rates`() {
        assertEquals("0.4 % · 1.2 %", StatsFormat.percentPair(0.42f, 1.18f))
        assertEquals("38 ms", StatsFormat.ms(37.6f))
        assertEquals("—", StatsFormat.ms(null))
        assertEquals("30 fps", StatsFormat.fps(29.7f))
        assertEquals("1280×720 · 30 fps", StatsFormat.picture(VideoSize.Hd720, 30f))
        assertEquals("640×480", StatsFormat.picture(VideoSize.Vga, 0f))
        assertEquals("—", StatsFormat.picture(null, null))
    }

    @Test
    fun `a codec reads with its clock rate`() {
        assertEquals("opus · 48 kHz", StatsFormat.codec("opus", 48_000))
        assertEquals("G722 · 8 kHz", StatsFormat.codec("G722", 8_000))
        assertEquals("L16 · 44.1 kHz", StatsFormat.codec("L16", 44_100))
        assertEquals("VP8", StatsFormat.codec("VP8", null))
        assertEquals("—", StatsFormat.codec(null, 90_000))
    }

    @Test
    fun `the transport reads as in a trace`() {
        assertEquals("TLS", StatsFormat.transport(Transport.Tls))
        assertEquals("IPv6", StatsFormat.ipFamily(IpFamily.V6))
        assertEquals("srflx", StatsFormat.ice(IceState.Reflexive))
        assertEquals(
            "SRTP · AES_CM_128_HMAC_SHA1_80",
            StatsFormat.encryption("SRTP", "AES_CM_128_HMAC_SHA1_80"),
        )
        assertEquals("ZRTP", StatsFormat.encryption("ZRTP", null))
        assertNull(StatsFormat.encryption(null, "AES_CM_128_HMAC_SHA1_80"))
    }
}
