/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.fake

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.CallId
import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.sip.api.AccountSpec
import com.bobbyesp.sona.sip.api.AudioDeviceKind
import com.bobbyesp.sona.sip.api.CallDirection
import com.bobbyesp.sona.sip.api.CallStatistics
import com.bobbyesp.sona.sip.api.CallVideo
import com.bobbyesp.sona.sip.api.CameraFacing
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
import com.bobbyesp.sona.sip.api.SipTraceEntry
import com.bobbyesp.sona.sip.api.Transport
import com.bobbyesp.sona.sip.api.VideoConfig
import com.bobbyesp.sona.sip.api.VideoSize
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
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
 * **Video** follows [VideoConfig] as a real engine does: a call dialed or answered with the camera
 * sends it, and video the other side adds (`fake.remoteStartsVideo(call)`) or offers to a call
 * answered without the camera is taken receive-only. Holding stops the video with the audio, and
 * resuming does not bring it back by itself: what comes back is the coordinator's to ask.
 *
 * **Statistics** are a deterministic random walk around [network] (or a call's own,
 * `fake.network(call, FakeNetwork.Weak)`), measured once a second, and only while collected.
 *
 * @param scope Where registrations and scripted calls take their time; a test's scope, or the
 *   app's.
 * @param demoCalls Whether a dialed call plays out by itself, for the demo flavor: it rings, then
 *   is answered (with video when dialed with video), except `486` (busy), `603` (declined) and
 *   `404` (no such extension). `555` is answered without video and the other side turns its camera
 *   on a moment later; `777` is answered over a weak network, so the quality indicator says Poor.
 */
class FakeSipEngine(
    private val scope: CoroutineScope,
    private val registrationDelay: Duration = 600.milliseconds,
    private val demoCalls: Boolean = false,
    private val clock: () -> Long = { 0L },
) : SipEngine {

    private val _state = MutableStateFlow<EngineState>(EngineState.Stopped)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    override val version: String = "Demo engine (no SIP)"

    override val capabilities: Set<Capability> =
        setOf(
            Capability.AudioCalls,
            Capability.Dtmf,
            Capability.Hold,
            Capability.SipTrace,
            Capability.VideoCalls,
            Capability.CallStats,
        )

    private val _registrations = MutableStateFlow<Map<AccountId, RegistrationState>>(emptyMap())
    override val registrations: StateFlow<Map<AccountId, RegistrationState>> =
        _registrations.asStateFlow()

    /** The accounts as last given, for asserting what the app sent. */
    var accounts: List<AccountSpec> = emptyList()
        private set

    /** The configuration as last applied. */
    var config: EngineConfig? = null
        private set

    private val videoConfig: VideoConfig
        get() = config?.video ?: VideoConfig()

    private val _calls = MutableStateFlow<List<EngineCall>>(emptyList())
    override val calls: StateFlow<List<EngineCall>> = _calls.asStateFlow()

    /** Every DTMF digit sent, in order, for asserting. */
    val dtmfSent: MutableList<Pair<CallId, Char>> = mutableListOf()

    private var nextCall = 0

    private val _trace = MutableStateFlow<List<SipTraceEntry>>(emptyList())
    override val trace: StateFlow<List<SipTraceEntry>> = _trace.asStateFlow()

    override suspend fun clearTrace() {
        _trace.value = emptyList()
    }

    /**
     * A SIP message goes by, kept when the configuration captures the trace: `fake.traced(true,
     * "REGISTER sip:lab.local SIP/2.0", "CSeq: 1 REGISTER")`. The fake writes its own for what it
     * plays (a registration, a call), so the demo has a trace to read.
     */
    fun traced(outgoing: Boolean, vararg lines: String, peer: String = "lab.local:5060") {
        if (config?.captureTrace != true) return
        _trace.update { all ->
            all +
                SipTraceEntry(
                    id = all.size + 1L,
                    atMillis = clock(),
                    outgoing = outgoing,
                    transport = "UDP",
                    peer = peer,
                    raw = lines.joinToString("\n"),
                )
        }
    }

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

    override suspend fun dial(account: AccountId, to: SipAddress, video: Boolean): DialResult {
        if (_registrations.value[account] != RegistrationState.Registered) {
            return DialResult.AccountUnavailable
        }
        val id = newCallId()
        val offered =
            if (video && videoConfig.enabled)
                CallVideo(sending = true, receiving = true, remoteSending = false).sized()
            else null
        put(
            EngineCall(
                id,
                account,
                CallDirection.Outgoing,
                to,
                state = EngineCallState.Dialing,
                video = offered,
            ),
        )
        traced(
            true,
            "INVITE ${to.uri} SIP/2.0",
            "Call-ID: ${id.value}",
            "CSeq: 20 INVITE",
            peer = "${to.host}:5060",
        )
        if (demoCalls) scope.launch { playDemoCall(id, to) }
        return DialResult.Started(id)
    }

    override suspend fun answer(call: CallId, video: Boolean) =
        update(call) {
            if (state != EngineCallState.Incoming) return@update this
            val answered =
                when {
                    !videoConfig.enabled || !videoOffered -> null
                    video -> CallVideo(sending = true, receiving = true, remoteSending = true)
                    videoConfig.acceptRemoteVideo ->
                        CallVideo(sending = false, receiving = true, remoteSending = true)
                    else -> null
                }
            copy(state = EngineCallState.Active, video = answered?.sized())
        }

    /** Every call refused, with whether it was told busy (`486`) rather than declined (`603`). */
    val declined = mutableListOf<Pair<CallId, Boolean>>()

    override suspend fun decline(call: CallId, busy: Boolean) {
        declined += call to busy
        declineCall(call)
    }

    private suspend fun declineCall(call: CallId) =
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
                // The video stops with the audio, and a resume does not restore it by itself.
                onHold && state == EngineCallState.Active ->
                    copy(
                        state = EngineCallState.LocalHold,
                        video = video?.copy(sending = false, receiving = false)?.sized(),
                    )
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

    /** The device each call's audio was last put on, for asserting. */
    val audioDevices: MutableMap<CallId, AudioDeviceKind> = mutableMapOf()

    override suspend fun setAudioDevice(call: CallId, device: AudioDeviceKind) {
        audioDevices[call] = device
    }

    /** A request of [SipEngine.setVideo], as it was made. */
    data class VideoRequest(val call: CallId, val send: Boolean, val receive: Boolean)

    /** Every [setVideo] asked, in order, for asserting. */
    val videoRequests: MutableList<VideoRequest> = mutableListOf()

    override suspend fun setVideo(call: CallId, send: Boolean, receive: Boolean) {
        videoRequests += VideoRequest(call, send, receive)
        if (!videoConfig.enabled) return
        update(call) {
            when {
                state is EngineCallState.Ended -> this
                !send && !receive -> copy(video = null)
                // Video added to a call without it: the other side answers sending too.
                else ->
                    copy(
                        video =
                            (video ?: CallVideo(false, false, remoteSending = true))
                                .copy(sending = send, receiving = receive)
                                .sized(),
                    )
            }
        }
    }

    override suspend fun switchCamera(call: CallId) =
        update(call) {
            val now = video ?: return@update this
            val other =
                if (now.camera == CameraFacing.Front) CameraFacing.Back else CameraFacing.Front
            copy(video = now.copy(camera = other))
        }

    /** The display's rotation as last given, for asserting. */
    var rotation: Int = 0
        private set

    override suspend fun setDeviceRotation(degrees: Int) {
        rotation = degrees
        _calls.update { all -> all.map { it.copy(video = it.video?.sized()) } }
    }

    /** What the network does to every call's media, unless a call has its own. */
    var network: FakeNetwork = FakeNetwork.Good

    private val networks = mutableMapOf<CallId, FakeNetwork>()

    /** [call]'s media goes over [network] from now on: [FakeNetwork.Weak] shows Poor. */
    fun network(call: CallId, network: FakeNetwork) {
        networks[call] = network
    }

    /** How many times statistics were measured: none while nobody collects them. */
    var statisticsSamples: Int = 0
        private set

    override fun statistics(call: CallId): Flow<CallStatistics> = flow {
        val meter = FakeMeter(seed = call.value.hashCode())
        while (true) {
            val now = _calls.value.firstOrNull { it.id == call } ?: break
            if (now.state is EngineCallState.Ended) break
            statisticsSamples++
            meter.step(networks[now.id] ?: network)
            emit(meter.sample(now, videoConfig, transportOf(now.account)))
            delay(STATISTICS_PERIOD)
        }
    }

    private fun transportOf(account: AccountId?): Transport? =
        accounts
            .firstOrNull { it.id == account }
            ?.let { (it.outboundProxy ?: it.registrar).transport }

    override suspend fun forget(call: CallId) {
        audioDevices.remove(call)
        networks.remove(call)
        _calls.update { all -> all.filterNot { it.id == call } }
    }

    /** An INVITE arrives for [account] from [from], offering video when [video]. */
    fun incomingCall(
        from: SipAddress,
        account: AccountId? = accounts.firstOrNull()?.id,
        name: String? = null,
        video: Boolean = false,
    ): CallId {
        val id = newCallId()
        put(
            EngineCall(
                id,
                account,
                CallDirection.Incoming,
                from,
                name,
                EngineCallState.Incoming,
                videoOffered = video && videoConfig.enabled,
            ),
        )
        return id
    }

    fun remoteRings(call: CallId) = update(call) { copy(state = EngineCallState.Ringing) }

    /**
     * The other side answers. [video] is whether it takes the video offered: by default it does
     * when the call was dialed with video, and sends its own camera too.
     */
    fun remoteAnswers(call: CallId, video: Boolean? = null) {
        answered(call, SipStatus(200, "OK"))
        update(call) {
            val taken = video ?: (this.video != null)
            copy(
                state = EngineCallState.Active,
                media = MediaInfo("opus", 48_000),
                video = this.video?.takeIf { taken }?.copy(remoteSending = true)?.sized(),
            )
        }
    }

    /** The trace of the other side's answer to an outgoing [call]. */
    private fun answered(call: CallId, status: SipStatus) {
        val known = _calls.value.firstOrNull { it.id == call } ?: return
        if (known.direction != CallDirection.Outgoing) return
        traced(
            false,
            "SIP/2.0 $status",
            "Call-ID: ${call.value}",
            "CSeq: 20 INVITE",
            peer = "${known.remote.host}:5060",
        )
    }

    fun remoteHolds(call: CallId, onHold: Boolean) =
        update(call) {
            copy(state = if (onHold) EngineCallState.RemoteHold else EngineCallState.Active)
        }

    /**
     * The other side turns its camera on: a re-INVITE adding video, or a new direction. Taken
     * receive-only when [VideoConfig.acceptRemoteVideo] says so, declined otherwise. [withCamera]
     * plays an engine that took it with this side's camera on, which the coordinator must not let
     * stand.
     */
    fun remoteStartsVideo(call: CallId, withCamera: Boolean = false) {
        if (!videoConfig.enabled || !videoConfig.acceptRemoteVideo) return
        update(call) {
            val now =
                video ?: CallVideo(sending = withCamera, receiving = true, remoteSending = true)
            copy(video = now.copy(receiving = true, remoteSending = true).sized())
        }
    }

    /**
     * The other side turns its camera off. Without this side's camera either, nothing is left to
     * send or receive, and the call has no video anymore.
     */
    fun remoteStopsVideo(call: CallId) =
        update(call) {
            val now = video ?: return@update this
            copy(video = if (now.sending) now.copy(remoteSending = false).sized() else null)
        }

    /** The other side ends the call: hangs up ([status] `null`), or fails it with [status]. */
    fun remoteEnds(call: CallId, status: SipStatus? = null, detail: String? = null) {
        status?.let { answered(call, it) }
        update(call) {
            copy(state = EngineCallState.Ended(status, byRemote = true, detail = detail))
        }
    }

    private suspend fun playDemoCall(id: CallId, to: SipAddress) {
        delay(DemoStep)
        remoteRings(id)
        delay(DemoStep * 3)
        when (to.user) {
            "486" -> remoteEnds(id, SipStatus(486, "Busy Here"))
            "603" -> remoteEnds(id, SipStatus(603, "Decline"))
            "404" -> remoteEnds(id, SipStatus(404, "Not Found"))
            "555" -> {
                remoteAnswers(id)
                delay(DemoStep * 6)
                remoteStartsVideo(id)
            }
            "777" -> {
                network(id, FakeNetwork.Weak)
                remoteAnswers(id)
            }
            else -> remoteAnswers(id)
        }
    }

    /**
     * The sizes a real engine reports: the configured definition sent, upright as the phone is
     * held, and a phone held upright at the other side.
     */
    private fun CallVideo.sized(): CallVideo {
        val definition = videoConfig.definition
        val sent =
            if (rotation % 180 == 0) VideoSize(definition.height, definition.width) else definition
        return copy(
            sentSize = sent.takeIf { sending },
            receivedSize = RemoteSize.takeIf { showsRemote },
            codec = codec ?: videoConfig.codecs?.firstOrNull() ?: "VP8",
        )
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
        val peer = "${account.registrar.host}:${account.registrar.port ?: 5060}"
        val headers =
            arrayOf(
                "From: <${account.identity.uri}>",
                "To: <${account.identity.uri}>",
                "Call-ID: register-${id.value}",
                "CSeq: 20 REGISTER",
            )
        traced(true, "REGISTER sip:${account.registrar.host} SIP/2.0", *headers, peer = peer)
        pending[id] = scope.launch {
            delay(registrationDelay)
            failures[id]?.let { traced(false, "SIP/2.0 $it", *headers, peer = peer) }
            if (id !in failures) traced(false, "SIP/2.0 200 OK", *headers, peer = peer)
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
        val STATISTICS_PERIOD = 1.seconds
        val RemoteSize = VideoSize(720, 1280)
    }
}
