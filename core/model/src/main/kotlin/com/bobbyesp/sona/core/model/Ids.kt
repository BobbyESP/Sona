/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.model

/** A SIP account, as Sona stores it. Stable across edits of the account's fields. */
@JvmInline
value class AccountId(val value: String) {
    override fun toString(): String = value
}

/** A call, from the moment it is dialed or rings until it is in the history. */
@JvmInline
value class CallId(val value: String) {
    override fun toString(): String = value
}

/**
 * A value that must never be shown: a SIP password. Its [toString] hides it, so it cannot reach a
 * log, a trace or a crash report by accident; only [reveal] gives it, and only the engine calls it.
 */
@JvmInline
value class Secret(private val value: String) {
    fun reveal(): String = value

    val isEmpty: Boolean
        get() = value.isEmpty()

    override fun toString(): String = "Secret(••••)"
}
