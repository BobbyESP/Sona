/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.directory.domain

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.sip.api.CallDirection

/** How a call in the history ended, for the row to say in one word. */
enum class CallOutcome {
    /** It was answered and talked. */
    Answered,

    /** It rang here and nobody answered. */
    Missed,

    /** It was refused: by this side for an incoming call, by the other for a dialed one. */
    Declined,

    /** A dialed call that was cancelled before it was answered. */
    Cancelled,
    Busy,

    /** A dialed call nobody answered before the server gave up. */
    NoAnswer,

    /** The extension is not registered right now. */
    Unavailable,
    NotFound,

    /** Anything else that stopped it: a server error, no common codec, a broken network. */
    Failed,
}

/**
 * One call in the history. [status] is the SIP status of a failure, kept for *Show SIP details*;
 * [name] is who the call was with when it was made, so the history still says "Ana" after her
 * contact is deleted.
 */
data class CallRecord(
    val id: Long = 0,
    val account: AccountId?,
    val direction: CallDirection,
    val outcome: CallOutcome,
    val address: SipAddress,
    val name: String?,
    /** When it began ringing or dialing, in epoch milliseconds. */
    val startedAt: Long,
    /** Talk time in milliseconds; zero when it was never answered. */
    val durationMillis: Long,
    val statusCode: Int? = null,
    val statusText: String? = null,
    /** False for a missed call until the user has seen Recents. */
    val seen: Boolean = true,
) {
    val isMissed: Boolean
        get() = outcome == CallOutcome.Missed

    /** What to call the other side: the name kept with the call, or their user part. */
    val title: String
        get() = name ?: address.user ?: address.host

    /** The SIP status as one line (`486 Busy Here`), or `null` when there was none. */
    val statusLine: String?
        get() = statusCode?.let { code ->
            listOfNotNull(code.toString(), statusText).joinToString(" ")
        }
}
