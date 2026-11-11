/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.calling.domain

import com.bobbyesp.sona.accounts.domain.AccountsRepository
import com.bobbyesp.sona.accounts.domain.defaultAccount
import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.CallId
import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.core.model.settings.CallSettings
import com.bobbyesp.sona.core.model.settings.DeclineMode
import com.bobbyesp.sona.core.model.settings.SettingsStore
import com.bobbyesp.sona.sip.api.AudioDeviceKind
import com.bobbyesp.sona.sip.api.CallDirection
import com.bobbyesp.sona.sip.api.DialResult
import com.bobbyesp.sona.sip.api.EngineCall
import com.bobbyesp.sona.sip.api.EngineCallState
import com.bobbyesp.sona.sip.api.RegistrationState
import com.bobbyesp.sona.sip.api.SipEngine
import com.bobbyesp.sona.sip.api.SipStatus
import com.bobbyesp.sona.telecom.api.AudioRoute
import com.bobbyesp.sona.telecom.api.AudioRouteKind
import com.bobbyesp.sona.telecom.api.DisconnectCause
import com.bobbyesp.sona.telecom.api.SystemCall
import com.bobbyesp.sona.telecom.api.SystemCallEvents
import com.bobbyesp.sona.telecom.api.SystemCallRequest
import com.bobbyesp.sona.telecom.api.SystemCalls
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What dialing did, for the dialer to explain when nothing was dialed. */
sealed interface DialOutcome {
    data class Started(val id: CallId) : DialOutcome

    data object NoAccount : DialOutcome

    data class AccountOff(val label: String) : DialOutcome

    /** The account exists and is on, but cannot call right now. */
    data class NotRegistered(val label: String, val registration: RegistrationState?) : DialOutcome

    data class InvalidAddress(val reason: AddressResolution.Reason) : DialOutcome

    data class Failed(val detail: String) : DialOutcome
}

/** Whether a call needs the app in the foreground, and its microphone. */
sealed interface CallForeground {
    data object None : CallForeground

    /** A call exists. [microphone] once one was dialed or answered (ringing needs none). */
    data class Calls(val microphone: Boolean) : CallForeground
}

/**
 * An incoming call that was refused before it rang: by do not disturb, or as busy during another
 * call with call waiting off. It never becomes a [Call] (nothing rings, nothing is shown), but it
 * is told, so the history can say who called.
 *
 * @property status What the caller was answered: `486 Busy Here` or `603 Decline`.
 * @property quiet The user asked not to be disturbed: to be written down, not announced.
 */
data class RefusedCall(
    val account: AccountId?,
    val remote: SipAddress,
    val remoteName: String?,
    val status: SipStatus,
    val quiet: Boolean,
    val at: Long,
)

/**
 * The only thing that changes a call. It follows the engine's calls, keeps Android's call system
 * (Telecom) in step with them, carries out what the user and the system ask, and publishes [calls]
 * as the user sees them. Screens send it intents; no ViewModel, service or receiver calls the
 * engine or Telecom for a call. See docs/calling.md.
 *
 * Everything that changes a call goes through one lock, in order: an engine update, the user's
 * answer and a headset's hang-up never interleave.
 *
 * @param endedLinger How long an ended call stays in [calls], for the call screen to say how it
 *   ended before closing.
 */
class CallCoordinator(
    private val engine: SipEngine,
    private val system: SystemCalls,
    private val accounts: AccountsRepository,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val endedLinger: Duration = 3.seconds,
    private val names: CallerNames = CallerNames.None,
) {
    private class Tracked(
        val engine: EngineCall,
        val system: SystemCall? = null,
        val answered: Boolean = false,
        val connectedAt: Long? = null,
        val endReason: EndReason? = null,
        val routeJob: Job? = null,
        /** Who the contacts say the other side is; preferred to what the server says. */
        val name: String? = null,
    ) {
        fun with(
            engine: EngineCall = this.engine,
            system: SystemCall? = this.system,
            answered: Boolean = this.answered,
            connectedAt: Long? = this.connectedAt,
            endReason: EndReason? = this.endReason,
            routeJob: Job? = this.routeJob,
            name: String? = this.name,
        ) = Tracked(engine, system, answered, connectedAt, endReason, routeJob, name)

        fun toCall(audio: Audio?): Call =
            Call(
                id = engine.id,
                account = engine.account,
                direction = engine.direction,
                remote = engine.remote,
                remoteName = name ?: engine.remoteName,
                state = engine.state.toCallState { endReason ?: EndReason.HungUp(byMe = true) },
                muted = engine.muted,
                media = engine.media,
                connectedAt = connectedAt,
                route = audio?.route,
                routes = audio?.routes.orEmpty(),
            )
    }

    /** Where a call's audio goes, as the system reports it: kept apart, it changes on its own. */
    private data class Audio(val route: AudioRoute?, val routes: List<AudioRoute>)

    private val lock = Mutex()
    private val tracked = MutableStateFlow<Map<CallId, Tracked>>(emptyMap())
    private val audio = MutableStateFlow<Map<CallId, Audio>>(emptyMap())

    /** Every call, in the order they began; an ended one stays a moment ([endedLinger]). */
    val calls: StateFlow<List<Call>> =
        combine(tracked, audio) { all, routes ->
                all.values.map { it.toCall(routes[it.engine.id]) }
            }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val foreground: StateFlow<CallForeground> =
        tracked
            .map { all ->
                val live = all.values.filter { it.engine.state !is EngineCallState.Ended }
                if (live.isEmpty()) CallForeground.None
                else
                    CallForeground.Calls(
                        microphone =
                            live.any {
                                it.answered || it.engine.direction == CallDirection.Outgoing
                            },
                    )
            }
            .stateIn(scope, SharingStarted.Eagerly, CallForeground.None)

    private val _refused = MutableSharedFlow<RefusedCall>(extraBufferCapacity = 16)

    /** The calls refused before they rang, as each happens: for the history, not for a screen. */
    val refused: SharedFlow<RefusedCall> = _refused.asSharedFlow()

    /** Follows the engine's calls; once, at start. */
    fun start(): Job = scope.launch {
        engine.calls.collect { list -> lock.withLock { onEngine(list) } }
    }

    suspend fun dial(input: String, account: AccountId? = null): DialOutcome {
        val from =
            (account?.let { accounts.get(it) } ?: accounts.defaultAccount().first())
                ?: return DialOutcome.NoAccount
        if (!from.enabled) return DialOutcome.AccountOff(from.label)
        val address =
            when (val resolution = AddressResolver.resolve(input, from.domain)) {
                is AddressResolution.Invalid -> return DialOutcome.InvalidAddress(resolution.reason)
                is AddressResolution.Resolved -> resolution.address
            }
        return when (val result = engine.dial(from.id, address)) {
            is DialResult.Started -> DialOutcome.Started(result.id)
            DialResult.AccountUnavailable ->
                DialOutcome.NotRegistered(from.label, engine.registrations.value[from.id])
            is DialResult.Failed -> DialOutcome.Failed(result.detail)
        }
    }

    /** Answers [id]; a call already in progress is put on hold first (call waiting). */
    fun answer(id: CallId) = act { answerLocked(id, fromSystem = false) }

    fun decline(id: CallId) = act { engine.decline(id) }

    /** Ends [id]: hangs up an answered call, declines a ringing one, cancels a dialing one. */
    fun hangUp(id: CallId) = act { endLocked(id) }

    fun setOnHold(id: CallId, onHold: Boolean) = act { engine.hold(id, onHold) }

    fun setMuted(id: CallId, muted: Boolean) = act { engine.setMuted(id, muted) }

    fun sendDtmf(id: CallId, digit: Char) = act { engine.sendDtmf(id, digit) }

    /** Moves the audio of [id]: the system routes it, and the engine's streams follow. */
    fun selectRoute(id: CallId, route: AudioRoute) = act {
        tracked.value[id]?.system?.requestRoute(route)
    }

    private fun act(block: suspend () -> Unit) {
        scope.launch { lock.withLock { block() } }
    }

    private suspend fun onEngine(list: List<EngineCall>) {
        for (call in list) {
            val known = tracked.value[call.id]
            if (known == null) begin(call) else change(known, call)
        }
        // Forgotten by the engine: gone for good.
        val present = list.map { it.id }.toSet()
        tracked.value.keys
            .filterNot { it in present }
            .forEach { id ->
                tracked.value[id]?.routeJob?.cancel()
                tracked.update { it - id }
                audio.update { it - id }
            }
    }

    private suspend fun begin(call: EngineCall) {
        val state = call.state
        if (state is EngineCallState.Ended) {
            // Ended before it was seen: a call that failed at once (busy, no such extension,
            // nothing sent). A dialed one is still shown with its reason, or the user never learns
            // why nothing happened; an incoming one rang too briefly to show.
            if (call.direction == CallDirection.Outgoing) {
                val reason = EndReason.of(state, call.direction, answered = false)
                tracked.update {
                    it +
                        (call.id to
                            Tracked(call, endReason = reason, name = names.nameFor(call.remote)))
                }
                forgetLater(call.id)
            } else {
                engine.forget(call.id)
            }
            return
        }
        if (
            call.direction == CallDirection.Incoming &&
                settings.observe(CallSettings.DoNotDisturb).first()
        ) {
            // Do not disturb: refused before it rings, and told as the user chose.
            val busy = settings.observe(CallSettings.DeclineWith).first() == DeclineMode.Busy
            engine.decline(call.id, busy = busy)
            refused(call, busy = busy, quiet = true)
            return
        }
        if (call.direction == CallDirection.Incoming && shouldRejectAsBusy()) {
            engine.decline(call.id, busy = true)
            refused(call, busy = true, quiet = false)
            return
        }
        val name = names.nameFor(call.remote)
        tracked.update { it + (call.id to Tracked(call, name = name)) }
        scope.launch { addToSystem(call, name) }
    }

    private suspend fun refused(call: EngineCall, busy: Boolean, quiet: Boolean) {
        _refused.emit(
            RefusedCall(
                account = call.account,
                remote = call.remote,
                remoteName = names.nameFor(call.remote) ?: call.remoteName,
                status = if (busy) SipStatus(486, "Busy Here") else SipStatus(603, "Decline"),
                quiet = quiet,
                at = clock(),
            ),
        )
    }

    /** Busy when a call is in progress and call waiting is off. */
    private suspend fun shouldRejectAsBusy(): Boolean {
        val inProgress = tracked.value.values.any { it.engine.state !is EngineCallState.Ended }
        return inProgress && !settings.observe(CallSettings.CallWaiting).first()
    }

    private suspend fun change(known: Tracked, call: EngineCall) {
        val before = known.engine.state
        val after = call.state
        var next = known.with(engine = call)
        if (before != after) {
            when (after) {
                EngineCallState.Active -> {
                    if (known.connectedAt == null)
                        next = next.with(answered = true, connectedAt = clock())
                    if (
                        call.direction == CallDirection.Outgoing ||
                            before == EngineCallState.LocalHold
                    ) {
                        known.system?.setActive()
                    }
                }
                EngineCallState.LocalHold -> known.system?.setInactive()
                is EngineCallState.Ended -> {
                    val reason = EndReason.of(after, call.direction, known.answered)
                    next = next.with(endReason = reason)
                    known.system?.disconnect(reason.toDisconnectCause())
                    forgetLater(call.id)
                }
                else -> Unit
            }
        }
        tracked.update { it + (call.id to next) }
    }

    private fun forgetLater(id: CallId) {
        scope.launch {
            delay(endedLinger)
            engine.forget(id)
        }
    }

    private suspend fun addToSystem(call: EngineCall, name: String?) {
        val handle =
            system.add(
                SystemCallRequest(
                    id = call.id,
                    displayName = name ?: call.remoteName ?: call.remote.user ?: call.remote.host,
                    address = call.remote.uri,
                    incoming = call.direction == CallDirection.Incoming,
                ),
                events = EventsFor(call.id),
            ) ?: return
        lock.withLock {
            val known = tracked.value[call.id]
            if (known == null || known.endReason != null) {
                handle.disconnect(known?.endReason?.toDisconnectCause() ?: DisconnectCause.Local)
                return
            }
            // The call may have moved on while the system was adding it.
            if (known.engine.state == EngineCallState.Active) handle.setActive()
            if (known.engine.state == EngineCallState.LocalHold) handle.setInactive()
            val routeJob = scope.launch {
                launch {
                    combine(handle.route, handle.routes, ::Audio).collect { now ->
                        audio.update { it + (call.id to now) }
                    }
                }
                // The system owns the route; the engine's streams are moved to follow it. Without
                // this the route changes and the sound stays where the engine first put it.
                handle.route
                    .map { it?.kind?.toEngineDevice() }
                    .distinctUntilChanged()
                    .collect { device ->
                        if (device != null) lock.withLock { engine.setAudioDevice(call.id, device) }
                    }
            }
            tracked.update { it + (call.id to known.with(system = handle, routeJob = routeJob)) }
        }
    }

    private suspend fun answerLocked(id: CallId, fromSystem: Boolean) {
        val known = tracked.value[id] ?: return
        if (known.engine.state != EngineCallState.Incoming) return
        tracked.value.values
            .filter { it.engine.id != id && it.engine.state == EngineCallState.Active }
            .forEach { engine.hold(it.engine.id, true) }
        if (!fromSystem) known.system?.answer()
        engine.answer(id)
    }

    private suspend fun endLocked(id: CallId) {
        val known = tracked.value[id] ?: return
        if (known.engine.state == EngineCallState.Incoming) engine.decline(id)
        else engine.hangUp(id)
    }

    /** What the system asks of one call, carried out in the same order as everything else. */
    private inner class EventsFor(private val id: CallId) : SystemCallEvents {
        override suspend fun onAnswer() = lock.withLock { answerLocked(id, fromSystem = true) }

        override suspend fun onDisconnect() = lock.withLock { endLocked(id) }

        override suspend fun onSetActive() = lock.withLock { engine.hold(id, false) }

        override suspend fun onSetInactive() = lock.withLock { engine.hold(id, true) }
    }
}

/** The engine's kind of device for the system's; `null` for one the engine cannot be told of. */
private fun AudioRouteKind.toEngineDevice(): AudioDeviceKind? =
    when (this) {
        AudioRouteKind.Earpiece -> AudioDeviceKind.Earpiece
        AudioRouteKind.Speaker -> AudioDeviceKind.Speaker
        AudioRouteKind.WiredHeadset -> AudioDeviceKind.WiredHeadset
        AudioRouteKind.Bluetooth -> AudioDeviceKind.Bluetooth
        AudioRouteKind.Other -> null
    }

private fun EndReason.toDisconnectCause(): DisconnectCause =
    when (this) {
        is EndReason.HungUp -> if (byMe) DisconnectCause.Local else DisconnectCause.Remote
        EndReason.Missed -> DisconnectCause.Missed
        EndReason.Cancelled -> DisconnectCause.Local
        is EndReason.Busy -> DisconnectCause.Busy
        is EndReason.Declined -> DisconnectCause.Rejected
        else -> DisconnectCause.Error
    }
