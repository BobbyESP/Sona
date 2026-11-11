/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.linphone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinphoneTraceTest {

    private val register =
        listOf(
                "REGISTER sip:192.168.1.10 SIP/2.0",
                "Via: SIP/2.0/UDP 192.168.1.23:41234;branch=z9hG4bK.abc;rport",
                "From: <sip:102@192.168.1.10>;tag=xyz",
                "To: sip:102@192.168.1.10",
                "CSeq: 21 REGISTER",
                "Call-ID: Nq3~callid",
                "Authorization: Digest realm=\"asterisk\", nonce=\"1699/abc,def\", " +
                    "algorithm=MD5, username=\"102\", uri=\"sip:192.168.1.10\", " +
                    "response=\"0123456789abcdef0123456789abcdef\", cnonce=\"zz\", nc=00000001, " +
                    "qop=auth",
                "Content-Length: 0",
                "",
                "",
            )
            .joinToString("\r\n")

    @Test
    fun `a sent message is read with where it went`() {
        val line =
            "channel [0x7b40001234]: message sent to [UDP://192.168.1.10:5060], " +
                "size: [612] bytes\n$register"

        val traced = tracedMessageOf(line)!!

        assertTrue(traced.outgoing)
        assertEquals("UDP", traced.transport)
        assertEquals("192.168.1.10:5060", traced.peer)
        assertEquals("REGISTER sip:192.168.1.10 SIP/2.0", traced.raw.lineSequence().first())
        assertFalse("\r" in traced.raw)
    }

    @Test
    fun `a received message is read with where it came from, IPv6 too`() {
        val line =
            "channel [0x7b40001234]: received [498] new bytes from [tcp://[fe80::1]:5060]:\n" +
                "SIP/2.0 401 Unauthorized\r\nCSeq: 20 REGISTER\r\n\r\n"

        val traced = tracedMessageOf(line)!!

        assertFalse(traced.outgoing)
        assertEquals("TCP", traced.transport)
        assertEquals("[fe80::1]:5060", traced.peer)
        assertEquals("SIP/2.0 401 Unauthorized\nCSeq: 20 REGISTER", traced.raw)
    }

    @Test
    fun `the rest of the log, keep-alives and messages not sent are no messages`() {
        assertNull(tracedMessageOf("Refreshing registration for sip:102@192.168.1.10"))
        assertNull(tracedMessageOf("channel [0x7b40001234] [498] bytes parsed"))
        assertNull(
            tracedMessageOf(
                "channel [0x1]: received [4] new bytes from [UDP://192.168.1.10:5060]:\n\r\n\r\n",
            ),
        )
        assertNull(
            tracedMessageOf(
                "channel [0x1]: message silently discarded to [UDP://192.168.1.10:5060], " +
                    "size: [612] bytes\n$register",
            ),
        )
    }

    @Test
    fun `an authorization keeps who it is for and loses what a password could be found from`() {
        val traced =
            tracedMessageOf(
                "channel [0x1]: message sent to [UDP://192.168.1.10:5060], size: [612] bytes\n" +
                    register,
            )!!

        assertEquals(
            "Authorization: Digest realm=\"asterisk\", algorithm=MD5, username=\"102\", " +
                "uri=\"sip:192.168.1.10\", qop=auth, response=[redacted]",
            traced.raw.lineSequence().first { it.startsWith("Authorization") },
        )
        assertFalse("0123456789abcdef" in traced.raw)
        assertFalse("1699/abc" in traced.raw)
        assertFalse("cnonce" in traced.raw)
    }

    @Test
    fun `a proxy authorization in any case is redacted, a challenge is not`() {
        val message =
            "INVITE sip:600@lab.local SIP/2.0\n" +
                "proxy-authorization: Digest username=\"102\", response=\"feedfacefeedface\"\n" +
                "WWW-Authenticate: Digest realm=\"asterisk\", nonce=\"abc\""

        val redacted = redactSecrets(message)

        assertFalse("feedface" in redacted)
        assertTrue("proxy-authorization: Digest username=\"102\", response=[redacted]" in redacted)
        assertTrue("WWW-Authenticate: Digest realm=\"asterisk\", nonce=\"abc\"" in redacted)
    }

    @Test
    fun `an SRTP key in the SDP is redacted`() {
        val sdp =
            "SIP/2.0 200 OK\n\nv=0\n" +
                "a=crypto:1 AES_CM_128_HMAC_SHA1_80 inline:WVNfX19zZW1jdGwgKCkgewkyMjA7fQp9CnVubGVz|2^20\n" +
                "a=rtpmap:0 PCMU/8000"

        val redacted = redactSecrets(sdp)

        assertFalse("WVNfX19zZW1jdGwg" in redacted)
        assertTrue("a=crypto:1 AES_CM_128_HMAC_SHA1_80 inline:[redacted]" in redacted)
        assertTrue("a=rtpmap:0 PCMU/8000" in redacted)
    }
}
