/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.linphone

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.sip.api.MessageSendResult
import com.bobbyesp.sona.sip.api.SipStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.linphone.core.ChatMessage.State

class LinphoneMessageMapperTest {

    private val server = "lab.local:5060 (UDP)"

    private fun map(
        state: State,
        code: Int = 0,
        phrase: String? = null,
        sendFailed: Boolean = false,
    ) = messageResultOf(state, code, phrase, sendFailed, server)

    @Test
    fun `a message the server took is accepted, with its status when Linphone kept it`() {
        assertEquals(
            MessageSendResult.Accepted(SipStatus(202, "Accepted")),
            map(State.Delivered, code = 202, phrase = "Accepted"),
        )
        assertEquals(MessageSendResult.Accepted(null), map(State.Delivered))
    }

    @Test
    fun `states that need IMDN mean the same as delivered`() {
        assertEquals(MessageSendResult.Accepted(null), map(State.DeliveredToUser))
        assertEquals(MessageSendResult.Accepted(null), map(State.Displayed))
    }

    @Test
    fun `a refusal keeps the server's status`() {
        assertEquals(
            MessageSendResult.Failed(SipStatus(415, "Unsupported Media Type")),
            map(State.NotDelivered, code = 415, phrase = "Unsupported Media Type"),
        )
        assertEquals(
            MessageSendResult.Failed(SipStatus(404, "Error")),
            map(State.NotDelivered, code = 404, phrase = " "),
        )
    }

    @Test
    fun `Linphone's own io error is not the server's answer`() {
        assertEquals(
            MessageSendResult.Failed(status = null, detail = "Could not send to $server"),
            map(State.NotDelivered, code = 503, phrase = "io error", sendFailed = true),
        )
    }

    @Test
    fun `a failure without a status says the server did not answer`() {
        assertEquals(
            MessageSendResult.Failed(status = null, detail = "No answer from $server"),
            map(State.NotDelivered),
        )
    }

    @Test
    fun `states on the way are not an answer`() {
        listOf(State.Idle, State.InProgress, State.Queued, State.PendingDelivery).forEach {
            assertNull(it.name, map(it))
        }
    }

    private val lab = AccountId("lab")
    private val home = AccountId("home")
    private val identities = mapOf(lab to "102@lab.local", home to "201@home.local")

    @Test
    fun `a message goes to the account Linphone matched`() {
        assertEquals(home, accountOfMessage(home, identities, to = "102@lab.local"))
    }

    @Test
    fun `otherwise to the account it was addressed to, whatever the case`() {
        assertEquals(lab, accountOfMessage(null, identities, to = "102@LAB.local"))
    }

    @Test
    fun `with one account it is that one, with several and no match none is guessed`() {
        assertEquals(lab, accountOfMessage(null, mapOf(lab to "102@lab.local"), to = "9@x"))
        assertNull(accountOfMessage(null, identities, to = "9@x"))
        assertNull(accountOfMessage(AccountId("gone"), identities, to = null))
    }
}
