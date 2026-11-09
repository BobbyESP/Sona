/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.dialer

import androidx.lifecycle.SavedStateHandle
import com.bobbyesp.sona.core.ui.viewmodel.BaseViewModel

internal data class KeypadState(val input: String = "")

internal sealed interface KeypadIntent {
    data class Type(val key: Char) : KeypadIntent

    data object Delete : KeypadIntent

    data object Clear : KeypadIntent
}

/**
 * The keypad's field. What was typed survives process death in the entry's [SavedStateHandle]: a
 * number half typed when the system reclaimed the app is still there when the user comes back.
 */
internal class KeypadViewModel(private val savedState: SavedStateHandle) :
    BaseViewModel<KeypadIntent, KeypadState, Nothing>(
        KeypadState(input = savedState[INPUT_KEY] ?: ""),
    ) {

    override fun onHandleIntent(intent: KeypadIntent) {
        val input = currentState.input
        val next =
            when (intent) {
                is KeypadIntent.Type -> DialInput.type(input, intent.key)
                KeypadIntent.Delete -> DialInput.delete(input)
                KeypadIntent.Clear -> ""
            }
        if (next == input) return
        setState { copy(input = next) }
        savedState[INPUT_KEY] = next
    }

    private companion object {
        const val INPUT_KEY = "input"
    }
}
