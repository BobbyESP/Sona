/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.linphone

import com.bobbyesp.sona.sip.api.AudioDeviceKind
import com.bobbyesp.sona.sip.api.EngineCallState
import com.bobbyesp.sona.sip.api.RegistrationState
import com.bobbyesp.sona.sip.api.ServerEndpoint
import com.bobbyesp.sona.sip.api.SipStatus
import com.bobbyesp.sona.sip.api.Transport
import org.linphone.core.AudioDevice
import org.linphone.core.Call.State as CallState
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

/**
 * Sona's state of a call for Linphone's, or `null` for states that change nothing Sona shows
 * (`Idle`, `Referred`, the re-INVITE states while media keeps flowing): the call keeps its state.
 *
 * @param code, [phrase] The SIP status a failed call ended with; `0` when none.
 * @param protocol What [code] is a code of. Linphone also reports the `Reason` header of a `BYE` or
 *   a `CANCEL` here (`Q.850;cause=16`, from Asterisk on every hang-up), and gives a cause of `0`
 *   the code `400`: only a code of SIP's own is a status. Found on a phone: a hang-up asked for on
 *   the server's console read as *Call failed · 400 Error*.
 * @param endedHere Whether this side ended it (hung up, declined), as the engine knows.
 */
internal fun callStateOf(
    state: CallState,
    code: Int,
    phrase: String?,
    protocol: String?,
    sendFailed: Boolean,
    endedHere: Boolean,
    remote: String,
): EngineCallState? {
    val isSipStatus = protocol.isNullOrBlank() || protocol.equals(SIP, ignoreCase = true)
    return when (state) {
        CallState.OutgoingInit,
        CallState.OutgoingProgress -> EngineCallState.Dialing
        CallState.OutgoingRinging,
        CallState.OutgoingEarlyMedia -> EngineCallState.Ringing
        CallState.IncomingReceived,
        CallState.PushIncomingReceived,
        CallState.IncomingEarlyMedia -> EngineCallState.Incoming
        CallState.Connected,
        CallState.StreamsRunning -> EngineCallState.Active
        CallState.Pausing,
        CallState.Paused -> EngineCallState.LocalHold
        CallState.PausedByRemote -> EngineCallState.RemoteHold
        CallState.Error,
        CallState.End,
        CallState.Released ->
            EngineCallState.Ended(
                status =
                    if (!sendFailed && isSipStatus && code >= 300 && code != 487)
                        SipStatus(code, phrase?.takeIf { it.isNotBlank() } ?: "Error")
                    else null,
                byRemote = !endedHere,
                detail = if (sendFailed) "Could not send to $remote" else null,
            )
        else -> null
    }
}

private const val SIP = "SIP"

/**
 * Linphone's types of device a call can play on for a kind of device, the preferred first. A2DP is
 * not among Bluetooth's: it carries music, not calls.
 */
internal fun AudioDeviceKind.outputTypes(): List<AudioDevice.Type> =
    when (this) {
        AudioDeviceKind.Earpiece -> listOf(AudioDevice.Type.Earpiece)
        AudioDeviceKind.Speaker -> listOf(AudioDevice.Type.Speaker)
        AudioDeviceKind.WiredHeadset ->
            listOf(
                AudioDevice.Type.Headset,
                AudioDevice.Type.Headphones,
                AudioDevice.Type.GenericUsb,
            )
        AudioDeviceKind.Bluetooth -> listOf(AudioDevice.Type.Bluetooth, AudioDevice.Type.HearingAid)
    }

/**
 * The types it can capture from, the preferred first: the device's own microphone when it has one,
 * the phone's otherwise (headphones have none, and the speaker and the earpiece never do).
 */
internal fun AudioDeviceKind.inputTypes(): List<AudioDevice.Type> =
    when (this) {
        AudioDeviceKind.Earpiece,
        AudioDeviceKind.Speaker -> listOf(AudioDevice.Type.Microphone)
        AudioDeviceKind.WiredHeadset ->
            listOf(
                AudioDevice.Type.Headset,
                AudioDevice.Type.GenericUsb,
                AudioDevice.Type.Microphone,
            )
        AudioDeviceKind.Bluetooth ->
            listOf(
                AudioDevice.Type.Bluetooth,
                AudioDevice.Type.HearingAid,
                AudioDevice.Type.Microphone,
            )
    }

/** The first of [devices] of the most preferred of [wanted], or `null` when it has none of them. */
internal fun <T> pickDevice(
    devices: List<T>,
    wanted: List<AudioDevice.Type>,
    typeOf: (T) -> AudioDevice.Type,
): T? = wanted.firstNotNullOfOrNull { type -> devices.firstOrNull { typeOf(it) == type } }

/**
 * The engine's codecs as the user wants them: each of [available] with whether it is on, in the
 * order to offer them. The [wanted] names come first, in their order (a name the engine has at
 * several clock rates, as Speex, keeps the engine's order among them); every other codec follows,
 * turned off. Names are compared as SDP does, without case; a wanted name the engine does not have
 * is skipped.
 */
internal fun <T> codecPlan(
    available: List<T>,
    wanted: List<String>,
    mime: (T) -> String,
): List<Pair<T, Boolean>> {
    // By position, so two payloads that look alike stay two, and a name wanted twice counts once.
    val on =
        wanted
            .flatMap { name ->
                available.indices.filter { mime(available[it]).equals(name, ignoreCase = true) }
            }
            .distinct()
    val off = available.indices - on.toSet()
    return on.map { available[it] to true } + off.map { available[it] to false }
}
