/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.directory.domain

import com.bobbyesp.sona.calling.domain.Call
import com.bobbyesp.sona.calling.domain.CallState
import com.bobbyesp.sona.calling.domain.EndReason
import com.bobbyesp.sona.calling.domain.RefusedCall
import com.bobbyesp.sona.core.model.CallId
import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.sip.api.CallDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/**
 * Writes the history: every call that ends is recorded once, with how it ended. It only follows
 * what [CallCoordinator][com.bobbyesp.sona.calling.domain.CallCoordinator] publishes; it decides
 * nothing about a call, so a screen can never forget to log one.
 *
 * @param names Who an address belongs to, to keep the name with the record.
 */
class CallRecorder(
    private val calls: Flow<List<Call>>,
    private val history: CallHistory,
    private val scope: CoroutineScope,
    private val names: suspend (SipAddress) -> String? = { null },
    private val clock: () -> Long = System::currentTimeMillis,
    private val refused: Flow<RefusedCall> = emptyFlow(),
) {
    private val firstSeen = mutableMapOf<CallId, Long>()
    private val recorded = mutableSetOf<CallId>()

    /** Follows the calls; once, at start. */
    fun start(): Job = scope.launch {
        launch {
            refused.collect { call ->
                history.add(call.toRecord(names(call.remote) ?: call.remoteName))
            }
        }
        calls.collect { list ->
            val now = clock()
            for (call in list) {
                firstSeen.getOrPut(call.id) { now }
                if (call.state is CallState.Ended && recorded.add(call.id)) {
                    val record =
                        call.toRecord(
                            startedAt = firstSeen.getValue(call.id),
                            endedAt = now,
                            name = names(call.remote) ?: call.remoteName,
                        )
                    history.add(record)
                }
            }
            val present = list.map { it.id }.toSet()
            firstSeen.keys.retainAll(present)
            recorded.retainAll(present)
        }
    }
}

/** The record of an ended call; [startedAt] and [endedAt] are epoch milliseconds. */
/**
 * A missed call, with what the caller was answered as its SIP line. One refused by do not disturb
 * is already seen: it is in Recents, and no notification says so.
 */
fun RefusedCall.toRecord(name: String?): CallRecord =
    CallRecord(
        account = account,
        direction = CallDirection.Incoming,
        outcome = CallOutcome.Missed,
        address = remote,
        name = name,
        startedAt = at,
        durationMillis = 0,
        statusCode = status.code,
        statusText = status.phrase,
        seen = quiet,
    )

fun Call.toRecord(startedAt: Long, endedAt: Long, name: String?): CallRecord {
    val reason = (state as? CallState.Ended)?.reason
    val status = reason?.status
    return CallRecord(
        account = account,
        direction = direction,
        outcome = outcomeOf(reason, answered = connectedAt != null),
        address = remote,
        name = name,
        startedAt = startedAt,
        durationMillis = connectedAt?.let { (endedAt - it).coerceAtLeast(0) } ?: 0,
        statusCode = status?.code,
        statusText = status?.phrase,
        seen = reason != EndReason.Missed,
    )
}

private fun Call.outcomeOf(reason: EndReason?, answered: Boolean): CallOutcome =
    when {
        answered -> CallOutcome.Answered
        reason == EndReason.Missed -> CallOutcome.Missed
        // An incoming call that was not answered and not missed was refused here.
        direction == CallDirection.Incoming -> CallOutcome.Declined
        else ->
            when (reason) {
                EndReason.Cancelled -> CallOutcome.Cancelled
                is EndReason.Busy -> CallOutcome.Busy
                is EndReason.Declined -> CallOutcome.Declined
                is EndReason.NoAnswer -> CallOutcome.NoAnswer
                is EndReason.Unavailable -> CallOutcome.Unavailable
                is EndReason.NotFound -> CallOutcome.NotFound
                else -> CallOutcome.Failed
            }
    }

/**
 * Calls in a row with the same person and the same kind, as one entry ("Ana (3)"): what Recents
 * shows when grouping is on. Input newest first, output newest first.
 */
data class RecentGroup(val records: List<CallRecord>) {
    val latest: CallRecord
        get() = records.first()

    val count: Int
        get() = records.size
}

fun List<CallRecord>.groupedConsecutive(): List<RecentGroup> {
    val groups = mutableListOf<MutableList<CallRecord>>()
    for (record in this) {
        val last = groups.lastOrNull()?.first()
        if (last != null && last.sameKindAs(record)) groups.last() += record
        else groups += mutableListOf(record)
    }
    return groups.map(::RecentGroup)
}

private fun CallRecord.sameKindAs(other: CallRecord): Boolean =
    address.user == other.address.user &&
        address.host.equals(other.address.host, ignoreCase = true) &&
        direction == other.direction &&
        isMissed == other.isMissed
