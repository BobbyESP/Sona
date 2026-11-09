/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.accounts.domain

import com.bobbyesp.sona.sip.api.RegistrationState
import com.bobbyesp.sona.sip.api.SipStatus

/**
 * What a failed registration means, decided here once so every screen says the same. The screen
 * turns each into a sentence; the [status], when there is one, is the technical line under it
 * (*Show SIP details*).
 */
sealed interface RegistrationProblem {
    val status: SipStatus?

    /** `401`, `407`: the server does not accept this username and password. */
    data class WrongCredentials(override val status: SipStatus) : RegistrationProblem

    /** `403`: the server refuses this account, whatever the password. */
    data class Refused(override val status: SipStatus) : RegistrationProblem

    /** `404`: there is no such extension on the server. */
    data class UnknownExtension(override val status: SipStatus) : RegistrationProblem

    /** No answer at all: the server is down, unreachable, or the port or transport is wrong. */
    data class NoAnswer(val detail: String?) : RegistrationProblem {
        override val status: SipStatus? = null
    }

    /** `5xx`: the server answered that it cannot. */
    data class ServerError(override val status: SipStatus) : RegistrationProblem

    data class Other(override val status: SipStatus) : RegistrationProblem

    companion object {
        fun of(failed: RegistrationState.Failed): RegistrationProblem {
            val status = failed.status ?: return NoAnswer(failed.detail)
            return when (status.code) {
                401,
                407 -> WrongCredentials(status)
                403 -> Refused(status)
                404 -> UnknownExtension(status)
                408 -> NoAnswer(failed.detail ?: status.toString())
                in 500..599 -> ServerError(status)
                else -> Other(status)
            }
        }
    }
}
