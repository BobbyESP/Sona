/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.linphone

import com.bobbyesp.sona.sip.api.RegistrationState
import com.bobbyesp.sona.sip.api.ServerEndpoint
import com.bobbyesp.sona.sip.api.SipStatus
import com.bobbyesp.sona.sip.api.Transport
import org.junit.Assert.assertEquals
import org.junit.Test
import org.linphone.core.RegistrationState as Linphone

class LinphoneMapperTest {

    private val lab = ServerEndpoint("lab.local", 5060, Transport.Udp)

    private fun map(
        state: Linphone,
        code: Int = 0,
        phrase: String? = null,
        sendFailed: Boolean = false,
        previous: RegistrationState? = null,
        network: Boolean = true,
    ) = registrationOf(state, code, phrase, sendFailed, previous, network, lab)

    @Test
    fun `ok is registered, progress is registering`() {
        assertEquals(RegistrationState.Registered, map(Linphone.Ok))
        assertEquals(RegistrationState.Registering, map(Linphone.Progress))
    }

    @Test
    fun `a refresh of a registration that holds stays registered`() {
        assertEquals(
            RegistrationState.Registered,
            map(Linphone.Refreshing, previous = RegistrationState.Registered),
        )
        assertEquals(RegistrationState.Registering, map(Linphone.Refreshing, previous = null))
    }

    @Test
    fun `a failure keeps the server's status`() {
        assertEquals(
            RegistrationState.Failed(SipStatus(401, "Unauthorized")),
            map(Linphone.Failed, code = 401, phrase = "Unauthorized"),
        )
    }

    @Test
    fun `a failure without an answer says which server did not answer`() {
        assertEquals(
            RegistrationState.Failed(status = null, detail = "No answer from lab.local:5060 (UDP)"),
            map(Linphone.Failed),
        )
    }

    @Test
    fun `Linphone's own io error is not the server's answer`() {
        assertEquals(
            RegistrationState.Failed(
                status = null,
                detail = "Could not send to lab.local:5060 (UDP)",
            ),
            map(Linphone.Failed, code = 503, phrase = "io error", sendFailed = true),
        )
    }

    @Test
    fun `a failure without a network is offline`() {
        assertEquals(RegistrationState.Offline, map(Linphone.Failed, code = 408, network = false))
    }

    @Test
    fun `cleared and none are unregistered`() {
        assertEquals(RegistrationState.Unregistered, map(Linphone.Cleared))
        assertEquals(RegistrationState.Unregistered, map(Linphone.None))
    }

    @Test
    fun `server addresses carry their transport`() {
        assertEquals("sip:lab.local:5060;transport=udp", lab.toSipUri())
        assertEquals(
            "sips:lab.local:5061;transport=tls",
            ServerEndpoint("lab.local", 5061, Transport.Tls).toSipUri(),
        )
    }
}
