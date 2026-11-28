/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import com.bobbyesp.sona.messaging.domain.FailureReason
import com.bobbyesp.sona.messaging.domain.MessageFailure
import com.bobbyesp.sona.messaging.domain.SendOutcome
import com.bobbyesp.sona.messaging.domain.envelope.EnvelopeCodec

/**
 * A sentence to be read: a string resource and what fills it. Pure, so what each case says is
 * tested without a screen.
 */
@Immutable
data class Sentence(@param:StringRes @get:StringRes val res: Int, val args: List<Any> = emptyList())

@Composable fun Sentence.text(): String = stringResource(res, *args.toTypedArray())

/**
 * What a failure is about, for its sentence to name it.
 *
 * @property account The account's label.
 * @property peer The user (`205`), or the whole address off the account's domain.
 * @property server The account's server as people read it.
 */
@Immutable data class FailureSubject(val account: String, val peer: String, val server: String)

/** Why it was not sent, as a sentence for anyone, and the SIP line for *Show SIP details*. */
@Immutable data class FailureText(val sentence: String, val technical: String?)

/** See docs/messaging.md, Failures, in words and in SIP. */
fun MessageFailure.sentence(subject: FailureSubject): Sentence =
    when (reason) {
        FailureReason.AccountOffline ->
            Sentence(R.string.failure_account_offline, listOf(subject.account))
        FailureReason.NoSuchPeer ->
            Sentence(R.string.failure_no_such_peer, listOf(subject.peer, subject.account))
        FailureReason.Unsupported -> Sentence(R.string.failure_unsupported)
        FailureReason.Refused -> Sentence(R.string.failure_refused)
        FailureReason.NoAnswer -> Sentence(R.string.failure_no_answer, listOf(subject.account))
        FailureReason.PeerOffline -> Sentence(R.string.failure_peer_offline, listOf(subject.peer))
        FailureReason.ServerUnreachable ->
            Sentence(R.string.failure_unreachable, listOf(subject.account, subject.server))
        FailureReason.TooLarge -> Sentence(R.string.failure_too_large)
        FailureReason.Rejected -> Sentence(R.string.failure_rejected)
        FailureReason.TimedOut -> Sentence(R.string.failure_timed_out, listOf(subject.peer))
        FailureReason.Corrupted -> Sentence(R.string.failure_corrupted)
        FailureReason.PeerFailed -> Sentence(R.string.failure_peer_failed, listOf(subject.peer))
        FailureReason.Storage -> Sentence(R.string.failure_storage)
        FailureReason.FileMissing -> Sentence(R.string.failure_file_missing)
    }

@Composable
fun MessageFailure.explain(subject: FailureSubject): FailureText =
    FailureText(sentence(subject).text(), technical)

/** Why asking to send added nothing: the sentence, and its technical line when there is one. */
@Immutable data class SendProblem(val sentence: Sentence, val technical: String?)

/** What [this] outcome tells the user; `null` when it was queued, or there was nothing to send. */
fun SendOutcome.problem(): SendProblem? =
    when (this) {
        is SendOutcome.Queued,
        SendOutcome.Empty -> null
        SendOutcome.NoConversation -> SendProblem(Sentence(R.string.send_no_conversation), null)
        is SendOutcome.TooLong ->
            SendProblem(
                Sentence(R.string.send_too_long),
                "$bytes B: more than ${EnvelopeCodec.MAX_TEXT_PARTS} messages of $limit B",
            )
        is SendOutcome.FileTooLarge ->
            SendProblem(
                Sentence(R.string.send_file_too_large, listOf(fileSize(size), fileSize(limit))),
                "$size B > $limit B",
            )
        is SendOutcome.FileUnreadable ->
            SendProblem(Sentence(R.string.send_file_unreadable), detail)
    }
