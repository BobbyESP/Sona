/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.api

import com.bobbyesp.sona.core.model.AccountId
import kotlinx.coroutines.flow.StateFlow

/**
 * The SIP engine: registrations today, calls from phase 2 (`dial`, `calls`, `controls` in
 * docs/calling.md). Plain Kotlin, no Linphone type, no Android type: the app binds an
 * implementation (Linphone, or the fake one in the demo flavor) and nothing else knows which.
 *
 * Every function may be called from any thread; the implementation confines its engine to its own.
 */
interface SipEngine {
    val state: StateFlow<EngineState>

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
}

/** What applies to every account. Grows with the settings that reach the engine. */
data class EngineConfig(
    val userAgent: String = "Sona",
    /** The engine's own log, every SIP message included, mirrored to the platform's log. */
    val verboseLogs: Boolean = false,
)
