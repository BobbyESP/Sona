/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.fake

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.CallId
import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.sip.api.AccountSpec
import com.bobbyesp.sona.sip.api.CallDirection
import com.bobbyesp.sona.sip.api.Capability
import com.bobbyesp.sona.sip.api.DialResult
import com.bobbyesp.sona.sip.api.EngineCall
import com.bobbyesp.sona.sip.api.EngineCallState
import com.bobbyesp.sona.sip.api.EngineConfig
import com.bobbyesp.sona.sip.api.EngineState
import com.bobbyesp.sona.sip.api.MediaInfo
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
 * Calls are scripted the same way (`fake.incomingCall(from)`, `fake.remoteAnswers(call)`,
 * `fake.remoteEnds(call, SipStatus(486, "Busy Here"))`), so a test reads as the scenario it checks.
 *
 * @param scope Where registrations and scripted calls take their time; a test's scope, or the
 *   app's.
 * @param demoCalls Whether a dialed call plays out by itself, for the demo flavor: it rings, then
 *   is answered, except `486` (busy), `603` (declined) and `404` (no such extension).
 */
class FakeSipEngine(
    private val scope: CoroutineScope,
    private val registrationDelay: Duration = 600.milliseconds,
    private val demoCalls: Boolean = false,
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

    private val _calls = MutableStateFlow<List<EngineCall>>(emptyList())
    override val calls: StateFlow<List<EngineCall>> = _calls.asStateFlow()

    /** Every DTMF digit sent, in order, for asserting. */
    val dtmfSent: MutableList<Pair<CallId, Char>> = mutableListOf()

    private var nextCall = 0

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

    override suspend fun dial(account: AccountId, to: SipAddress): DialResult {
        if (_registrations.value[account] != RegistrationState.Registered) {
            return DialResult.AccountUnavailable
        }
        val id = newCallId()
        put(EngineCall(id, account, CallDirection.Outgoing, to, state = EngineCallState.Dialing))
        if (demoCalls) scope.launch { playDemoCall(id, to) }
        return DialResult.Started(id)
    }

    override suspend fun answer(call: CallId) =
        update(call) {
            if (state == EngineCallState.Incoming) copy(state = EngineCallState.Active) else this
        }

    override suspend fun decline(call: CallId, busy: Boolean) =
        update(call) {
            copy(state = EngineCallState.Ended(status = null, byRemote = false))
        }

    override suspend fun hangUp(call: CallId) =
        update(call) {
            if (state is EngineCallState.Ended) this
            else copy(state = EngineCallState.Ended(status = null, byRemote = false))
        }

    override suspend fun hold(call: CallId, onHold: Boolean) =
        update(call) {
            when {
                onHold && state == EngineCallState.Active -> copy(state = EngineCallState.LocalHold)
                !onHold && state == EngineCallState.LocalHold ->
                    copy(state = EngineCallState.Active)
                else -> this
            }
        }

    override suspend fun setMuted(call: CallId, muted: Boolean) =
        update(call) {
            copy(muted = muted)
        }

    override suspend fun sendDtmf(call: CallId, digit: Char) {
        dtmfSent += call to digit
    }

    override suspend fun forget(call: CallId) {
        _calls.update { all -> all.filterNot { it.id == call } }
    }

    /** An INVITE arrives for [account] from [from]. */
    fun incomingCall(
        from: SipAddress,
        account: AccountId? = accounts.firstOrNull()?.id,
        name: String? = null,
    ): CallId {
        val id = newCallId()
        put(EngineCall(id, account, CallDirection.Incoming, from, name, EngineCallState.Incoming))
        return id
    }

    fun remoteRings(call: CallId) = update(call) { copy(state = EngineCallState.Ringing) }

    fun remoteAnswers(call: CallId) =
        update(call) {
            copy(state = EngineCallState.Active, media = MediaInfo("opus", 48_000))
        }

    fun remoteHolds(call: CallId, onHold: Boolean) =
        update(call) {
            copy(state = if (onHold) EngineCallState.RemoteHold else EngineCallState.Active)
        }

    /** The other side ends the call: hangs up ([status] `null`), or fails it with [status]. */
    fun remoteEnds(call: CallId, status: SipStatus? = null, detail: String? = null) =
        update(call) {
            copy(state = EngineCallState.Ended(status, byRemote = true, detail = detail))
        }

    private suspend fun playDemoCall(id: CallId, to: SipAddress) {
        delay(DemoStep)
        remoteRings(id)
        delay(DemoStep * 3)
        when (to.user) {
            "486" -> remoteEnds(id, SipStatus(486, "Busy Here"))
            "603" -> remoteEnds(id, SipStatus(603, "Decline"))
            "404" -> remoteEnds(id, SipStatus(404, "Not Found"))
            else -> remoteAnswers(id)
        }
    }

    private fun newCallId() = CallId("fake-call-${++nextCall}")

    private fun put(call: EngineCall) = _calls.update { it + call }

    private fun update(call: CallId, change: EngineCall.() -> EngineCall) = _calls.update { all ->
        all.map { if (it.id == call) it.change() else it }
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

    private companion object {
        val DemoStep = 500.milliseconds
    }
}
