/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.linphone

import com.bobbyesp.sona.sip.api.EngineCallState
import com.bobbyesp.sona.sip.api.SipStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.linphone.core.Call.State as Linphone

class CallStateMapperTest {

    private fun map(
        state: Linphone,
        code: Int = 0,
        phrase: String? = null,
        sendFailed: Boolean = false,
        endedHere: Boolean = false,
    ) = callStateOf(state, code, phrase, sendFailed, endedHere, remote = "sip:600@lab.local")

    @Test
    fun `an outgoing call dials, then rings`() {
        assertEquals(EngineCallState.Dialing, map(Linphone.OutgoingProgress))
        assertEquals(EngineCallState.Ringing, map(Linphone.OutgoingRinging))
        assertEquals(EngineCallState.Ringing, map(Linphone.OutgoingEarlyMedia))
    }

    @Test
    fun `answered calls are active, on either side's hold they say whose`() {
        assertEquals(EngineCallState.Active, map(Linphone.StreamsRunning))
        assertEquals(EngineCallState.LocalHold, map(Linphone.Paused))
        assertEquals(EngineCallState.RemoteHold, map(Linphone.PausedByRemote))
    }

    @Test
    fun `a busy extension ends the call with its status, from the other side`() {
        assertEquals(
            EngineCallState.Ended(SipStatus(486, "Busy Here"), byRemote = true),
            map(Linphone.Error, code = 486, phrase = "Busy Here"),
        )
    }

    @Test
    fun `a hang-up here is a normal end, not a failure`() {
        assertEquals(
            EngineCallState.Ended(status = null, byRemote = false),
            map(Linphone.End, code = 200, endedHere = true),
        )
    }

    @Test
    fun `a cancelled call (487) is not a failure`() {
        assertEquals(
            EngineCallState.Ended(status = null, byRemote = true),
            map(Linphone.End, code = 487),
        )
    }

    @Test
    fun `when nothing could be sent, the detail says where`() {
        assertEquals(
            EngineCallState.Ended(
                status = null,
                byRemote = true,
                detail = "Could not send to sip:600@lab.local",
            ),
            map(Linphone.Error, code = 503, phrase = "io error", sendFailed = true),
        )
    }

    @Test
    fun `states that change nothing visible keep the call as it is`() {
        assertNull(map(Linphone.Idle))
        assertNull(map(Linphone.Updating))
    }
}
