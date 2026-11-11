/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.linphone

/*
 * The SIP trace, read from the log. Linphone has no callback for the messages it sends and
 * receives, but its SIP stack (Belle-SIP) writes each one to the log, whole, at the Message level:
 *
 *     channel [0x7b…]: message sent to [UDP://192.168.1.10:5060], size: [612] bytes
 *     REGISTER sip:192.168.1.10 SIP/2.0
 *     …
 *     channel [0x7b…]: received [498] new bytes from [UDP://192.168.1.10:5060]:
 *     SIP/2.0 401 Unauthorized
 *     …
 *
 * (The two formats are in liblinphone 5.5's binary as such.) Everything here is a pure function,
 * tested on the JVM; the engine only feeds it log lines.
 */

/** A SIP message found in a log line, already without its secrets. */
internal data class TracedMessage(
    val outgoing: Boolean,
    val transport: String,
    val peer: String,
    val raw: String,
)

private val Sent =
    Regex(
        """^channel \[[^\]]*]: message sent to \[(\w+)://(.*):(\d+)], size: \[\d+] bytes\r?\n(.*)$""",
        RegexOption.DOT_MATCHES_ALL,
    )

private val Received =
    Regex(
        """^channel \[[^\]]*]: received \[\d+] new bytes from \[(\w+)://(.*):(\d+)]:\r?\n(.*)$""",
        RegexOption.DOT_MATCHES_ALL,
    )

private val StartLine = Regex("""^(SIP/2\.0 \d{3}\b.*|[A-Z]+ \S+ SIP/2\.0)$""")

/**
 * The SIP message [logLine] reports, or `null` for any other line: the rest of the log, a
 * keep-alive (bytes that are no message), a message that was not sent. What is returned went
 * through [redactSecrets].
 */
internal fun tracedMessageOf(logLine: String): TracedMessage? {
    // Nearly every log line stops here.
    if (!logLine.startsWith("channel [")) return null
    val sent = Sent.find(logLine)
    val match = sent ?: Received.find(logLine) ?: return null
    val (transport, host, port, body) = match.destructured
    val raw = body.replace("\r\n", "\n").trim().removeSurrounding("[", "]").trim()
    if (!StartLine.matches(raw.lineSequence().firstOrNull().orEmpty())) return null
    return TracedMessage(
        outgoing = sent != null,
        transport = transport.uppercase(),
        peer = "$host:$port",
        raw = redactSecrets(raw),
    )
}

/** What of a digest says who is asking and of whom, and nothing a password could be found from. */
private val HarmlessDigestParts = setOf("username", "realm", "uri", "algorithm", "qop")

private val AuthorizationHeader =
    Regex("""^(Authorization|Proxy-Authorization)(\s*:\s*)(\S+)\s*(.*)$""", RegexOption.IGNORE_CASE)

private val CryptoLine = Regex("""^(a=crypto:\S+ \S+ )inline:\S+(.*)$""")

/**
 * [message] without what must never leave the engine:
 * - an `Authorization` or `Proxy-Authorization` header keeps its scheme and who it is for
 *   (`username`, `realm`, `uri`), which is what a failed registration is debugged with, and loses
 *   the response, the nonces and anything else: with them a password can be searched for offline;
 * - an SDP `a=crypto` line loses its key, which decrypts the call's audio.
 */
internal fun redactSecrets(message: String): String =
    message.lineSequence().joinToString("\n") { line ->
        AuthorizationHeader.matchEntire(line)?.let { match ->
            val (name, separator, scheme, parameters) = match.destructured
            val kept =
                splitOutsideQuotes(parameters).filter { part ->
                    part.substringBefore('=').trim().lowercase() in HarmlessDigestParts
                }
            return@joinToString name +
                separator +
                scheme +
                " " +
                (kept + "response=[redacted]").joinToString(", ")
        }
        CryptoLine.matchEntire(line)?.let { match ->
            return@joinToString match.groupValues[1] + "inline:[redacted]" + match.groupValues[2]
        }
        line
    }

/** The comma separated parts of [text], a comma inside quotes not being a separator. */
private fun splitOutsideQuotes(text: String): List<String> {
    val parts = mutableListOf<String>()
    val current = StringBuilder()
    var quoted = false
    for (char in text) {
        when {
            char == '"' -> {
                quoted = !quoted
                current.append(char)
            }
            char == ',' && !quoted -> {
                parts += current.toString().trim()
                current.clear()
            }
            else -> current.append(char)
        }
    }
    parts += current.toString().trim()
    return parts.filter { it.isNotEmpty() }
}
