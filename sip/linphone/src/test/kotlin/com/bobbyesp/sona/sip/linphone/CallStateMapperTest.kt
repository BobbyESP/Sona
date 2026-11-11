/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.linphone

import com.bobbyesp.sona.sip.api.AudioDeviceKind
import com.bobbyesp.sona.sip.api.EngineCallState
import com.bobbyesp.sona.sip.api.SipStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.linphone.core.AudioDevice
import org.linphone.core.Call.State as Linphone

class CallStateMapperTest {

    private fun map(
        state: Linphone,
        code: Int = 0,
        phrase: String? = null,
        protocol: String? = "SIP",
        sendFailed: Boolean = false,
        endedHere: Boolean = false,
    ) =
        callStateOf(
            state,
            code,
            phrase,
            protocol,
            sendFailed,
            endedHere,
            remote = "sip:600@lab.local",
        )

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

    /**
     * Found on a phone: Asterisk ends a call with `Reason: Q.850;cause=0` when the hang-up was
     * asked for on its console, and Linphone reports that cause as the code 400.
     */
    @Test
    fun `a hang-up's Q850 reason is not a SIP status`() {
        assertEquals(
            EngineCallState.Ended(status = null, byRemote = true),
            map(Linphone.End, code = 400, phrase = "", protocol = "Q.850"),
        )
        assertEquals(
            EngineCallState.Ended(status = null, byRemote = true),
            map(Linphone.End, code = 16, protocol = "Q.850"),
        )
    }

    @Test
    fun `a status with no protocol named is still SIP's`() {
        assertEquals(
            EngineCallState.Ended(SipStatus(603, "Decline"), byRemote = true),
            map(Linphone.End, code = 603, phrase = "Decline", protocol = null),
        )
    }

    @Test
    fun `a kind of device plays on its own type and records on the phone when it has no microphone`() {
        val phone =
            listOf(
                "mic" to AudioDevice.Type.Microphone,
                "earpiece" to AudioDevice.Type.Earpiece,
                "speaker" to AudioDevice.Type.Speaker,
                "headphones" to AudioDevice.Type.Headphones,
                "music" to AudioDevice.Type.BluetoothA2DP,
            )
        fun play(kind: AudioDeviceKind) = pickDevice(phone, kind.outputTypes()) { it.second }?.first
        fun record(kind: AudioDeviceKind) =
            pickDevice(phone, kind.inputTypes()) { it.second }?.first

        assertEquals("speaker", play(AudioDeviceKind.Speaker))
        assertEquals("mic", record(AudioDeviceKind.Speaker))
        assertEquals("headphones", play(AudioDeviceKind.WiredHeadset))
        assertEquals("mic", record(AudioDeviceKind.WiredHeadset))
        // A2DP carries music, not calls: with no call device, the call stays where it is.
        assertNull(play(AudioDeviceKind.Bluetooth))
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
