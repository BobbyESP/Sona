/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.linphone

import org.junit.Assert.assertEquals
import org.junit.Test

class CodecPlanTest {
    /** A payload as Linphone lists it: its SDP name and clock rate. */
    private data class Payload(val mime: String, val rate: Int)

    private val linphone =
        listOf(
            Payload("opus", 48_000),
            Payload("speex", 16_000),
            Payload("speex", 8_000),
            Payload("PCMU", 8_000),
            Payload("PCMA", 8_000),
            Payload("GSM", 8_000),
            Payload("G722", 8_000),
            Payload("iLBC", 8_000),
        )

    private fun plan(vararg wanted: String) =
        codecPlan(linphone, wanted.toList()) { it.mime }
            .map { (payload, on) -> "${if (on) "" else "-"}${payload.mime}/${payload.rate}" }

    @Test
    fun `the wanted codecs come first in their order, the others follow turned off`() {
        assertEquals(
            listOf(
                "G722/8000",
                "opus/48000",
                "PCMU/8000",
                "-speex/16000",
                "-speex/8000",
                "-PCMA/8000",
                "-GSM/8000",
                "-iLBC/8000",
            ),
            plan("G722", "opus", "PCMU"),
        )
    }

    @Test
    fun `a name at several clock rates turns on each of them`() {
        assertEquals(listOf("speex/16000", "speex/8000"), plan("speex").take(2))
    }

    @Test
    fun `names are matched without case, and one the engine lacks is skipped`() {
        assertEquals(listOf("opus/48000", "G722/8000"), plan("OPUS", "amr", "g722").take(2))
    }

    @Test
    fun `no codec is lost or listed twice`() {
        assertEquals(linphone.size, plan("PCMU", "PCMU", "opus").size)
        assertEquals(linphone.size, plan().size)
    }
}
