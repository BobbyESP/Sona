/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.api

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.CallId
import com.bobbyesp.sona.core.model.SipAddress
import kotlinx.coroutines.flow.StateFlow

/**
 * The SIP engine: registrations and calls. Plain Kotlin, no Linphone type, no Android type: the app
 * binds an implementation (Linphone, or the fake one in the demo flavor) and nothing else knows
 * which.
 *
 * Every function may be called from any thread; the implementation confines its engine to its own.
 */
interface SipEngine {
    val state: StateFlow<EngineState>

    /**
     * What this engine is, for *About* and a bug report: `liblinphone 5.5.39`. An engine that only
     * knows its version once started says its name alone before.
     */
    val version: String

    /** What this engine can do at all. A feature hides what is not here. */
    val capabilities: Set<Capability>

    /** Every account the engine was given, by id. An account not given is not in the map. */
    val registrations: StateFlow<Map<AccountId, RegistrationState>>

    suspend fun start(config: EngineConfig)

    suspend fun stop()

    /** Options that apply to every account. Some take effect on the next registration or call. */
    suspend fun apply(config: EngineConfig)

    /**
     * The whole set of accounts the engine must have, with their credentials. The engine adds,
     * updates and removes until it has exactly these: it keeps no account of its own, so the app's
     * storage is the only source of truth.
     */
    suspend fun setAccounts(accounts: List<AccountSpec>)

    /** Registers again every enabled account, after the network changed. */
    suspend fun refreshRegistrations()

    /** Every call, the ended ones included until [forget]. */
    val calls: StateFlow<List<EngineCall>>

    /**
     * Calls [to] from [account]. Only the calling coordinator calls this, and the functions below.
     */
    suspend fun dial(account: AccountId, to: SipAddress): DialResult

    suspend fun answer(call: CallId)

    /** Declines an incoming call: `486 Busy Here` when [busy], `603 Decline` otherwise. */
    suspend fun decline(call: CallId, busy: Boolean = false)

    suspend fun hangUp(call: CallId)

    suspend fun hold(call: CallId, onHold: Boolean)

    suspend fun setMuted(call: CallId, muted: Boolean)

    /** Sends a DTMF digit (`0`–`9`, `*`, `#`) on an active call. */
    suspend fun sendDtmf(call: CallId, digit: Char)

    /**
     * Plays and captures [call]'s audio on [device], the kind the system routed the call to. It is
     * remembered for the call: streams that start later (the answer, a resume) open on it too. An
     * engine that has no such device keeps the one it is on.
     */
    suspend fun setAudioDevice(call: CallId, device: AudioDeviceKind)

    /** Drops an ended call from [calls], once its ending was taken into account. */
    suspend fun forget(call: CallId)

    /**
     * The SIP messages sent and received while [EngineConfig.captureTrace] is on, oldest first,
     * without their secrets. Only the last few hundred are kept, in memory, for this run. Always
     * empty on an engine without [Capability.SipTrace].
     */
    val trace: StateFlow<List<SipTraceEntry>>

    suspend fun clearTrace()
}

/** Whether the engine is running. */
sealed interface EngineState {
    data object Stopped : EngineState

    data object Starting : EngineState

    data object Running : EngineState

    /** The engine could not start; [detail] is for the diagnostics, not the user's sentence. */
    data class Failed(val detail: String) : EngineState
}

/** What an engine can do. See docs/architecture.md, Capabilities. */
enum class Capability {
    AudioCalls,
    Dtmf,
    Hold,
    BlindTransfer,
    AttendedTransfer,
    Conference,
    Messaging,
    Presence,
    Mwi,
    Srtp,
    Zrtp,
    CallStats,
    SipTrace,
}

/**
 * What applies to every account, built from the user's settings (`EngineConfigBuilder`). A change
 * given to [SipEngine.apply] takes effect at once, or on the next call for what a call negotiates.
 */
data class EngineConfig(
    val userAgent: String = "Sona",
    /** How much of the engine's own log reaches the platform's. */
    val logLevel: EngineLogLevel = EngineLogLevel.Warnings,
    /**
     * The audio codecs to offer, by their SDP names (`opus`, `G722`, `PCMU`), first choice first;
     * every other codec is off. `null` leaves the engine's own choice.
     */
    val codecs: List<String>? = null,
    val dtmf: DtmfMode = DtmfMode.Rfc4733,
    val echoCancellation: Boolean = true,
    /** Register and call only over Wi-Fi (or Ethernet): mobile data counts as no network. */
    val wifiOnly: Boolean = false,
    /** Keep the SIP messages in [SipEngine.trace] as they go by. */
    val captureTrace: Boolean = false,
)

enum class EngineLogLevel {
    Errors,
    Warnings,

    /** Every SIP message included. */
    Messages,
    Debug,
}

/** How DTMF digits are sent. */
enum class DtmfMode {
    /** RTP events (RFC 4733). */
    Rfc4733,

    /** SIP `INFO` requests. */
    SipInfo,

    /** Tones in the audio. */
    InBand,
}
