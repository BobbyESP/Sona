/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.call

import com.bobbyesp.sona.accounts.domain.Account
import com.bobbyesp.sona.accounts.domain.AccountsRepository
import com.bobbyesp.sona.calling.domain.Call
import com.bobbyesp.sona.calling.domain.CallCoordinator
import com.bobbyesp.sona.calling.domain.CallState
import com.bobbyesp.sona.core.model.CallId
import com.bobbyesp.sona.core.model.settings.AudioSettings
import com.bobbyesp.sona.core.model.settings.SettingsStore
import com.bobbyesp.sona.core.ui.viewmodel.BaseViewModel
import com.bobbyesp.sona.telecom.api.AudioRoute
import kotlinx.coroutines.flow.combine

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
)

internal sealed interface CallIntent {
    data object Answer : CallIntent

    data object Decline : CallIntent

    data object HangUp : CallIntent

    data object ToggleMute : CallIntent

    data object ToggleHold : CallIntent

    data object ToggleKeypad : CallIntent

    data class Dtmf(val digit: Char) : CallIntent

    data class Route(val route: AudioRoute) : CallIntent

    /** Puts the shown call on hold and resumes [CallUiState.other]. */
    data object Swap : CallIntent
}

/**
 * The call screen's state, from [CallCoordinator]: the screen never owns a call, it sends the
 * coordinator intents. When the last call is gone (an ended one stays a moment, to say how it
 * ended), [CallUiState.closed] tells the screen to close.
 */
internal class CallViewModel(
    private val coordinator: CallCoordinator,
    accounts: AccountsRepository,
    settings: SettingsStore,
) : BaseViewModel<CallIntent, CallUiState, Nothing>(CallUiState()) {

    init {
        launch {
            settings.observe(AudioSettings.DialpadTones).collect {
                setState { copy(dialpadTones = it) }
            }
        }
        launch {
            combine(coordinator.calls, accounts.accounts) { calls, all -> calls to all }
                .collect { (calls, all) ->
                    if (calls.isEmpty()) {
                        setState { copy(closed = true) }
                        return@collect
                    }
                    val shown = calls.focus(currentState.call?.id)
                    setState {
                        copy(
                            call = shown,
                            other = calls.firstOrNull { it.id != shown.id && !it.isOver },
                            account = all.firstOrNull { it.id == shown.account },
                            dtmf = if (shown.id == call?.id) dtmf else "",
                        )
                    }
                }
        }
    }

    override fun onHandleIntent(intent: CallIntent) {
        val call = currentState.call ?: return
        when (intent) {
            CallIntent.Answer -> coordinator.answer(call.id)
            CallIntent.Decline -> coordinator.decline(call.id)
            CallIntent.HangUp -> coordinator.hangUp(call.id)
            CallIntent.ToggleMute -> coordinator.setMuted(call.id, !call.muted)
            CallIntent.ToggleHold -> coordinator.setOnHold(call.id, call.state != CallState.OnHold)
            CallIntent.ToggleKeypad -> setState { copy(keypadOpen = !keypadOpen) }
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
        }
    }

    private companion object {
        const val MAX_DTMF_SHOWN = 24
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
