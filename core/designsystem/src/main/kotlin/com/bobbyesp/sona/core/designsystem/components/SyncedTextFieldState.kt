/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.designsystem.components

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow

/**
 * A [TextFieldState] for a text a ViewModel owns as a string: what is typed goes to
 * [onValueChange], and a [value] the ViewModel changes by itself (a draft cleared after sending, a
 * number filtered, a form loaded) goes into the field. A [value] that echoes something typed, even
 * an older keystroke still in flight, leaves the field alone, so the cursor is not disturbed.
 */
@Composable
fun rememberSyncedTextFieldState(value: String, onValueChange: (String) -> Unit): TextFieldState {
    val state = rememberTextFieldState(value)
    val onChange by rememberUpdatedState(onValueChange)
    val sync = remember { Sync(value) }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            .collect { text ->
                if (text != sync.last) {
                    sync.last = text
                    sync.sent.addLast(text)
                    onChange(text)
                }
            }
    }
    LaunchedEffect(value) {
        val echo = sync.sent.indexOf(value)
        if (echo >= 0) {
            repeat(echo + 1) { sync.sent.removeFirst() }
        } else if (value != state.text.toString()) {
            sync.sent.clear()
            sync.last = value
            state.setTextAndPlaceCursorAtEnd(value)
        }
    }
    return state
}

/** What the field last had, and the texts sent to the ViewModel that it has not echoed yet. */
private class Sync(var last: String) {
    val sent = ArrayDeque<String>()
}
