/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.accounts.domain

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.sip.api.ServerEndpoint
import com.bobbyesp.sona.sip.api.Transport
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountDraftTest {

    private fun AccountDraft.check() = validate(AccountId("a"), color = 0)

    private fun AccountDraft.account() = (check() as DraftValidation.Valid).account

    private fun AccountDraft.problems() = (check() as DraftValidation.Invalid).problems

    @Test
    fun `server and extension are required`() {
        assertEquals(
            mapOf(
                DraftField.Server to DraftProblem.Required,
                DraftField.Username to DraftProblem.Required,
            ),
            AccountDraft().problems(),
        )
    }

    @Test
    fun `a server typed as a SIP URI with its port is understood`() {
        val account = AccountDraft(server = " sip:192.168.1.10:5070 ", username = "102").account()

        assertEquals(ServerEndpoint("192.168.1.10", 5070, Transport.Udp), account.server)
        assertEquals("192.168.1.10", account.domain)
    }

    @Test
    fun `TLS without a port uses 5061`() {
        val account =
            AccountDraft(server = "lab.local", username = "102", transport = Transport.Tls)
                .account()

        assertEquals(5061, account.server.port)
    }

    @Test
    fun `a bracketed IPv6 server keeps its brackets`() {
        val account = AccountDraft(server = "[2001:db8::1]:5060", username = "102").account()

        assertEquals("[2001:db8::1]", account.server.host)
        assertEquals(5060, account.server.port)
    }

    @Test
    fun `what cannot be a host or a username is refused`() {
        val problems = AccountDraft(server = "lab local", username = "10 2").problems()

        assertEquals(DraftProblem.NotAHost, problems[DraftField.Server])
        assertEquals(DraftProblem.NotAUsername, problems[DraftField.Username])
    }

    @Test
    fun `ports and expiry outside their range are refused`() {
        val problems =
            AccountDraft(
                    server = "lab.local",
                    username = "102",
                    port = "70000",
                    expirySeconds = "5",
                )
                .problems()

        assertEquals(DraftProblem.OutOfRange, problems[DraftField.Port])
        assertEquals(DraftProblem.OutOfRange, problems[DraftField.Expiry])
    }

    @Test
    fun `a separate domain and auth username are kept`() {
        val account =
            AccountDraft(
                    server = "10.0.0.5",
                    username = "102",
                    domain = "lab.local",
                    authUsername = "auth102",
                )
                .account()

        assertEquals("sip:102@lab.local", account.identity.uri)
        assertEquals("auth102", account.authUsername)
    }

    @Test
    fun `an account edited without changes is the same draft`() {
        val account =
            AccountDraft(server = "lab.local", username = "102", label = "Lab", port = "5070")
                .account()

        assertEquals(
            account,
            AccountDraft.of(account).validate(account.id, account.color).let {
                (it as DraftValidation.Valid).account
            },
        )
    }
}
