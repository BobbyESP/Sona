/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.api

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.Secret
import com.bobbyesp.sona.core.model.SipAddress
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

enum class Transport {
    Udp,
    Tcp,
    Tls,
}

/** Where an account registers: the registrar, or the outbound proxy in front of it. */
data class ServerEndpoint(val host: String, val port: Int? = null, val transport: Transport) {
    /** As people read it: `192.168.1.10:5060 (UDP)`. */
    override fun toString(): String = buildString {
        append(host)
        if (port != null) append(':').append(port)
        append(" (").append(transport.name.uppercase()).append(')')
    }
}

/**
 * An account as the engine needs it, credentials included. Built from storage and the vault by the
 * calling domain each time the accounts change; never stored as such.
 *
 * @property identity The account's address of record: `sip:102@lab.local`.
 * @property authUsername The username to authenticate with, when it is not the identity's user.
 */
data class AccountSpec(
    val id: AccountId,
    val label: String,
    val identity: SipAddress,
    val password: Secret,
    val registrar: ServerEndpoint,
    val displayName: String? = null,
    val authUsername: String? = null,
    val outboundProxy: ServerEndpoint? = null,
    val expiry: Duration = 600.seconds,
    val enabled: Boolean = true,
)

/** A SIP status line, kept as the server sent it: the domain decides what it means. */
data class SipStatus(val code: Int, val phrase: String) {
    override fun toString(): String = "$code $phrase"
}

/** Whether an account can make and receive calls. */
sealed interface RegistrationState {
    /** Disabled, or not yet asked to register. */
    data object Unregistered : RegistrationState

    data object Registering : RegistrationState

    data object Registered : RegistrationState

    /** There is no network: the engine registers again when it comes back. */
    data object Offline : RegistrationState

    /**
     * The registration failed. [status] is the server's answer when there was one (`401`, `403`,
     * `404`); without one the server did not answer, and [detail] says what was tried.
     */
    data class Failed(val status: SipStatus?, val detail: String? = null) : RegistrationState
}
