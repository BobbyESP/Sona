/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SipAddressTest {

    @Test
    fun `an extension on a host`() {
        assertEquals(SipAddress("102", "lab.local"), SipAddress.parse("sip:102@lab.local"))
    }

    @Test
    fun `sips, a port, and surrounding spaces`() {
        assertEquals(
            SipAddress("alice", "example.org", 5061, secure = true),
            SipAddress.parse("  sips:alice@example.org:5061 "),
        )
    }

    @Test
    fun `parameters and headers are dropped`() {
        assertEquals(
            SipAddress("102", "10.0.0.5", 5060),
            SipAddress.parse("sip:102@10.0.0.5:5060;transport=tcp?subject=hi"),
        )
    }

    @Test
    fun `a server without a user`() {
        assertEquals(SipAddress(null, "lab.local"), SipAddress.parse("sip:lab.local"))
    }

    @Test
    fun `an IPv6 host keeps its brackets and its port`() {
        assertEquals(
            SipAddress("102", "[2001:db8::1]", 5060),
            SipAddress.parse("sip:102@[2001:db8::1]:5060"),
        )
    }

    @Test
    fun `what is not an address is refused`() {
        assertNull(SipAddress.parse("102"))
        assertNull(SipAddress.parse("tel:+34600112233"))
        assertNull(SipAddress.parse("sip:"))
        assertNull(SipAddress.parse("sip:102@"))
        assertNull(SipAddress.parse("sip:102@lab.local:99999"))
        assertNull(SipAddress.parse("sip:102@lab local"))
    }

    @Test
    fun `the uri is what goes on the wire`() {
        assertEquals(
            "sips:alice@example.org:5061",
            SipAddress("alice", "example.org", 5061, true).uri,
        )
    }

    @Test
    fun `a secret never prints itself`() {
        assertEquals("Secret(••••)", Secret("hunter2").toString())
        assertEquals("hunter2", Secret("hunter2").reveal())
    }
}
