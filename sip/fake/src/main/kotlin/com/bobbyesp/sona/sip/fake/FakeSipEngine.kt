/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.fake

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.sip.api.AccountSpec
import com.bobbyesp.sona.sip.api.Capability
import com.bobbyesp.sona.sip.api.EngineConfig
import com.bobbyesp.sona.sip.api.EngineState
import com.bobbyesp.sona.sip.api.RegistrationState
import com.bobbyesp.sona.sip.api.SipEngine
import com.bobbyesp.sona.sip.api.SipStatus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * An engine in memory, scripted by the test or the demo that owns it. It behaves as a server that
 * accepts every account after [registrationDelay], unless told otherwise:
 * ```
 * fake.failRegistration(account, SipStatus(401, "Unauthorized"))
 * fake.goOffline()
 * ```
 *
 * Calls are added to it with the calling phase, so a UI test reads as the scenario it checks.
 *
 * @param scope Where registrations take their time; a test's scope, or the app's.
 */
class FakeSipEngine(
    private val scope: CoroutineScope,
    private val registrationDelay: Duration = 600.milliseconds,
) : SipEngine {

    private val _state = MutableStateFlow<EngineState>(EngineState.Stopped)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    override val capabilities: Set<Capability> =
        setOf(Capability.AudioCalls, Capability.Dtmf, Capability.Hold)

    private val _registrations = MutableStateFlow<Map<AccountId, RegistrationState>>(emptyMap())
    override val registrations: StateFlow<Map<AccountId, RegistrationState>> =
        _registrations.asStateFlow()

    /** The accounts as last given, for asserting what the app sent. */
    var accounts: List<AccountSpec> = emptyList()
        private set

    /** The configuration as last applied. */
    var config: EngineConfig? = null
        private set

    private val failures = mutableMapOf<AccountId, SipStatus?>()
    private val pending = mutableMapOf<AccountId, Job>()
    private var online = true

    override suspend fun start(config: EngineConfig) {
        this.config = config
        _state.value = EngineState.Running
        registerAll()
    }

    override suspend fun stop() {
        pending.values.forEach { it.cancel() }
        pending.clear()
        _state.value = EngineState.Stopped
        _registrations.update { current -> current.mapValues { RegistrationState.Unregistered } }
    }

    override suspend fun apply(config: EngineConfig) {
        this.config = config
    }

    override suspend fun setAccounts(accounts: List<AccountSpec>) {
        val kept = accounts.map { it.id }.toSet()
        (pending.keys - kept).forEach { pending.remove(it)?.cancel() }
        this.accounts = accounts
        _registrations.update { current -> current.filterKeys { it in kept } }
        registerAll()
    }

    override suspend fun refreshRegistrations() = registerAll()

    /** The server will answer [account]'s next registrations with [status]; `null`: no answer. */
    fun failRegistration(account: AccountId, status: SipStatus?) {
        failures[account] = status
        if (_state.value == EngineState.Running) register(account)
    }

    /** The server accepts [account] again. */
    fun acceptRegistration(account: AccountId) {
        failures.remove(account)
        if (_state.value == EngineState.Running) register(account)
    }

    /** The network is gone: every enabled account goes offline. */
    fun goOffline() {
        online = false
        pending.values.forEach { it.cancel() }
        pending.clear()
        _registrations.update { current ->
            current.mapValues { (id, state) ->
                if (accounts.firstOrNull { it.id == id }?.enabled == true) RegistrationState.Offline
                else state
            }
        }
    }

    /** The network is back, and the engine registers again, as a real one would. */
    fun goOnline() {
        online = true
        registerAll()
    }

    private fun registerAll() {
        if (_state.value != EngineState.Running) return
        accounts.forEach { register(it.id) }
    }

    private fun register(id: AccountId) {
        val account = accounts.firstOrNull { it.id == id } ?: return
        pending.remove(id)?.cancel()

        if (!account.enabled) {
            set(id, RegistrationState.Unregistered)
            return
        }
        if (!online) {
            set(id, RegistrationState.Offline)
            return
        }

        set(id, RegistrationState.Registering)
        pending[id] = scope.launch {
            delay(registrationDelay)
            val result =
                if (id in failures) {
                    val status = failures[id]
                    RegistrationState.Failed(
                        status = status,
                        detail =
                            if (status == null) "No answer from ${account.registrar}" else null,
                    )
                } else {
                    RegistrationState.Registered
                }
            set(id, result)
            pending.remove(id)
        }
    }

    private fun set(id: AccountId, state: RegistrationState) {
        _registrations.update { it + (id to state) }
    }
}
