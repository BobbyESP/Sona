/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.domain

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.messaging.domain.envelope.Envelope
import com.bobbyesp.sona.messaging.domain.envelope.EnvelopeCodec
import com.bobbyesp.sona.sip.api.MessageSendResult
import com.bobbyesp.sona.sip.api.RegistrationState
import com.bobbyesp.sona.sip.api.SipEngine
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Takes bodies to the engine, and tries again what deserves it: a server that did not answer, or
 * said it was busy (`408`, `503`), is asked again after a while, each time longer; a refusal for
 * what the message is (`404`, `415`) is final. What the domain decides about a failure (fail a
 * text, keep a transfer waiting) is the caller's.
 */
internal class Courier(private val engine: SipEngine, private val scope: CoroutineScope) {

    /** How many times to try again, and whether an account that is not registered is waited for. */
    data class Policy(val retries: Int, val waitForAccount: Boolean)

    /** Sends once. */
    suspend fun send(account: AccountId, to: SipAddress, body: String): MessageSendResult =
        engine.messaging.send(account, to, body)

    /** Sends, and tries again after a back-off while the failure is one that may pass. */
    suspend fun send(
        account: AccountId,
        to: SipAddress,
        body: String,
        policy: Policy,
    ): MessageSendResult {
        var attempt = 0
        while (true) {
            val result = send(account, to, body)
            val again =
                result.isTransient &&
                    attempt < policy.retries &&
                    (result != MessageSendResult.AccountUnavailable || policy.waitForAccount)
            if (!again) return result
            if (result == MessageSendResult.AccountUnavailable) {
                awaitRegistered(account, backoff(attempt) * 4)
            } else {
                delay(backoff(attempt))
            }
            attempt++
        }
    }

    /** Sends [envelope] without waiting for the answer: what is lost is lost after [policy]. */
    fun later(account: AccountId, to: SipAddress, envelope: Envelope, policy: Policy) {
        scope.launch { send(account, to, EnvelopeCodec.encode(envelope), policy) }
    }

    /**
     * Waits, at most [timeout], for [account] to be registered. Right after the app starts, or
     * after the network changed, it registers within seconds; a message sent before would fail.
     */
    suspend fun awaitRegistered(account: AccountId, timeout: Duration): Boolean =
        withTimeoutOrNull(timeout) {
            engine.registrations.first { it[account] == RegistrationState.Registered }
        } != null

    /** Whether [account] is on its way to being registered: worth a short wait. */
    fun isRegistering(account: AccountId): Boolean =
        engine.registrations.value[account] == RegistrationState.Registering

    companion object {
        /** 2 s, 4 s, 8 s… up to 30 s. */
        fun backoff(attempt: Int): Duration =
            (2.seconds * (1 shl attempt.coerceIn(0, 4))).coerceAtMost(30.seconds)

        /** Texts and offers: four more tries, about half a minute; a missing account fails them. */
        val Texts = Policy(retries = 4, waitForAccount = false)

        /** What a transfer cannot do without (accept, done, cancel): the account is waited for. */
        val Important = Policy(retries = 5, waitForAccount = true)

        /** Receipts: a couple of tries. */
        val Receipts = Policy(retries = 2, waitForAccount = true)

        /** Typing, progress, hello: once; the next one says the same. */
        val Once = Policy(retries = 0, waitForAccount = false)
    }
}
