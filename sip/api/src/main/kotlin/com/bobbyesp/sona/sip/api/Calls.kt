/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.api

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.CallId
import com.bobbyesp.sona.core.model.SipAddress

enum class CallDirection {
    Incoming,
    Outgoing,
}

/** A call as the engine knows it: SIP, without Telecom, contacts or the user's view of it. */
sealed interface EngineCallState {
    /** INVITE sent, nothing back yet but `100 Trying`. */
    data object Dialing : EngineCallState

    /** `180 Ringing` or `183` with early media: the other side is ringing. */
    data object Ringing : EngineCallState

    /** An INVITE arrived and is waiting for an answer. */
    data object Incoming : EngineCallState

    /** Answered, media flowing. */
    data object Active : EngineCallState

    /** Put on hold from here. */
    data object LocalHold : EngineCallState

    /** Put on hold by the other side. */
    data object RemoteHold : EngineCallState

    /**
     * Over. [status] is the SIP status of the failure (`486 Busy Here`) or `null` for a normal end;
     * [byRemote] says which side ended it; [detail] is what was tried when nothing answered.
     */
    data class Ended(
        val status: SipStatus? = null,
        val byRemote: Boolean = false,
        val detail: String? = null,
    ) : EngineCallState
}

/**
 * The kind of device a call's audio is on. The engine never picks one: Android's call system does
 * (the user's choice, a headset plugged in), and the engine is told so its streams follow.
 */
enum class AudioDeviceKind {
    Earpiece,
    Speaker,
    WiredHeadset,
    Bluetooth,
}

/** What is negotiated for the audio. */
data class MediaInfo(val codec: String, val clockRate: Int, val encryption: String? = null) {
    override fun toString(): String = buildString {
        append(codec).append(" · ").append(clockRate / 1000).append(" kHz")
        if (encryption != null) append(" · ").append(encryption)
    }
}

/**
 * A call the engine has. Calls that ended stay in [SipEngine.calls] as [EngineCallState.Ended]
 * until [SipEngine.forget]: a `StateFlow` keeps only the last value, and an ending missed would be
 * a failure the user never hears about.
 */
data class EngineCall(
    val id: CallId,
    val account: AccountId?,
    val direction: CallDirection,
    val remote: SipAddress,
    val remoteName: String? = null,
    val state: EngineCallState,
    val muted: Boolean = false,
    val media: MediaInfo? = null,
)

sealed interface DialResult {
    data class Started(val id: CallId) : DialResult

    /** The account is unknown to the engine, disabled, or not registered. */
    data object AccountUnavailable : DialResult

    /** The engine refused to dial; [detail] is for the diagnostics. */
    data class Failed(val detail: String) : DialResult
}
