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
 * Sends bodies through the engine, retrying with back-off the failures that may pass. What a
 * failure means for a message is the caller's to decide.
 */
internal class Courier(private val engine: SipEngine, private val scope: CoroutineScope) {

    data class Policy(val retries: Int, val waitForAccount: Boolean)

    suspend fun send(account: AccountId, to: SipAddress, body: String): MessageSendResult =
        engine.messaging.send(account, to, body)

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

    /** Sends [envelope] without waiting for the answer. */
    fun later(account: AccountId, to: SipAddress, envelope: Envelope, policy: Policy) {
        scope.launch { send(account, to, EnvelopeCodec.encode(envelope), policy) }
    }

    /**
     * After a start or a network change the account registers within seconds; a message sent before
     * would fail.
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
