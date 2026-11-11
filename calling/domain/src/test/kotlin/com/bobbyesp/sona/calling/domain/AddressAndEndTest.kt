/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.calling.domain

import com.bobbyesp.sona.calling.domain.AddressResolution.Invalid
import com.bobbyesp.sona.calling.domain.AddressResolution.Reason
import com.bobbyesp.sona.calling.domain.AddressResolution.Resolved
import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.sip.api.CallDirection
import com.bobbyesp.sona.sip.api.EngineCallState
import com.bobbyesp.sona.sip.api.SipStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressResolverTest {

    private fun resolve(input: String) = AddressResolver.resolve(input, domain = "lab.local")

    @Test
    fun `an extension is called on the account's domain`() {
        assertEquals(Resolved(SipAddress("102", "lab.local")), resolve("102"))
        assertEquals(Resolved(SipAddress("*97", "lab.local")), resolve("*97"))
        assertEquals(Resolved(SipAddress("600#", "lab.local")), resolve(" 600# "))
    }

    @Test
    fun `a number keeps its plus and loses its punctuation`() {
        assertEquals(Resolved(SipAddress("+34600112233", "lab.local")), resolve("+34 600 11 22 33"))
        assertEquals(Resolved(SipAddress("600112233", "lab.local")), resolve("(600) 112-233"))
        assertEquals(Resolved(SipAddress("+34600112233", "lab.local")), resolve("tel:+34600112233"))
    }

    @Test
    fun `addresses are kept, with or without their scheme`() {
        assertEquals(Resolved(SipAddress("alice", "example.org")), resolve("sip:alice@example.org"))
        assertEquals(Resolved(SipAddress("alice", "example.org")), resolve("alice@example.org"))
    }

    @Test
    fun `what is not an address says so`() {
        assertEquals(Invalid(Reason.Empty), resolve("  "))
        assertEquals(Invalid(Reason.NotAnAddress), resolve("call alice"))
        assertEquals(Invalid(Reason.NotAnAddress), resolve("+"))
        assertEquals(Invalid(Reason.NotAnAddress), resolve("sip:"))
    }
}

class EndReasonTest {

    private fun end(code: Int?, byRemote: Boolean = true, detail: String? = null) =
        EngineCallState.Ended(code?.let { SipStatus(it, "x") }, byRemote, detail)

    private fun out(ended: EngineCallState.Ended, answered: Boolean = false) =
        EndReason.of(ended, CallDirection.Outgoing, answered)

    @Test
    fun `each failure of an outgoing call has its reason`() {
        assertTrue(out(end(486)) is EndReason.Busy)
        assertTrue(out(end(603)) is EndReason.Declined)
        assertTrue(out(end(480)) is EndReason.Unavailable)
        assertTrue(out(end(404)) is EndReason.NotFound)
        assertTrue(out(end(408)) is EndReason.NoAnswer)
        assertTrue(out(end(401)) is EndReason.Unauthorized)
        assertTrue(out(end(488)) is EndReason.NoCommonMedia)
        assertTrue(out(end(503)) is EndReason.ServerUnavailable)
        assertTrue(out(end(500)) is EndReason.Failed)
    }

    @Test
    fun `nothing sent is the server being unavailable, with what was tried`() {
        assertEquals(
            EndReason.ServerUnavailable(null, "Could not send to sip:600@lab.local"),
            out(end(null, detail = "Could not send to sip:600@lab.local")),
        )
    }

    @Test
    fun `an answered call whose audio stopped arriving lost its media`() {
        assertEquals(
            EndReason.MediaLost("Could not send to sip:600@lab.local"),
            out(end(null, detail = "Could not send to sip:600@lab.local"), answered = true),
        )
    }

    @Test
    fun `a normal end says who hung up`() {
        assertEquals(
            EndReason.HungUp(byMe = true),
            out(end(null, byRemote = false), answered = true),
        )
        assertEquals(EndReason.HungUp(byMe = false), out(end(null), answered = true))
    }

    @Test
    fun `a server that answers and hangs up at once is told apart from a normal hang-up`() {
        fun after(ms: Long?, byRemote: Boolean = true) =
            EndReason.of(end(null, byRemote), CallDirection.Outgoing, true, connectedForMs = ms)

        assertEquals(EndReason.EndedAtOnce, after(400))
        assertEquals(EndReason.HungUp(byMe = false), after(EndReason.AT_ONCE_MS))
        assertEquals(EndReason.HungUp(byMe = false), after(null))
        // I hung up quickly: that is not the server's doing.
        assertEquals(EndReason.HungUp(byMe = true), after(400, byRemote = false))
    }

    @Test
    fun `an unanswered call is missed when it rang here, cancelled when it was dialed here`() {
        assertEquals(
            EndReason.Missed,
            EndReason.of(end(null), CallDirection.Incoming, answered = false),
        )
        assertEquals(EndReason.Cancelled, out(end(null, byRemote = false)))
    }
}
