/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.linphone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.view.TextureView
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
import com.bobbyesp.sona.sip.api.SipTraceEntry
import com.bobbyesp.sona.sip.api.VideoSurfaces
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.linphone.core.Account
import org.linphone.core.AccountListenerStub
import org.linphone.core.AudioDevice
import org.linphone.core.AuthInfo
import org.linphone.core.Call
import org.linphone.core.CallStats
import org.linphone.core.ChatMessage
import org.linphone.core.ChatRoom
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.LoggingServiceListener
import org.linphone.core.MediaDirection
import org.linphone.core.MediaEncryption
import org.linphone.core.Reason
import org.linphone.core.StreamType

/** The app's Linphone engine. Linphone stays an implementation detail. */
fun Context.createLinphoneEngine(appVersion: String): SipEngine =
    LinphoneSipEngine(applicationContext, appVersion)

/** This engine's [VideoSurfaces]; it must come from [createLinphoneEngine]. */
fun SipEngine.linphoneVideoSurfaces(): VideoSurfaces =
    this as? LinphoneSipEngine
        ?: error("Not the Linphone engine: its video surfaces are its own ($this)")

/**
 * The SIP engine on liblinphone.
 *
 * **One thread owns the Core.** Linphone's `Core` is not thread safe: it is created, iterated
 * (every [ITERATE_MS]) and called only on the `sona-core` thread, which every public function
 * switches to. Listeners run there too, and what leaves is an immutable value in a `StateFlow`.
 *
 * **Linphone keeps nothing.** The Core is created without a configuration file and without push or
 * any Linphone service; the accounts are whatever [setAccounts] last gave, rebuilt from the app's
 * storage at every start. See docs/calling.md.
 *
 * **Video never starts on its own** (docs/video.md): the camera is sent only when the coordinator
 * asks, and video the other side adds is deferred, then taken receive-only.
 */
internal class LinphoneSipEngine(private val context: Context, appVersion: String) :
    SipEngine, VideoSurfaces {

    private val thread = HandlerThread("sona-core").apply { start() }
    private val handler = Handler(thread.looper)
    private val dispatcher = handler.asCoroutineDispatcher("sona-core")
    private val configurer = LinphoneConfigurer(appVersion)

    private val _state = MutableStateFlow<EngineState>(EngineState.Stopped)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    // Linphone says its version through a Core; read by any thread.
    @Volatile private var coreVersion: String? = null
    override val version: String
        get() = listOfNotNull("liblinphone", coreVersion).joinToString(" ")

    override val capabilities: Set<Capability> =
        setOf(
            Capability.AudioCalls,
            Capability.Dtmf,
            Capability.Hold,
            Capability.Messaging,
            Capability.Srtp,
            Capability.Zrtp,
            Capability.SipTrace,
            Capability.VideoCalls,
            Capability.CallStats,
        )

    private val _registrations = MutableStateFlow<Map<AccountId, RegistrationState>>(emptyMap())
    override val registrations: StateFlow<Map<AccountId, RegistrationState>> =
        _registrations.asStateFlow()

    // Only touched on the core thread.
    private var core: Core? = null
    private var desired: List<AccountSpec> = emptyList()
    private var config = EngineConfig()
    private val bound = mutableMapOf<AccountId, Bound>()

    private class Bound(val spec: AccountSpec, val account: Account, val auth: AuthInfo)

    private val _calls = MutableStateFlow<List<EngineCall>>(emptyList())
    override val calls: StateFlow<List<EngineCall>> = _calls.asStateFlow()

    // Only touched on the core thread: the live Linphone call behind each id, and the calls this
    // side ended (Linphone does not say who ended a call).
    private val live = mutableMapOf<CallId, Call>()
    private val endedHere = mutableSetOf<CallId>()

    // Only touched on the core thread: the kind of device the system put each call's audio on.
    private val devices = mutableMapOf<CallId, AudioDeviceKind>()

    private data class Surfaces(
        val call: CallId,
        val remote: TextureView?,
        val preview: TextureView?,
    )

    // Only touched on the core thread.
    private var surfaces: Surfaces? = null

    private val _trace = MutableStateFlow<List<SipTraceEntry>>(emptyList())
    override val trace: StateFlow<List<SipTraceEntry>> = _trace.asStateFlow()
    private val traceIds = AtomicLong()

    // Read by whichever thread writes to Linphone's log.
    @Volatile private var capturing = false

    /** Every line of Linphone's log goes by here; the SIP messages among them are the trace. */
    private val logListener = LoggingServiceListener { _, _, _, message ->
        if (!capturing) return@LoggingServiceListener
        val traced = tracedMessageOf(message) ?: return@LoggingServiceListener
        val entry =
            SipTraceEntry(
                id = traceIds.incrementAndGet(),
                atMillis = System.currentTimeMillis(),
                outgoing = traced.outgoing,
                transport = traced.transport,
                peer = traced.peer,
                raw = traced.raw,
            )
        _trace.update { all -> (all + entry).takeLast(TRACE_SIZE) }
    }

    override suspend fun clearTrace() {
        _trace.value = emptyList()
    }

    override val messaging =
        LinphoneMessaging(
            dispatcher,
            core = { core },
            lines = { bound.mapValues { (_, it) -> LinphoneMessaging.Line(it.account, it.spec) } },
            registered = { _registrations.value[it] == RegistrationState.Registered },
            later = { block -> handler.post(block) },
        )

    private val coreListener =
        object : CoreListenerStub() {
            override fun onCallStateChanged(
                core: Core,
                call: Call,
                state: Call.State,
                message: String,
            ) = onCall(call, state)

            /** The video reports keep the sizes on screen current. */
            override fun onCallStatsUpdated(core: Core, call: Call, stats: CallStats) {
                if (stats.type != StreamType.Video) return
                val id = call.sonaId()
                if (id !in live) return
                val video = call.videoNow(call.state)
                updateCall(id) { if (this.video == video) this else copy(video = video) }
            }

            override fun onMessageReceived(core: Core, room: ChatRoom, message: ChatMessage) =
                messaging.onReceived(room, message)

            override fun onMessagesReceived(
                core: Core,
                room: ChatRoom,
                messages: Array<out ChatMessage>,
            ) = messages.forEach { messaging.onReceived(room, it) }
        }

    private val iterate =
        object : Runnable {
            override fun run() {
                core?.iterate() ?: return
                handler.postDelayed(this, ITERATE_MS)
            }
        }

    override suspend fun start(config: EngineConfig) =
        withContext(dispatcher) {
            if (core != null) return@withContext
            _state.value = EngineState.Starting
            try {
                this@LinphoneSipEngine.config = config
                capturing = config.captureTrace
                configurer.logging(config)
                Factory.instance().loggingService.addListener(logListener)
                val created = Factory.instance().createCore(null, null, context)
                coreVersion = created.version
                created.isAutoIterateEnabled = false
                created.isPushNotificationEnabled = false
                // Random local ports: a fixed 5060 collides with anything else on the phone.
                val transports = created.transports
                transports.udpPort = RANDOM_PORT
                transports.tcpPort = RANDOM_PORT
                transports.tlsPort = RANDOM_PORT
                created.transports = transports
                // Sona rings through its notification channel, so Do not disturb and the ringer
                // volume apply. Linphone keeps only the ringback heard while calling out.
                created.isNativeRingingEnabled = false
                created.ring = null
                configurer.apply(created, config)
                messaging.prepare(created)
                created.addListener(coreListener)
                created.start()
                cameraFor(created.videoDevicesList.toList(), CameraFacing.Front)?.let {
                    created.setVideoDevice(it)
                }
                core = created
                handler.post(iterate)
                _state.value = EngineState.Running
                reconcile(created)
            } catch (error: Throwable) {
                _state.value = EngineState.Failed(error.message ?: error.javaClass.simpleName)
            }
        }

    override suspend fun stop() =
        withContext(dispatcher) {
            val current = core ?: return@withContext
            handler.removeCallbacks(iterate)
            Factory.instance().loggingService.removeListener(logListener)
            bound.values.forEach { current.removeAccount(it.account) }
            bound.clear()
            current.clearAllAuthInfo()
            current.stop()
            core = null
            _registrations.update { all -> all.mapValues { RegistrationState.Unregistered } }
            _state.value = EngineState.Stopped
        }

    override suspend fun apply(config: EngineConfig) =
        withContext(dispatcher) {
            this@LinphoneSipEngine.config = config
            capturing = config.captureTrace
            core?.let { configurer.apply(it, config) } ?: Unit
        }

    /**
     * Linphone has one codec list and one DTMF way for the whole Core, and an account may have its
     * own: before a call is offered or answered, the Core is given what [account] wants, or the
     * app's when it says nothing. Runs on the core thread.
     */
    private fun useOptionsOf(core: Core, account: AccountId?) {
        val spec = account?.let { bound[it]?.spec }
        (spec?.codecs ?: config.codecs)?.let { configurer.codecs(core, it) }
        configurer.dtmf(core, spec?.dtmf ?: config.dtmf)
    }

    override suspend fun setAccounts(accounts: List<AccountSpec>) =
        withContext(dispatcher) {
            desired = accounts
            core?.let(::reconcile) ?: Unit
        }

    override suspend fun refreshRegistrations() =
        withContext(dispatcher) { core?.refreshRegisters() ?: Unit }

    override suspend fun dial(account: AccountId, to: SipAddress, video: Boolean): DialResult =
        withContext(dispatcher) {
            val current = core ?: return@withContext DialResult.Failed("The engine is not running")
            val from = bound[account]
            if (from == null || _registrations.value[account] != RegistrationState.Registered) {
                return@withContext DialResult.AccountUnavailable
            }
            val address =
                Factory.instance().createAddress(to.uri)
                    ?: return@withContext DialResult.Failed("Not a SIP address: $to")
            useOptionsOf(current, account)
            val params =
                current.createCallParams(null)
                    ?: return@withContext DialResult.Failed("No call parameters")
            params.account = from.account
            val withVideo = video && config.video.enabled
            params.isVideoEnabled = withVideo
            if (withVideo) {
                ensureCamera(current)
                params.videoDirection = MediaDirection.SendRecv
            }
            // Low-bandwidth mode turns video off: only for a call without it.
            params.isLowBandwidthEnabled = config.video.lowBandwidth && !withVideo
            val call =
                current.inviteAddressWithParams(address, params)
                    ?: return@withContext DialResult.Failed("Linphone refused to call $to")
            DialResult.Started(
                track(call, account, CallDirection.Outgoing, EngineCallState.Dialing),
            )
        }

    override suspend fun answer(call: CallId, video: Boolean) =
        onCall(call) {
            useOptionsOf(core, accountOf(call))
            val direction =
                answerDirectionOf(
                    enabled = config.video.enabled,
                    camera = video,
                    offered = remoteParams?.isVideoEnabled == true,
                    acceptRemote = config.video.acceptRemoteVideo,
                )
            val params = core.createCallParams(this) ?: return@onCall accept()
            params.isVideoEnabled = direction != null
            if (direction != null) params.videoDirection = direction
            if (direction == MediaDirection.SendRecv) ensureCamera(core)
            params.isLowBandwidthEnabled = config.video.lowBandwidth && direction == null
            acceptWithParams(params)
        }

    override suspend fun decline(call: CallId, busy: Boolean) =
        onCall(call) {
            endedHere += call
            decline(if (busy) Reason.Busy else Reason.Declined)
        }

    override suspend fun hangUp(call: CallId) =
        onCall(call) {
            endedHere += call
            terminate()
        }

    override suspend fun hold(call: CallId, onHold: Boolean) =
        onCall(call) { if (onHold) pause() else resume() }

    override suspend fun setMuted(call: CallId, muted: Boolean) =
        onCall(call) {
            microphoneMuted = muted
            updateCall(call) { copy(muted = muted) }
        }

    override suspend fun sendDtmf(call: CallId, digit: Char) =
        onCall(call) {
            // With two calls on two accounts, the way of the call the digit is for.
            val spec = accountOf(call)?.let { bound[it]?.spec }
            configurer.dtmf(core, spec?.dtmf ?: config.dtmf)

            sendDtmf(digit)
        }

    private fun accountOf(call: CallId): AccountId? =
        _calls.value.firstOrNull { it.id == call }?.account

    override suspend fun setAudioDevice(call: CallId, device: AudioDeviceKind) =
        withContext(dispatcher) {
            devices[call] = device
            live[call]?.let { follow(it, device) }
            Unit
        }

    /**
     * Moves [call]'s streams to the phone's device of [kind], when it has one. Linphone opens its
     * player and recorder on a device of its own choosing (the earpiece) and keeps them there:
     * without this the system's route changes and the sound does not. Found on a phone, where the
     * speaker button did everything but move the audio. Runs on the core thread.
     */
    private fun follow(call: Call, kind: AudioDeviceKind) {
        val all = core?.extendedAudioDevices?.toList() ?: return
        val output =
            pickDevice(
                all.filter { it.hasCapability(AudioDevice.Capabilities.CapabilityPlay) },
                kind.outputTypes(),
            ) {
                it.type
            }
        val input =
            pickDevice(
                all.filter { it.hasCapability(AudioDevice.Capabilities.CapabilityRecord) },
                kind.inputTypes(),
            ) {
                it.type
            }
        // Setting a device restarts its stream: only when it is another one.
        if (output != null && !output.isSame(call.outputAudioDevice)) {
            call.outputAudioDevice = output
        }
        if (input != null && !input.isSame(call.inputAudioDevice)) call.inputAudioDevice = input
    }

    private fun AudioDevice.isSame(other: AudioDevice?): Boolean =
        other != null && other.id == id && other.type == type

    /**
     * Camera off is `sendrecv`→`recvonly`: nothing is sent. Linphone refuses an update while
     * another is in progress; the call then keeps its directions, as [calls] goes on saying.
     */
    override suspend fun setVideo(call: CallId, send: Boolean, receive: Boolean) =
        onCall(call) {
            if (!config.video.enabled) return@onCall Unit
            val params = core.createCallParams(this) ?: return@onCall Unit
            val direction = videoDirectionOf(send, receive)
            params.isVideoEnabled = direction != null
            if (direction != null) {
                params.videoDirection = direction
                // Low-bandwidth mode would turn the video off again.
                params.isLowBandwidthEnabled = false
            }
            if (send) ensureCamera(core)
            update(params)
        }

    /** A `null` update applies the new camera without a re-INVITE, as Linphone's own app does. */
    override suspend fun switchCamera(call: CallId) =
        withContext(dispatcher) {
            val current = core ?: return@withContext
            val next =
                otherCamera(current.videoDevicesList.toList(), current.videoDevice)
                    ?: return@withContext
            current.setVideoDevice(next)
            live[call]?.let { linphoneCall ->
                linphoneCall.update(null)
                updateCall(call) { copy(video = linphoneCall.videoNow(linphoneCall.state)) }
            }
            Unit
        }

    override suspend fun setDeviceRotation(degrees: Int) =
        withContext(dispatcher) { core?.deviceRotation = degrees }

    /** Polled only while collected; ends with the call. */
    override fun statistics(call: CallId): Flow<CallStatistics> = flow {
        while (true) {
            val sample =
                withContext(dispatcher) {
                    val linphoneCall = live[call] ?: return@withContext null
                    callStatisticsOf(
                        params = linphoneCall.currentParams,
                        audio = linphoneCall.audioStats,
                        video = linphoneCall.videoStats,
                        transport = transportOf(accountOf(call)),
                    )
                } ?: break
            emit(sample)
            delay(STATISTICS_PERIOD)
        }
    }

    private fun transportOf(account: AccountId?) =
        account?.let { bound[it]?.spec }?.let { (it.outboundProxy ?: it.registrar).transport }

    /** Posted to the Core thread: the screen calls this from the main one. */
    override fun attach(call: CallId, remote: Any?, preview: Any?) {
        handler.post { show(call, remote as? TextureView, preview as? TextureView) }
    }

    private fun show(call: CallId, remote: TextureView?, preview: TextureView?) {
        val current = core ?: return
        val previous = surfaces
        // A screen letting go of a call no longer shown leaves the views of the one shown now.
        if (remote == null && preview == null && previous != null && previous.call != call) return
        if (previous != null && previous.call != call)
            live[previous.call]?.nativeVideoWindowId = null
        surfaces = if (remote == null && preview == null) null else Surfaces(call, remote, preview)
        live[call]?.nativeVideoWindowId = remote
        current.nativePreviewWindowId = preview
        refreshPreview()
    }

    /**
     * The preview runs while a view shows it, sent or not (a ringing video call shows it), and
     * sends nothing. Without a view it stops, to release the camera.
     */
    private fun refreshPreview() {
        val current = core ?: return
        val shown = surfaces
        val on =
            previewRuns(
                attached = shown?.preview != null,
                callGoing = shown != null && shown.call in live,
                cameraAllowed = cameraAllowed(),
            )
        if (on && !current.isVideoPreviewEnabled) ensureCamera(current)
        if (current.isVideoPreviewEnabled != on) current.isVideoPreviewEnabled = on
    }

    private fun cameraAllowed(): Boolean =
        context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun detachEnded(call: CallId) {
        if (surfaces?.call != call) return
        surfaces = null
        core?.let {
            it.nativePreviewWindowId = null
            it.isVideoPreviewEnabled = false
        }
    }

    /**
     * Linphone falls back to a still picture if it looked for cameras before the permission was
     * given.
     */
    private fun ensureCamera(core: Core) {
        if (cameraFacingOf(core.videoDevice) != null) return
        if (core.videoDevicesList.none { !it.startsWith(STATIC_IMAGE) }) core.reloadVideoDevices()
        cameraFor(core.videoDevicesList.toList(), CameraFacing.Front)?.let {
            core.setVideoDevice(it)
        }
    }

    private fun Call.videoNow(state: Call.State): CallVideo? {
        if (state.ringingHere()) return null
        val negotiated = state.negotiated()
        return callVideoOf(
            local = if (negotiated) currentParams else params,
            remote = if (negotiated) remoteParams else null,
            camera = cameraFacingOf(core?.videoDevice) ?: CameraFacing.Front,
        )
    }

    private fun Call.offersVideo(): Boolean =
        config.video.enabled && remoteParams?.isVideoEnabled == true

    /**
     * Video added by the other side is deferred, then taken receive-only, or declined if the
     * settings refuse it. Linphone answers any other update itself.
     */
    private fun onUpdatedByRemote(call: Call) {
        val remote = call.remoteParams ?: return
        if (!remote.isVideoEnabled || call.currentParams.isVideoEnabled) return
        if (!config.video.enabled || !config.video.acceptRemoteVideo) return
        call.deferUpdate()
        handler.post {
            if (call.state != Call.State.UpdatedByRemote) return@post
            val params = core?.createCallParams(call) ?: return@post
            params.isVideoEnabled = true
            params.videoDirection = MediaDirection.RecvOnly
            params.isLowBandwidthEnabled = false
            call.acceptUpdate(params)
        }
    }

    override suspend fun forget(call: CallId) =
        withContext(dispatcher) {
            detachEnded(call)
            live.remove(call)
            endedHere.remove(call)
            devices.remove(call)
            _calls.update { all -> all.filterNot { it.id == call } }
        }

    private suspend fun onCall(id: CallId, action: Call.() -> Any) =
        withContext(dispatcher) {
            live[id]?.action()
            Unit
        }

    /**
     * A Linphone call in a new state, as Sona sees it. Runs on the core thread, from the listener.
     */
    private fun onCall(call: Call, state: Call.State) {
        val id = call.sonaId()
        if (id !in live) {
            if (state != Call.State.IncomingReceived) return
            val account = accountOf(call)
            track(call, account, CallDirection.Incoming, EngineCallState.Incoming)
            core?.let { useOptionsOf(it, account) }
        }
        if (state == Call.State.UpdatedByRemote) onUpdatedByRemote(call)
        val error = call.errorInfo
        val mapped =
            callStateOf(
                state = state,
                code = error.protocolCode,
                phrase = error.phrase,
                protocol = error.protocol,
                sendFailed = error.reason == Reason.IOError,
                endedHere = id in endedHere,
                remote = call.remoteAddress.asStringUriOnly(),
            ) ?: return
        val ended = mapped is EngineCallState.Ended
        updateCall(id) {
            // An ending is reported once: Linphone goes through End, then Released.
            if (this.state is EngineCallState.Ended) this
            else if (ended) copy(state = mapped)
            else
                copy(
                    state = mapped,
                    media = call.mediaInfo() ?: media,
                    video = call.videoNow(state),
                )
        }
        if (ended) {
            detachEnded(id)
            live.remove(id)
        }
        if (state == Call.State.StreamsRunning) devices[id]?.let { follow(call, it) }
    }

    private fun track(
        call: Call,
        account: AccountId?,
        direction: CallDirection,
        state: EngineCallState,
    ): CallId {
        val id = call.sonaId()
        live[id] = call
        val remote = call.remoteAddress
        val engineCall =
            EngineCall(
                id = id,
                account = account,
                direction = direction,
                remote =
                    SipAddress(
                        user = remote.username,
                        host = remote.domain ?: "unknown",
                        port = remote.port.takeIf { it > 0 },
                    ),
                remoteName = remote.displayName?.takeIf { it.isNotBlank() },
                state = state,
                // A dialed call has what it asked for; a ringing one only its offer.
                video = call.videoNow(call.state),
                videoOffered = direction == CallDirection.Incoming && call.offersVideo(),
            )
        _calls.update { all -> all.filterNot { it.id == id } + engineCall }
        return id
    }

    /** Runs on the core thread: the preview of the shown call follows what it now sends. */
    private fun updateCall(id: CallId, change: EngineCall.() -> EngineCall) {
        _calls.update { all -> all.map { if (it.id == id) it.change() else it } }
        if (surfaces?.call == id) refreshPreview()
    }

    /** The account a call came to: the one Linphone matched, or the one with its address. */
    private fun accountOf(call: Call): AccountId? {
        val account = call.params.account
        bound.entries
            .firstOrNull { it.value.account == account }
            ?.let {
                return it.key
            }
        val to = call.toAddress
        return bound.entries
            .firstOrNull {
                it.value.spec.identity.user == to.username &&
                    it.value.spec.identity.host == to.domain
            }
            ?.key ?: bound.keys.firstOrNull()
    }

    /** The SIP Call-ID: stable for the call's whole life, and what a SIP trace shows. */
    private fun Call.sonaId(): CallId = CallId(callLog.callId ?: hashCode().toString())

    private fun Call.mediaInfo(): MediaInfo? {
        val params = currentParams
        val payload = params.usedAudioPayloadType ?: return null
        val encryption = params.mediaEncryption.takeIf { it != MediaEncryption.None }?.name
        return MediaInfo(payload.mimeType, payload.clockRate, encryption)
    }

    /** Makes the Core's accounts exactly [desired]: removes, replaces and adds. */
    private fun reconcile(core: Core) {
        val wanted = desired.associateBy { it.id }

        // Gone, or changed: a changed account is replaced whole, which re-registers it.
        bound.entries.toList().forEach { (id, current) ->
            if (wanted[id] != current.spec) {
                core.removeAccount(current.account)
                bound.remove(id)
            }
        }
        _registrations.update { all -> all.filterKeys { it in wanted } }

        // Credentials are rebuilt whole. Linphone keeps its own copy of each auth info, so removing
        // ours by object can miss it, and a replaced account would quietly keep the old password.
        core.clearAllAuthInfo()
        bound.values.forEach { core.addAuthInfo(it.auth) }

        for (spec in desired) {
            if (spec.id in bound) continue
            bind(core, spec)
        }
        core.defaultAccount = desired.firstOrNull { it.enabled }?.let { bound[it.id]?.account }
    }

    private fun bind(core: Core, spec: AccountSpec) {
        val factory = Factory.instance()
        val identity =
            factory.createAddress(spec.identity.uri)
                ?: return setRegistration(spec.id, invalid("Not a SIP address: ${spec.identity}"))
        spec.displayName?.let { identity.setDisplayName(it) }
        val server =
            factory.createAddress(spec.registrar.toSipUri())
                ?: return setRegistration(spec.id, invalid("Not a server: ${spec.registrar}"))
        server.transport = spec.registrar.transport.toLinphone()

        val params = core.createAccountParams()
        params.identityAddress = identity
        params.serverAddress = server
        params.isRegisterEnabled = spec.enabled
        params.expires = spec.expiry.inWholeSeconds.toInt()
        params.isPublishEnabled = false
        params.pushNotificationAllowed = false
        params.remotePushNotificationAllowed = false
        spec.outboundProxy?.let { proxy ->
            factory.createAddress(proxy.toSipUri())?.let { params.setRoutesAddresses(arrayOf(it)) }
        }

        // Username is the identity's user. The auth username, when different, is Linphone's
        // userid. No realm or domain: Asterisk's realm need not match the account's domain.
        val auth =
            factory.createAuthInfo(
                spec.identity.user.orEmpty(),
                spec.authUsername,
                spec.password.reveal(),
                null,
                null,
                null,
            )
        val account = core.createAccount(params)
        account.addListener(
            object : AccountListenerStub() {
                override fun onRegistrationStateChanged(
                    account: Account,
                    state: org.linphone.core.RegistrationState,
                    message: String,
                ) {
                    val error = account.errorInfo
                    setRegistration(
                        spec.id,
                        registrationOf(
                            state = state,
                            code = error.protocolCode,
                            phrase = error.phrase,
                            sendFailed = error.reason == Reason.IOError,
                            previous = _registrations.value[spec.id],
                            networkReachable = core.isNetworkReachable,
                            registrar = spec.registrar,
                        ),
                    )
                }
            },
        )
        core.addAuthInfo(auth)
        core.addAccount(account)
        bound[spec.id] = Bound(spec, account, auth)
        setRegistration(
            spec.id,
            if (spec.enabled) RegistrationState.Registering else RegistrationState.Unregistered,
        )
    }

    private fun invalid(detail: String) = RegistrationState.Failed(status = null, detail = detail)

    private fun setRegistration(id: AccountId, state: RegistrationState) {
        _registrations.update { it + (id to state) }
    }

    private companion object {
        const val ITERATE_MS = 20L
        const val RANDOM_PORT = -1
        const val TRACE_SIZE = 500
        const val STATIC_IMAGE = "StaticImage"
        val STATISTICS_PERIOD = 1.seconds
    }
}
