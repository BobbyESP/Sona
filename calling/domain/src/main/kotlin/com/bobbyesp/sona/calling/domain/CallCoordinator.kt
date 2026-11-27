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
import com.bobbyesp.sona.core.model.settings.VideoSettings
import com.bobbyesp.sona.sip.api.AudioDeviceKind
import com.bobbyesp.sona.sip.api.CallDirection
import com.bobbyesp.sona.sip.api.CallStatistics
import com.bobbyesp.sona.sip.api.CallVideo
import com.bobbyesp.sona.sip.api.Capability
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
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

/** Whether a call needs the app in the foreground, and its microphone and camera. */
sealed interface CallForeground {
    data object None : CallForeground

    /**
     * A call exists. [microphone] once one was dialed or answered (ringing needs none); [camera]
     * while a call's camera is on, and the call is neither on hold nor over.
     */
    data class Calls(val microphone: Boolean, val camera: Boolean = false) : CallForeground
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
 * **Video** is a second stream of the same call (docs/video.md). The coordinator keeps what the
 * user wants of the camera ([Call.cameraOn]) apart from what the engine negotiated ([Call.video]),
 * and makes the second follow the first: video the other side adds is shown with this side's camera
 * off, a hold brings back on resume the directions it stopped, and the camera is never asked of the
 * engine without the permission.
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
    private val camera: CameraPermission = CameraPermission.Granted,
) {
    /** Video directions, as asked or as they were. */
    private data class Directions(val send: Boolean, val receive: Boolean) {
        val any: Boolean
            get() = send || receive
    }

    private data class Tracked(
        val engine: EngineCall,
        val system: SystemCall? = null,
        val answered: Boolean = false,
        val connectedAt: Long? = null,
        val endReason: EndReason? = null,
        val routeJob: Job? = null,
        /** Who the contacts say the other side is; preferred to what the server says. */
        val name: String? = null,
        /** The caller offered video, and video calls are on. */
        val videoOffered: Boolean = false,
        /** The user wants the camera sent; the engine's video is made to follow. */
        val cameraOn: Boolean = false,
        val cameraBlocked: CameraBlocked? = null,
        /** The directions a local hold stopped, to bring back on resume. */
        val held: Directions? = null,
        /** The route was already looked at for this stretch of video: chosen once, then left. */
        val routedForVideo: Boolean = false,
        /** Whether the system was told this is a video call. */
        val systemVideo: Boolean = false,
        /**
         * New directions were just asked of the engine: until it answers, what it reports is the
         * old state, and the system is not told about it.
         */
        val settling: Boolean = false,
    ) {
        val isOver: Boolean
            get() = engine.state is EngineCallState.Ended

        val isHeld: Boolean
            get() =
                engine.state == EngineCallState.LocalHold ||
                    engine.state == EngineCallState.RemoteHold

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
                video = engine.video,
                videoOffered = videoOffered,
                cameraOn = cameraOn,
                cameraBlocked = cameraBlocked,
            )
    }

    /** Where a call's audio goes, as the system reports it: kept apart, it changes on its own. */
    private data class Audio(val route: AudioRoute?, val routes: List<AudioRoute>)

    private val lock = Mutex()
    private val tracked = MutableStateFlow<Map<CallId, Tracked>>(emptyMap())
    private val audio = MutableStateFlow<Map<CallId, Audio>>(emptyMap())

    /** What the user asked of a call's camera when dialing it, until the call is first seen. */
    private data class Dialed(val camera: Boolean, val blocked: CameraBlocked?)

    private val dialed = mutableMapOf<CallId, Dialed>()

    /** Every call, in the order they began; an ended one stays a moment ([endedLinger]). */
    val calls: StateFlow<List<Call>> =
        combine(tracked, audio) { all, routes ->
                all.values.map { it.toCall(routes[it.engine.id]) }
            }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val foreground: StateFlow<CallForeground> =
        tracked
            .map { all ->
                val live = all.values.filterNot { it.isOver }
                if (live.isEmpty()) CallForeground.None
                else
                    CallForeground.Calls(
                        microphone =
                            live.any {
                                it.answered || it.engine.direction == CallDirection.Outgoing
                            },
                        camera = live.any { it.cameraOn && !it.isHeld },
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

    /**
     * Calls [input] from [account] (the default one when `null`), with the camera on when [video].
     * Without the camera permission the call is dialed as audio and says why
     * ([Call.cameraBlocked]); with video off in the settings, or an engine without it, as audio.
     */
    suspend fun dial(
        input: String,
        account: AccountId? = null,
        video: Boolean = false,
    ): DialOutcome {
        val from =
            (account?.let { accounts.get(it) } ?: accounts.defaultAccount().first())
                ?: return DialOutcome.NoAccount
        if (!from.enabled) return DialOutcome.AccountOff(from.label)
        val address =
            when (val resolution = AddressResolver.resolve(input, from.domain)) {
                is AddressResolution.Invalid -> return DialOutcome.InvalidAddress(resolution.reason)
                is AddressResolution.Resolved -> resolution.address
            }
        val wantsCamera = video && videoAllowed()
        val withCamera = wantsCamera && camera.granted()
        // Under the lock, so the call is first seen once its camera's story is written down.
        return lock.withLock {
            when (val result = engine.dial(from.id, address, video = withCamera)) {
                is DialResult.Started -> {
                    val blocked = CameraBlocked.NoPermission.takeIf { wantsCamera && !withCamera }
                    dialed[result.id] = Dialed(withCamera, blocked)
                    DialOutcome.Started(result.id)
                }
                DialResult.AccountUnavailable ->
                    DialOutcome.NotRegistered(from.label, engine.registrations.value[from.id])
                is DialResult.Failed -> DialOutcome.Failed(result.detail)
            }
        }
    }

    /**
     * Answers [id], with the camera on when [video] and the caller offered video; a call already in
     * progress is put on hold first (call waiting). Without [video], video the caller offered is
     * still shown, receive-only, when the settings accept it: *Answer audio only* keeps this side's
     * camera off, not the other side's picture.
     */
    fun answer(id: CallId, video: Boolean = false) = act {
        answerLocked(id, fromSystem = false, video = video)
    }

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

    /**
     * Turns this side's camera on or off for [id]: a change of direction, so turning it off sends
     * nothing and the other side shows its placeholder. Turned on in an audio call, it adds video
     * both ways. Without the permission nothing is asked and the call says why. On hold, the wish
     * is kept for the resume; before the call is up, for when it is.
     */
    fun setCameraOn(id: CallId, on: Boolean) = act {
        val known = tracked.value[id] ?: return@act
        if (known.isOver) return@act
        if (on && !camera.granted()) {
            put(known.copy(cameraOn = false, cameraBlocked = CameraBlocked.NoPermission))
            return@act
        }
        if (on && !videoAllowed()) return@act
        val now = known.engine.video
        when (known.engine.state) {
            EngineCallState.LocalHold -> {
                val held = known.held ?: Directions(send = false, receive = on)
                put(known.copy(cameraOn = on, cameraBlocked = null, held = held.copy(send = on)))
            }
            EngineCallState.Active -> {
                put(known.copy(cameraOn = on, cameraBlocked = null))
                val receive = now?.receiving ?: on
                if (now != null || on) engine.setVideo(id, send = on, receive = receive)
            }
            else -> put(known.copy(cameraOn = on, cameraBlocked = null))
        }
    }

    /**
     * Video on or off for [id] as a whole. Off drops the stream both ways and keeps the voice (the
     * weak connection banner's *Turn off video*); on takes the other side's picture again,
     * receive-only, with the camera left off until the user turns it on.
     */
    fun setVideoEnabled(id: CallId, enabled: Boolean) = act {
        val known = tracked.value[id] ?: return@act
        if (known.isOver) return@act
        if (enabled && !videoAllowed()) return@act
        val now = known.engine.video
        when (known.engine.state) {
            EngineCallState.LocalHold ->
                put(
                    known.copy(
                        cameraOn = known.cameraOn && enabled,
                        held = if (enabled) Directions(known.cameraOn, receive = true) else null,
                    ),
                )
            EngineCallState.Active ->
                if (!enabled) {
                    put(known.copy(cameraOn = false))
                    if (now != null) engine.setVideo(id, send = false, receive = false)
                } else if (now?.receiving != true) {
                    engine.setVideo(
                        id,
                        send = known.cameraOn && now?.sending == true,
                        receive = true,
                    )
                }
            else -> if (!enabled) put(known.copy(cameraOn = false))
        }
    }

    /** Sends [id] from the other camera, front or back, without renegotiating. */
    fun switchCamera(id: CallId) = act {
        if (tracked.value[id]?.engine?.video != null) engine.switchCamera(id)
    }

    /**
     * How the display is turned, so the picture sent stays upright: any angle, taken to the nearest
     * quarter turn (0, 90, 180, 270).
     */
    fun setDeviceRotation(degrees: Int) = act { engine.setDeviceRotation(quarterTurn(degrees)) }

    /**
     * [id]'s numbers once a second while collected, and nothing measured while not (the engine
     * polls only for a collector). Empty on an engine without statistics.
     */
    fun statistics(id: CallId): Flow<CallStatistics> =
        if (Capability.CallStats in engine.capabilities) engine.statistics(id) else emptyFlow()

    /** [id]'s quality as it changes, from [statistics]; measured only while collected too. */
    fun quality(id: CallId): Flow<CallQuality> =
        statistics(id).map(CallQuality::of).filterNotNull().distinctUntilChanged()

    private fun act(block: suspend () -> Unit) {
        scope.launch { lock.withLock { block() } }
    }

    private fun put(call: Tracked) = tracked.update { it + (call.engine.id to call) }

    private suspend fun videoAllowed(): Boolean =
        Capability.VideoCalls in engine.capabilities &&
            settings.observe(VideoSettings.Enabled).first()

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
            dialed.remove(call.id)
            if (call.direction == CallDirection.Outgoing) {
                val reason = EndReason.of(state, call.direction, answered = false)
                put(Tracked(call, endReason = reason, name = names.nameFor(call.remote)))
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
        // A call dialed with the camera: the user asked for it, and the engine sends it.
        val asked = dialed.remove(call.id)
        val tracked =
            Tracked(
                call,
                name = name,
                videoOffered = call.videoOffered && videoAllowed(),
                cameraOn = (asked?.camera ?: false) && call.video != null,
                cameraBlocked = asked?.blocked,
            )
        put(tracked)
        scope.launch { addToSystem(tracked) }
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
        val inProgress = tracked.value.values.any { !it.isOver }
        return inProgress && !settings.observe(CallSettings.CallWaiting).first()
    }

    private suspend fun change(known: Tracked, call: EngineCall) {
        val before = known.engine.state
        val after = call.state
        // The engine moved: whatever was asked of it has had its answer.
        var next = known.copy(engine = call, settling = false)
        if (before != after) {
            when (after) {
                EngineCallState.Active -> {
                    if (known.connectedAt == null)
                        next = next.copy(answered = true, connectedAt = clock())
                    if (
                        call.direction == CallDirection.Outgoing ||
                            before == EngineCallState.LocalHold
                    ) {
                        known.system?.setActive()
                    }
                }
                EngineCallState.LocalHold -> known.system?.setInactive()
                is EngineCallState.Ended -> {
                    val reason =
                        EndReason.of(
                            after,
                            call.direction,
                            known.answered,
                            connectedForMs = known.connectedAt?.let { clock() - it },
                        )
                    next = next.copy(endReason = reason)
                    known.system?.disconnect(reason.toDisconnectCause())
                    forgetLater(call.id)
                }
                else -> Unit
            }
        }
        next = followCamera(next, before, known.engine.video)
        put(next)
        followVideo(call.id)
    }

    /**
     * Keeps the engine's video what the user wants of it, after the engine moved: the decisions of
     * docs/video.md that belong to the camera.
     */
    private suspend fun followCamera(
        call: Tracked,
        before: EngineCallState,
        videoBefore: CallVideo?,
    ): Tracked {
        val after = call.engine.state
        val video = call.engine.video
        val id = call.engine.id
        // Hold: the video stops with the audio; what it was is kept for the resume.
        if (after == EngineCallState.LocalHold && before == EngineCallState.Active) {
            val held = videoBefore?.let {
                Directions(send = it.sending && call.cameraOn, it.receiving)
            }
            return call.copy(held = held?.takeIf { it.any })
        }
        // Resume: the directions held before come back, unless the engine brought them already.
        if (after == EngineCallState.Active && before == EngineCallState.LocalHold) {
            val held = call.held
            val now = Directions(video?.sending == true, video?.receiving == true)
            val ask = held != null && held != now
            if (ask) engine.setVideo(id, held.send, held.receive)
            return call.copy(held = null, settling = ask)
        }
        if (after is EngineCallState.Ended) return call
        // No video left (the other side declined it, or dropped it): no camera to keep on.
        if (video == null && videoBefore != null && call.cameraOn) {
            return call.copy(cameraOn = false)
        }
        if (after != EngineCallState.Active) return call
        val ask =
            when {
                // Up at last: what the user changed while it rang, or was held, is asked now.
                before == EngineCallState.Dialing ||
                    before == EngineCallState.Ringing ||
                    before == EngineCallState.RemoteHold ->
                    when {
                        video != null && video.sending != call.cameraOn ->
                            Directions(call.cameraOn, video.receiving)
                        video == null && call.cameraOn -> Directions(send = true, receive = true)
                        else -> null
                    }
                // Privacy: video the other side added is shown with this side's camera off,
                // whatever the engine accepted. Only the user turns the camera on.
                video?.sending == true && videoBefore?.sending != true && !call.cameraOn ->
                    Directions(send = false, receive = video.receiving)
                else -> null
            } ?: return call
        engine.setVideo(id, ask.send, ask.receive)
        return call.copy(settling = true)
    }

    /**
     * The system's side of a call's video: its type follows whether the call shows video, and video
     * that starts on the earpiece moves the audio to the speaker, once (a headset or Bluetooth
     * route is kept, and a route the user picks after is theirs). Nothing changes while the call is
     * on hold, for it comes back as it was, nor while it rings: its video is not negotiated until
     * it is answered, and it rings as what was offered.
     */
    private suspend fun followVideo(id: CallId) {
        val known = tracked.value[id] ?: return
        val handle = known.system ?: return
        if (known.isOver || known.isHeld || known.settling) return
        if (known.engine.state == EngineCallState.Incoming) return
        val shows = known.engine.video?.isActive == true
        var next = known
        if (shows != known.systemVideo) {
            handle.setVideo(shows)
            next = next.copy(systemVideo = shows)
        }
        if (!shows) {
            // Video ended: the route is left as it is, and the next video looks at it again.
            next = next.copy(routedForVideo = false)
        } else if (!known.routedForVideo) {
            val route = handle.route.value
            if (route != null) {
                next = next.copy(routedForVideo = true)
                if (route.kind == AudioRouteKind.Earpiece) {
                    handle.routes.value
                        .firstOrNull { it.kind == AudioRouteKind.Speaker }
                        ?.let { handle.requestRoute(it) }
                }
            }
        }
        if (next != known) put(next)
    }

    private fun forgetLater(id: CallId) {
        scope.launch {
            delay(endedLinger)
            engine.forget(id)
        }
    }

    private suspend fun addToSystem(first: Tracked) {
        val call = first.engine
        val name = first.name
        // Ringing with video offered, or dialed with the camera: a video call to the system.
        val video =
            if (call.direction == CallDirection.Incoming) first.videoOffered
            else call.video?.isActive == true
        val handle =
            system.add(
                SystemCallRequest(
                    id = call.id,
                    displayName = name ?: call.remoteName ?: call.remote.user ?: call.remote.host,
                    address = call.remote.uri,
                    incoming = call.direction == CallDirection.Incoming,
                    video = video,
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
                    .distinctUntilChanged { old, new -> old?.kind == new?.kind }
                    .collect { route ->
                        lock.withLock {
                            route?.kind?.toEngineDevice()?.let {
                                engine.setAudioDevice(call.id, it)
                            }
                            // A route known at last: video may want the speaker.
                            followVideo(call.id)
                        }
                    }
            }
            put(known.copy(system = handle, routeJob = routeJob, systemVideo = video))
            followVideo(call.id)
        }
    }

    private suspend fun answerLocked(id: CallId, fromSystem: Boolean, video: Boolean) {
        val known = tracked.value[id] ?: return
        if (known.engine.state != EngineCallState.Incoming) return
        tracked.value.values
            .filter { it.engine.id != id && it.engine.state == EngineCallState.Active }
            .forEach { engine.hold(it.engine.id, true) }
        val offered = known.videoOffered
        // Another device's answer never turns the camera on.
        val wantsCamera = video && offered && !fromSystem
        val withCamera = wantsCamera && camera.granted()
        val showsVideo =
            withCamera || (offered && settings.observe(VideoSettings.AcceptRemote).first())
        put(
            known.copy(
                cameraOn = withCamera,
                cameraBlocked =
                    if (wantsCamera && !withCamera) CameraBlocked.NoPermission else null,
                systemVideo = if (fromSystem) known.systemVideo else showsVideo,
            ),
        )
        // The system first, so it has moved the audio to the call before the media starts.
        if (!fromSystem) known.system?.answer(video = showsVideo)
        engine.answer(id, video = withCamera)
    }

    private suspend fun endLocked(id: CallId) {
        val known = tracked.value[id] ?: return
        if (known.engine.state == EngineCallState.Incoming) engine.decline(id)
        else engine.hangUp(id)
    }

    /** What the system asks of one call, carried out in the same order as everything else. */
    private inner class EventsFor(private val id: CallId) : SystemCallEvents {
        override suspend fun onAnswer() = lock.withLock {
            answerLocked(id, fromSystem = true, video = false)
        }

        override suspend fun onDisconnect() = lock.withLock { endLocked(id) }

        override suspend fun onSetActive() = lock.withLock { engine.hold(id, false) }

        override suspend fun onSetInactive() = lock.withLock { engine.hold(id, true) }
    }
}

/** [degrees] taken to the nearest quarter turn, from 0 to 270. */
internal fun quarterTurn(degrees: Int): Int {
    val turned = Math.floorMod(degrees, 360)
    return (turned + 45) / 90 % 4 * 90
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
        EndReason.EndedAtOnce -> DisconnectCause.Remote
        EndReason.Cancelled -> DisconnectCause.Local
        is EndReason.Busy -> DisconnectCause.Busy
        is EndReason.Declined -> DisconnectCause.Rejected
        else -> DisconnectCause.Error
    }
