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
import com.bobbyesp.sona.core.model.settings.AudioSettings
import com.bobbyesp.sona.core.model.settings.SettingsStore
import com.bobbyesp.sona.core.ui.viewmodel.BaseViewModel
import com.bobbyesp.sona.directory.domain.Directory
import com.bobbyesp.sona.directory.domain.Suggestion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

internal data class KeypadState(
    val input: String = "",
    /** The domain of the account calls are made from; `null` while there is no account. */
    val domain: String? = null,
    /** Why the last call could not be made, until the input changes. */
    val problem: DialOutcome? = null,
    /** People and numbers the input could be, from contacts and recent calls. */
    val suggestions: List<Suggestion> = emptyList(),
    /** The number a long press on 1 calls: the default account's voicemail. */
    val voicemail: String? = null,
    /** Whether a key sounds its tone under the finger. */
    val dialpadTones: Boolean = true,
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

    /** The field as a keyboard left it: kept as far as the field's rules allow. */
    data class Edit(val text: String) : KeypadIntent

    /** Text from the clipboard, which replaces what was typed. */
    data class Paste(val text: String) : KeypadIntent

    /** A suggestion was chosen: its number replaces what was typed. */
    data class Pick(val number: String) : KeypadIntent

    /** Calls what was typed, from the default account. */
    data object Call : KeypadIntent

    /** Calls a suggestion at once; it stays in the field, as if typed and called. */
    data class CallNumber(val number: String) : KeypadIntent

    /** The long press on 1: calls the default account's voicemail. */
    data object Voicemail : KeypadIntent
}

/** Whether [this] starts a call, which needs the microphone asked for first. */
internal val KeypadIntent.startsCall: Boolean
    get() =
        this == KeypadIntent.Call ||
            this == KeypadIntent.Voicemail ||
            this is KeypadIntent.CallNumber

internal sealed interface KeypadEffect {
    data object OpenCall : KeypadEffect
}

/**
 * The keypad's field and its call button. What was typed survives process death in the entry's
 * [SavedStateHandle]. Calling goes through [CallCoordinator]: when nothing could be dialed, the
 * reason stays under the field until the input changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class KeypadViewModel(
    private val savedState: SavedStateHandle,
    private val coordinator: CallCoordinator,
    directory: Directory,
    accounts: AccountsRepository,
    settings: SettingsStore,
) :
    BaseViewModel<KeypadIntent, KeypadState, KeypadEffect>(
        KeypadState(input = savedState[INPUT_KEY] ?: ""),
    ) {

    private val typed = MutableStateFlow(savedState.get<String>(INPUT_KEY) ?: "")

    init {
        launch {
            settings.observe(AudioSettings.DialpadTones).collect {
                setState { copy(dialpadTones = it) }
            }
        }
        launch {
            typed
                .flatMapLatest { directory.suggest(it) }
                .collect { setState { copy(suggestions = it) } }
        }
        launch {
            accounts.defaultAccount().collect { account ->
                setState {
                    copy(
                        domain = account?.domain,
                        voicemail = account?.voicemailNumber?.takeIf { it.isNotBlank() },
                    )
                }
            }
        }
    }

    override fun onHandleIntent(intent: KeypadIntent) {
        val input = currentState.input
        val next =
            when (intent) {
                is KeypadIntent.Type -> DialInput.type(input, intent.key)
                KeypadIntent.Delete -> DialInput.delete(input)
                KeypadIntent.Clear -> ""
                is KeypadIntent.Edit -> DialInput.typed(intent.text)
                is KeypadIntent.Paste -> DialInput.clean(intent.text).ifEmpty { input }
                is KeypadIntent.Pick -> intent.number
                is KeypadIntent.CallNumber -> intent.number
                KeypadIntent.Call -> {
                    call(input)
                    return
                }
                KeypadIntent.Voicemail -> {
                    currentState.voicemail?.let(::call)
                    return
                }
            }
        if (next != input) {
            setState { copy(input = next, problem = null) }
            savedState[INPUT_KEY] = next
            typed.value = next
        }
        if (intent is KeypadIntent.CallNumber) call(next)
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
