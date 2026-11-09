/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.dialer

import androidx.lifecycle.SavedStateHandle
import com.bobbyesp.sona.accounts.domain.AccountsRepository
import com.bobbyesp.sona.accounts.domain.defaultAccount
import com.bobbyesp.sona.calling.domain.AddressResolution
import com.bobbyesp.sona.calling.domain.AddressResolver
import com.bobbyesp.sona.calling.domain.CallCoordinator
import com.bobbyesp.sona.calling.domain.DialOutcome
import com.bobbyesp.sona.core.ui.viewmodel.BaseViewModel

internal data class KeypadState(
    val input: String = "",
    /** The domain of the account calls are made from; `null` while there is no account. */
    val domain: String? = null,
    /** Why the last call could not be made, until the input changes. */
    val problem: DialOutcome? = null,
) {
    /** The address [input] would call, shown under it as it is typed. */
    val preview: String?
        get() =
            domain
                ?.let { AddressResolver.resolve(input, it) }
                ?.let { (it as? AddressResolution.Resolved)?.address?.uri }

    val canCall: Boolean
        get() = input.isNotEmpty()
}

internal sealed interface KeypadIntent {
    data class Type(val key: Char) : KeypadIntent

    data object Delete : KeypadIntent

    data object Clear : KeypadIntent

    /** Calls what was typed, from the default account. */
    data object Call : KeypadIntent
}

internal sealed interface KeypadEffect {
    data object OpenCall : KeypadEffect
}

/**
 * The keypad's field and its call button. What was typed survives process death in the entry's
 * [SavedStateHandle]. Calling goes through [CallCoordinator]: when nothing could be dialed, the
 * reason stays under the field until the input changes.
 */
internal class KeypadViewModel(
    private val savedState: SavedStateHandle,
    private val coordinator: CallCoordinator,
    accounts: AccountsRepository,
) :
    BaseViewModel<KeypadIntent, KeypadState, KeypadEffect>(
        KeypadState(input = savedState[INPUT_KEY] ?: ""),
    ) {

    init {
        launch { accounts.defaultAccount().collect { setState { copy(domain = it?.domain) } } }
    }

    override fun onHandleIntent(intent: KeypadIntent) {
        val input = currentState.input
        val next =
            when (intent) {
                is KeypadIntent.Type -> DialInput.type(input, intent.key)
                KeypadIntent.Delete -> DialInput.delete(input)
                KeypadIntent.Clear -> ""
                KeypadIntent.Call -> {
                    call(input)
                    return
                }
            }
        if (next == input) return
        setState { copy(input = next, problem = null) }
        savedState[INPUT_KEY] = next
    }

    private fun call(input: String) {
        if (input.isEmpty()) return
        launch {
            when (val outcome = coordinator.dial(input)) {
                is DialOutcome.Started -> {
                    setState { copy(problem = null) }
                    sendEffect(KeypadEffect.OpenCall)
                }
                else -> setState { copy(problem = outcome) }
            }
        }
    }

    private companion object {
        const val INPUT_KEY = "input"
    }
}
