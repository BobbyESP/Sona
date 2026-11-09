/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.ui.events

/** How a message reads: neutral, a confirmation, or a failure. */
enum class MessageKind {
    Normal,
    Success,
    Error,
}

/** Something to tell the user, kept until there is someone to tell (see `BaseViewModel`). */
sealed interface UiEvent {
    data class ShowMessage(val message: String, val kind: MessageKind = MessageKind.Normal) :
        UiEvent
}
