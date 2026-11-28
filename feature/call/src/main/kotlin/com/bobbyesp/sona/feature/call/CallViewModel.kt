/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.call

import com.bobbyesp.sona.accounts.domain.Account
import com.bobbyesp.sona.accounts.domain.AccountsRepository
import com.bobbyesp.sona.calling.domain.Call
import com.bobbyesp.sona.calling.domain.CallCoordinator
import com.bobbyesp.sona.calling.domain.CallQuality
import com.bobbyesp.sona.calling.domain.CallState
import com.bobbyesp.sona.core.model.CallId
import com.bobbyesp.sona.core.model.settings.AudioSettings
import com.bobbyesp.sona.core.model.settings.SettingsStore
import com.bobbyesp.sona.core.model.settings.VideoSettings
import com.bobbyesp.sona.core.ui.viewmodel.BaseViewModel
import com.bobbyesp.sona.feature.call.video.FrozenVideoWatch
import com.bobbyesp.sona.feature.call.video.StatsHistory
import com.bobbyesp.sona.feature.call.video.StatsSeries
import com.bobbyesp.sona.feature.call.video.WeakConnectionWatch
import com.bobbyesp.sona.feature.call.video.remoteTurnedOnVideo
import com.bobbyesp.sona.sip.api.CallStatistics
import com.bobbyesp.sona.sip.api.Capability
import com.bobbyesp.sona.telecom.api.AudioRoute
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

internal data class CallUiState(
    /** The call on screen: one ringing first, then the active one, then the latest. */
    val call: Call? = null,
    /** Another call in progress, held while [call] is shown (call waiting). */
    val other: Call? = null,
    val account: Account? = null,
    val keypadOpen: Boolean = false,
    /** Whether the keypad sounds a tone under the finger. */
    val dialpadTones: Boolean = true,
    /** The digits sent on this screen, shown above the keypad. */
    val dtmf: String = "",
    /**
     * No call is left: the screen closes. State, not an effect: a screen that opens after the last
     * call ended must still close, and an effect sent before it listened would be lost.
     */
    val closed: Boolean = false,
    val videoAvailable: Boolean = false,
    val quality: CallQuality? = null,
    /** In a video call they fade after a few seconds; a tap brings them back. */
    val controlsVisible: Boolean = true,
    val moreOpen: Boolean = false,
    /** Only while open are [statistics] and [history] updated. */
    val statisticsOpen: Boolean = false,
    val statistics: CallStatistics? = null,
    val history: StatsSeries = StatsSeries(),
    /** "Weak connection: video lowered", with *Turn off video*. */
    val weakConnection: Boolean = false,
    /** "Alice turned on video", with *Turn on camera*. */
    val remoteTurnedOnVideo: Boolean = false,
    /** The other side's picture froze: the last frame dims under "Video paused". */
    val videoPaused: Boolean = false,
) {
    /** In an answered video call, while nothing the user opened is on screen. */
    val controlsAutoHide: Boolean
        get() =
            call?.isVideo == true &&
                call.state == CallState.Active &&
                !moreOpen &&
                !keypadOpen &&
                !statisticsOpen
}

internal sealed interface CallIntent {
    /** Answers; with an offer of video, *Answer audio only* (the other side is still seen). */
    data object Answer : CallIntent

    data object AnswerWithVideo : CallIntent

    data object Decline : CallIntent

    data object HangUp : CallIntent

    data object ToggleMute : CallIntent

    data object ToggleHold : CallIntent

    data object ToggleKeypad : CallIntent

    data class Dtmf(val digit: Char) : CallIntent

    data class Route(val route: AudioRoute) : CallIntent

    /** Puts the shown call on hold and resumes [CallUiState.other]. */
    data object Swap : CallIntent

    /** This side's camera on or off; on, in an audio call, adds video. */
    data class Camera(val on: Boolean) : CallIntent

    data object SwitchCamera : CallIntent

    data object TurnOffVideo : CallIntent

    data object DismissBanner : CallIntent

    data object ToggleMore : CallIntent

    data object DismissMore : CallIntent

    data object ToggleStatistics : CallIntent

    /** A tap on the picture: shows the controls, or hides them when shown. */
    data object TapScreen : CallIntent

    data class Rotation(val degrees: Int) : CallIntent

    /** Statistics are measured only while the screen is shown. */
    data class Shown(val shown: Boolean) : CallIntent
}

/**
 * The call screen's state, from [CallCoordinator]: the screen never owns a call, it sends the
 * coordinator intents. When the last call is gone (an ended one stays a moment, to say how it
 * ended), [CallUiState.closed] tells the screen to close.
 *
 * While the screen is shown, reads the call's statistics for the quality indicator, the weak
 * connection and frozen video watches, and the panel. The panel's numbers reach the state only
 * while it is open, so a closed one recomposes nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class CallViewModel(
    private val coordinator: CallCoordinator,
    accounts: AccountsRepository,
    settings: SettingsStore,
    capabilities: Set<Capability>,
    private val controlsFadeAfter: Duration = CONTROLS_FADE_AFTER,
    private val bannerLasts: Duration = BANNER_LASTS,
) : BaseViewModel<CallIntent, CallUiState, Nothing>(CallUiState()) {

    private val shown = MutableStateFlow(false)
    private val samples = StatsHistory()
    private var weakWatch = WeakConnectionWatch()
    private var frozenWatch = FrozenVideoWatch()
    private var weakDismissed = false
    private var lastStatistics: CallStatistics? = null
    private var rotation: Int? = null
    private var hideJob: Job? = null
    private var bannerJob: Job? = null

    init {
        launch {
            settings.observe(AudioSettings.DialpadTones).collect {
                setState { copy(dialpadTones = it) }
            }
        }
        launch {
            settings.observe(VideoSettings.Enabled).collect { enabled ->
                setState {
                    copy(videoAvailable = enabled && Capability.VideoCalls in capabilities)
                }
            }
        }
        launch {
            combine(coordinator.calls, accounts.accounts) { calls, all -> calls to all }
                .collect { (calls, all) ->
                    if (calls.isEmpty()) {
                        setState { copy(closed = true) }
                        return@collect
                    }
                    val before = currentState.call
                    val autoHid = currentState.controlsAutoHide
                    val shown = calls.focus(before?.id)
                    setState {
                        copy(
                            call = shown,
                            other = calls.firstOrNull { it.id != shown.id && !it.isOver },
                            account = all.firstOrNull { it.id == shown.account },
                            dtmf = if (shown.id == call?.id) dtmf else "",
                        )
                    }
                    followVideo(before, shown, autoHid)
                }
        }
        launch {
            combine(
                    state.map { it.call?.takeUnless(Call::isOver)?.id }.distinctUntilChanged(),
                    shown,
                ) { id, visible ->
                    id.takeIf { visible }
                }
                .distinctUntilChanged()
                .flatMapLatest { id ->
                    if (id == null) emptyFlow()
                    else coordinator.statistics(id).onStart { startMeasuring() }
                }
                .collect(::onStatistics)
        }
    }

    override fun onHandleIntent(intent: CallIntent) {
        when (intent) {
            is CallIntent.Rotation -> return rotate(intent.degrees)
            is CallIntent.Shown -> {
                shown.value = intent.shown
                return
            }
            CallIntent.TapScreen -> return tapScreen()
            else -> Unit
        }
        val call = currentState.call ?: return
        when (intent) {
            CallIntent.Answer -> coordinator.answer(call.id)
            CallIntent.AnswerWithVideo -> coordinator.answer(call.id, video = true)
            CallIntent.Decline -> coordinator.decline(call.id)
            CallIntent.HangUp -> coordinator.hangUp(call.id)
            CallIntent.ToggleMute -> coordinator.setMuted(call.id, !call.muted)
            CallIntent.ToggleHold -> {
                coordinator.setOnHold(call.id, call.state != CallState.OnHold)
                setState { copy(moreOpen = false) }
            }
            CallIntent.ToggleKeypad ->
                setState {
                    copy(keypadOpen = !keypadOpen, statisticsOpen = false, moreOpen = false)
                }
            is CallIntent.Dtmf -> {
                coordinator.sendDtmf(call.id, intent.digit)
                setState { copy(dtmf = (dtmf + intent.digit).takeLast(MAX_DTMF_SHOWN)) }
            }
            is CallIntent.Route -> coordinator.selectRoute(call.id, intent.route)
            CallIntent.Swap -> {
                val other = currentState.other ?: return
                coordinator.setOnHold(call.id, true)
                coordinator.setOnHold(other.id, false)
                setState { copy(call = other, other = call, dtmf = "") }
            }
            is CallIntent.Camera -> {
                coordinator.setCameraOn(call.id, intent.on)
                if (intent.on) dismissRemoteVideoBanner()
            }
            CallIntent.SwitchCamera -> coordinator.switchCamera(call.id)
            CallIntent.TurnOffVideo -> {
                coordinator.setVideoEnabled(call.id, false)
                weakDismissed = true
                setState { copy(weakConnection = false) }
            }
            CallIntent.DismissBanner -> {
                if (currentState.weakConnection) weakDismissed = true
                dismissRemoteVideoBanner()
                setState { copy(weakConnection = false) }
            }
            CallIntent.ToggleMore -> setState { copy(moreOpen = !moreOpen) }
            CallIntent.DismissMore -> setState { copy(moreOpen = false) }
            CallIntent.ToggleStatistics ->
                setState {
                    val open = !statisticsOpen
                    copy(
                        statisticsOpen = open,
                        keypadOpen = false,
                        moreOpen = false,
                        statistics = if (open) lastStatistics else null,
                        history = if (open) samples.snapshot() else StatsSeries(),
                    )
                }
            is CallIntent.Rotation,
            is CallIntent.Shown,
            CallIntent.TapScreen -> Unit
        }
        keepControls()
    }

    private fun keepControls() {
        hideJob?.cancel()
        if (!currentState.controlsVisible) setState { copy(controlsVisible = true) }
        if (currentState.controlsAutoHide) {
            hideJob = launch {
                delay(controlsFadeAfter)
                if (currentState.controlsAutoHide) setState { copy(controlsVisible = false) }
            }
        }
    }

    private fun tapScreen() {
        if (currentState.controlsVisible && currentState.controlsAutoHide) {
            hideJob?.cancel()
            setState { copy(controlsVisible = false, moreOpen = false) }
        } else {
            keepControls()
        }
    }

    private fun rotate(degrees: Int) {
        if (degrees == rotation) return
        rotation = degrees
        coordinator.setDeviceRotation(degrees)
    }

    private fun followVideo(before: Call?, now: Call, autoHid: Boolean) {
        if (remoteTurnedOnVideo(before, now)) {
            setState { copy(remoteTurnedOnVideo = true) }
            bannerJob?.cancel()
            bannerJob = launch {
                delay(bannerLasts)
                setState { copy(remoteTurnedOnVideo = false) }
            }
        } else if (currentState.remoteTurnedOnVideo && (now.cameraOn || !now.showsRemoteVideo)) {
            dismissRemoteVideoBanner()
        }
        if (!now.isVideo) {
            setState { copy(weakConnection = false, videoPaused = false) }
        }
        if (autoHid != currentState.controlsAutoHide) keepControls()
    }

    private fun dismissRemoteVideoBanner() {
        bannerJob?.cancel()
        setState { copy(remoteTurnedOnVideo = false) }
    }

    private fun startMeasuring() {
        samples.clear()
        weakWatch = WeakConnectionWatch()
        frozenWatch = FrozenVideoWatch()
        weakDismissed = false
        lastStatistics = null
    }

    private fun onStatistics(statistics: CallStatistics) {
        val call = currentState.call ?: return
        lastStatistics = statistics
        samples.add(statistics)
        val quality = CallQuality.of(statistics)
        val weak = weakWatch.update(quality, call.isVideo)
        if (!weak) weakDismissed = false
        val paused =
            frozenWatch.update(call.showsRemoteVideo, statistics.video?.frames?.receivedFps)
        setState {
            copy(
                quality = quality ?: this.quality,
                weakConnection = weak && !weakDismissed,
                videoPaused = paused,
                statistics = if (statisticsOpen) statistics else null,
                history = if (statisticsOpen) samples.snapshot() else this.history,
            )
        }
    }

    private companion object {
        const val MAX_DTMF_SHOWN = 24
        val CONTROLS_FADE_AFTER = 4.seconds
        val BANNER_LASTS = 10.seconds
    }
}

/**
 * The call to show: a ringing one always comes first (it needs an answer); otherwise the one
 * already shown, while it lasts; otherwise the active one, then the latest.
 */
internal fun List<Call>.focus(current: CallId?): Call =
    firstOrNull { it.state == CallState.Incoming }
        ?: firstOrNull { it.id == current }
        ?: firstOrNull { it.state == CallState.Active }
        ?: last()
