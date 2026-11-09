/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.model

/**
 * A SIP address: `sip:102@lab.local`, `sips:alice@example.org:5061`.
 *
 * Never an `android.net.Uri`, so everything that reads or builds an address is tested on the JVM.
 * Turning what the user typed ("102", "+34 600…") into one is the job of the calling domain's
 * address resolver, not of this class: [parse] only accepts what already is an address.
 */
data class SipAddress(
    val user: String?,
    val host: String,
    val port: Int? = null,
    val secure: Boolean = false,
) {
    init {
        require(host.isNotBlank()) { "A SIP address needs a host" }
        require(port == null || port in 1..65535) { "Port $port is out of range" }
    }

    /** `sip:` or `sips:` with the user, host and port, as it goes on the wire. */
    val uri: String
        get() = buildString {
            append(if (secure) "sips:" else "sip:")
            if (user != null) append(user).append('@')
            append(host)
            if (port != null) append(':').append(port)
        }

    override fun toString(): String = uri

    companion object {
        /**
         * The address in [text], or `null` when it is not one. Parameters and headers
         * (`;transport=`, `?subject=`) are dropped: Sona keeps the transport with the account, not
         * the address.
         */
        fun parse(text: String): SipAddress? {
            val trimmed = text.trim()
            val secure =
                when {
                    trimmed.startsWith("sips:", ignoreCase = true) -> true
                    trimmed.startsWith("sip:", ignoreCase = true) -> false
                    else -> return null
                }
            val rest = trimmed.substringAfter(':').substringBefore(';').substringBefore('?')
            val user = rest.substringBeforeLast('@', missingDelimiterValue = "").ifEmpty { null }
            val hostPort = rest.substringAfterLast('@')
            if (hostPort.isEmpty() || hostPort.any { it.isWhitespace() }) return null

            // An IPv6 host is bracketed, so its colons are not the port's.
            val (host, portText) =
                if (hostPort.startsWith("[")) {
                    val end = hostPort.indexOf(']').takeIf { it > 0 } ?: return null
                    hostPort.substring(0, end + 1) to
                        hostPort.substring(end + 1).removePrefix(":").ifEmpty { null }
                } else {
                    hostPort.substringBefore(':') to
                        hostPort.substringAfter(':', missingDelimiterValue = "").ifEmpty { null }
                }
            val port = portText?.let {
                it.toIntOrNull()?.takeIf { p -> p in 1..65535 } ?: return null
            }
            if (host.isBlank()) return null

            return SipAddress(user = user, host = host, port = port, secure = secure)
        }
    }
}
