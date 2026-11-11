/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.api

/**
 * One SIP message as it went over the wire, for whoever reads SIP: the diagnostics' trace. The
 * engine removes what must not leave it before the message gets here (the digest of an
 * `Authorization` header, the keys of an SDP `crypto` line), so a trace can be copied and shared.
 *
 * @property id Grows with each message of an engine's run: the order they happened in.
 * @property atMillis When, in epoch milliseconds.
 * @property outgoing Sent by Sona; otherwise received.
 * @property transport, [peer] How and with whom: `UDP`, `192.168.1.10:5060`.
 * @property raw The whole message, headers and body, lines ended by `\n`.
 */
data class SipTraceEntry(
    val id: Long,
    val atMillis: Long,
    val outgoing: Boolean,
    val transport: String,
    val peer: String,
    val raw: String,
) {
    /** `REGISTER sip:lab.local SIP/2.0`, or `SIP/2.0 401 Unauthorized`. */
    val startLine: String
        get() = raw.lineSequence().firstOrNull().orEmpty()

    /** The request's method or the response's status, without the rest: `REGISTER`, `401`. */
    val summary: String
        get() =
            if (startLine.startsWith("SIP/2.0 ")) startLine.removePrefix("SIP/2.0 ")
            else startLine.substringBefore(' ')

    /** What ties a message to its call or registration, and to [EngineCall.id]. */
    val callId: String?
        get() = header("Call-ID") ?: header("i")

    /** `REGISTER` for a request and for the responses to it, from the `CSeq` header. */
    val method: String?
        get() = header("CSeq")?.substringAfterLast(' ')

    /** The first value of the header [name], whatever its case; `null` when it is not there. */
    fun header(name: String): String? =
        raw.lineSequence()
            .takeWhile { it.isNotBlank() }
            .drop(1)
            .firstOrNull { it.substringBefore(':').trim().equals(name, ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
}
