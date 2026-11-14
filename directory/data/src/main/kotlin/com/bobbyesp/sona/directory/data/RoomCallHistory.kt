/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.directory.data

import com.bobbyesp.sona.core.database.CallRecordDao
import com.bobbyesp.sona.core.database.CallRecordEntity
import com.bobbyesp.sona.core.database.SonaDatabase
import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.directory.domain.CallHistory
import com.bobbyesp.sona.directory.domain.CallOutcome
import com.bobbyesp.sona.directory.domain.CallRecord
import com.bobbyesp.sona.sip.api.CallDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The call history the app uses. Room stays an implementation detail. */
fun SonaDatabase.callHistory(): CallHistory = RoomCallHistory(callRecordDao())

internal class RoomCallHistory(private val dao: CallRecordDao) : CallHistory {
    override val records: Flow<List<CallRecord>> =
        dao.observeAll().map { rows -> rows.mapNotNull { it.toRecord() } }

    override val unseenMissed: Flow<List<CallRecord>> =
        dao.observeUnseenMissed().map { rows -> rows.mapNotNull { it.toRecord() } }

    override suspend fun add(record: CallRecord) {
        dao.insert(record.toEntity())
    }

    override suspend fun delete(ids: Set<Long>) = dao.delete(ids)

    override suspend fun clear() = dao.clear()

    override suspend fun markSeen() = dao.markSeen()

    override suspend fun deleteBefore(epochMillis: Long) = dao.deleteBefore(epochMillis)
}

/** `null` for a row that means nothing anymore (an address that does not parse). */
internal fun CallRecordEntity.toRecord(): CallRecord? {
    val address = SipAddress.parse(uri) ?: return null
    return CallRecord(
        id = id,
        account = accountId?.let(::AccountId),
        direction =
            CallDirection.entries.firstOrNull { it.name == direction } ?: CallDirection.Outgoing,
        outcome = CallOutcome.entries.firstOrNull { it.name == outcome } ?: CallOutcome.Failed,
        address = address,
        name = name,
        startedAt = startedAt,
        durationMillis = durationMillis,
        statusCode = statusCode,
        statusText = statusText,
        seen = seen,
    )
}

internal fun CallRecord.toEntity() =
    CallRecordEntity(
        id = id,
        accountId = account?.value,
        direction = direction.name,
        outcome = outcome.name,
        uri = address.uri,
        name = name,
        startedAt = startedAt,
        durationMillis = durationMillis,
        statusCode = statusCode,
        statusText = statusText,
        seen = seen,
    )
