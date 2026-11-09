/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.linphone

import com.bobbyesp.sona.sip.api.RegistrationState
import com.bobbyesp.sona.sip.api.ServerEndpoint
import com.bobbyesp.sona.sip.api.SipStatus
import com.bobbyesp.sona.sip.api.Transport
import org.linphone.core.RegistrationState as LinphoneRegistration
import org.linphone.core.TransportType

/*
 * The only file that knows both vocabularies, Linphone's and Sona's. Everything here is a pure
 * function, tested on the JVM.
 */

/**
 * Sona's state for what Linphone reports about an account.
 *
 * @param code, [phrase] The server's answer to a failed registration; `0` when there was none.
 * @param sendFailed Linphone could not send at all (its local `503 io error`): no server answered,
 *   whatever the code says. On Android 17 this is also what a blocked local network looks like.
 * @param previous What the account was before: a refresh of a registration that holds keeps it
 *   *Registered*, rather than flickering to *Registering* every few minutes.
 * @param networkReachable Without a network a failure is *Offline*, not the server's fault.
 */
internal fun registrationOf(
    state: LinphoneRegistration,
    code: Int,
    phrase: String?,
    sendFailed: Boolean,
    previous: RegistrationState?,
    networkReachable: Boolean,
    registrar: ServerEndpoint,
): RegistrationState =
    when (state) {
        LinphoneRegistration.Ok -> RegistrationState.Registered
        LinphoneRegistration.Refreshing ->
            if (previous == RegistrationState.Registered) RegistrationState.Registered
            else RegistrationState.Registering
        LinphoneRegistration.Progress -> RegistrationState.Registering
        LinphoneRegistration.Failed ->
            when {
                !networkReachable -> RegistrationState.Offline
                sendFailed ->
                    RegistrationState.Failed(status = null, detail = "Could not send to $registrar")
                code > 0 ->
                    RegistrationState.Failed(
                        status = SipStatus(code, phrase?.takeIf { it.isNotBlank() } ?: "Error"),
                    )
                else ->
                    RegistrationState.Failed(status = null, detail = "No answer from $registrar")
            }
        LinphoneRegistration.None,
        LinphoneRegistration.Cleared -> RegistrationState.Unregistered
    }

internal fun Transport.toLinphone(): TransportType =
    when (this) {
        Transport.Udp -> TransportType.Udp
        Transport.Tcp -> TransportType.Tcp
        Transport.Tls -> TransportType.Tls
    }

/** `sip:host:port;transport=udp`, as Linphone takes a server address. */
internal fun ServerEndpoint.toSipUri(): String = buildString {
    append(if (transport == Transport.Tls) "sips:" else "sip:")
    append(host)
    if (port != null) append(':').append(port)
    append(";transport=").append(transport.name.lowercase())
}
