/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.calling.domain

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.CallId
import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.sip.api.CallDirection
import com.bobbyesp.sona.sip.api.EngineCallState
import com.bobbyesp.sona.sip.api.MediaInfo
import com.bobbyesp.sona.sip.api.SipStatus
import com.bobbyesp.sona.telecom.api.AudioRoute

/** A call as the user sees it: the engine's SIP and the system's audio together. */
data class Call(
    val id: CallId,
    val account: AccountId?,
    val direction: CallDirection,
    val remote: SipAddress,
    val remoteName: String?,
    val state: CallState,
    val muted: Boolean = false,
    val media: MediaInfo? = null,
    /** When it was answered, in epoch milliseconds: the call screen's timer counts from it. */
    val connectedAt: Long? = null,
    val route: AudioRoute? = null,
    val routes: List<AudioRoute> = emptyList(),
) {
    /** What to call the other side: their name when they gave one, their user part otherwise. */
    val title: String
        get() = remoteName ?: remote.user ?: remote.host

    val isOver: Boolean
        get() = state is CallState.Ended
}

sealed interface CallState {
    data object Dialing : CallState

    data object RingingRemote : CallState

    data object Incoming : CallState

    data object Active : CallState

    data object OnHold : CallState

    data object HeldByRemote : CallState

    data class Ended(val reason: EndReason) : CallState
}

/**
 * Why a call ended, decided here once. The screen turns each into a sentence ("102 is busy"), and
 * [status] is the technical line under it.
 */
sealed interface EndReason {
    val status: SipStatus?
        get() = null

    data class HungUp(val byMe: Boolean) : EndReason

    /** An incoming call that stopped ringing before it was answered. */
    data object Missed : EndReason

    /** An outgoing call this side gave up on before it was answered. */
    data object Cancelled : EndReason

    data class Busy(override val status: SipStatus) : EndReason

    data class Declined(override val status: SipStatus) : EndReason

    data class Unavailable(override val status: SipStatus) : EndReason

    data class NotFound(override val status: SipStatus) : EndReason

    data class NoAnswer(override val status: SipStatus) : EndReason

    data class Unauthorized(override val status: SipStatus) : EndReason

    data class NoCommonMedia(override val status: SipStatus) : EndReason

    /**
     * The other side answered and hung up within a moment, with no SIP status: a server that picks
     * up and then fails to run the number (an Asterisk dial plan naming an app that is not loaded,
     * a voicemail box that does not exist).
     */
    data object EndedAtOnce : EndReason

    /** An answered call whose audio stopped arriving: the network between the two sides broke. */
    data class MediaLost(val detail: String?) : EndReason

    /** The server did not answer, or said it cannot ([status] `503`, or none and a [detail]). */
    data class ServerUnavailable(override val status: SipStatus?, val detail: String?) : EndReason

    data class Failed(override val status: SipStatus) : EndReason

    companion object {
        /** A remote hang-up sooner than this after answering is [EndedAtOnce]. */
        const val AT_ONCE_MS = 3_000L

        /** The reason for an engine's ending, given whether the call was ever answered. */
        fun of(
            ended: EngineCallState.Ended,
            direction: CallDirection,
            answered: Boolean,
            connectedForMs: Long? = null,
        ): EndReason {
            val status =
                ended.status
                    ?: return when {
                        ended.detail != null && answered -> MediaLost(ended.detail)
                        ended.detail != null -> ServerUnavailable(null, ended.detail)
                        answered &&
                            ended.byRemote &&
                            direction == CallDirection.Outgoing &&
                            connectedForMs != null &&
                            connectedForMs < AT_ONCE_MS -> EndedAtOnce

                        answered -> HungUp(byMe = !ended.byRemote)
                        direction == CallDirection.Incoming ->
                            if (ended.byRemote) Missed else HungUp(byMe = true)

                        else -> if (ended.byRemote) HungUp(byMe = false) else Cancelled
                    }
            return when (status.code) {
                486,
                600 -> Busy(status)
                603 -> Declined(status)
                480 -> Unavailable(status)
                404,
                484 -> NotFound(status)
                408 -> NoAnswer(status)
                401,
                403,
                407 -> Unauthorized(status)
                415,
                488 -> NoCommonMedia(status)
                503 -> ServerUnavailable(status, ended.detail)
                else -> Failed(status)
            }
        }
    }
}

/** The state of a call as the user sees it, for an engine state. */
internal fun EngineCallState.toCallState(endReason: () -> EndReason): CallState =
    when (this) {
        EngineCallState.Dialing -> CallState.Dialing
        EngineCallState.Ringing -> CallState.RingingRemote
        EngineCallState.Incoming -> CallState.Incoming
        EngineCallState.Active -> CallState.Active
        EngineCallState.LocalHold -> CallState.OnHold
        EngineCallState.RemoteHold -> CallState.HeldByRemote
        is EngineCallState.Ended -> CallState.Ended(endReason())
    }
