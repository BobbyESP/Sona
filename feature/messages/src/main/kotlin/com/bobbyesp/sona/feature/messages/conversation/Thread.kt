/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.messages.conversation

import androidx.compose.runtime.Immutable
import com.bobbyesp.sona.messaging.domain.FailureReason
import com.bobbyesp.sona.messaging.domain.Message
import com.bobbyesp.sona.messaging.domain.MessageStatus
import com.bobbyesp.sona.messaging.domain.TransferState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A row of a conversation, with a key that is stable across updates. */
@Immutable
internal sealed interface ThreadItem {
    val key: String

    data class Day(val day: LocalDate, val at: Long) : ThreadItem {
        override val key: String = "day-$day"
    }

    /**
     * A message. [position] is its place in its run; the [last] of a run shows time and status.
     *
     * @property uri Where its file can be read (`content://…`), once it is on this phone.
     */
    data class Bubble(
        val message: Message,
        val position: BubblePosition,
        val uri: String? = null,
    ) : ThreadItem {
        override val key: String = "message-${message.id.value}"

        val last: Boolean
            get() = position == BubblePosition.Single || position == BubblePosition.Last
    }
}

/** A bubble's place in its run. */
internal enum class BubblePosition {
    Single,
    First,
    Middle,
    Last,
}

/** Messages a side writes this close together are one run. */
internal const val RUN_GAP_MILLIS = 5 * 60 * 1000L

/**
 * Oldest first, with a heading at each day. A run is what one side wrote the same day, each message
 * within [RUN_GAP_MILLIS] of the one before.
 *
 * @param uriOf Where a message's file is, if it is here.
 */
internal fun List<Message>.toThread(
    zone: ZoneId = ZoneId.systemDefault(),
    uriOf: (Message) -> String? = { null },
): List<ThreadItem> {
    if (isEmpty()) return emptyList()
    val out = ArrayList<ThreadItem>(size + 4)
    fun day(message: Message) = Instant.ofEpochMilli(message.at).atZone(zone).toLocalDate()
    fun together(a: Message, b: Message) =
        a.direction == b.direction && day(a) == day(b) && b.at - a.at <= RUN_GAP_MILLIS

    forEachIndexed { index, message ->
        val before = getOrNull(index - 1)
        val after = getOrNull(index + 1)
        if (before == null || day(before) != day(message)) {
            out += ThreadItem.Day(day(message), message.at)
        }
        val joinsBefore = before != null && together(before, message)
        val joinsAfter = after != null && together(message, after)
        val position =
            when {
                joinsBefore && joinsAfter -> BubblePosition.Middle
                joinsBefore -> BubblePosition.Last
                joinsAfter -> BubblePosition.First
                else -> BubblePosition.Single
            }
        out += ThreadItem.Bubble(message, position, uriOf(message))
    }
    return out
}

internal val Message.canRetry: Boolean
    get() {
        val file = attachment
        if (file != null && file.state == TransferState.Failed) {
            return failure?.reason != FailureReason.FileMissing
        }
        return outgoing && status == MessageStatus.Failed
    }

internal val Message.hasFailed: Boolean
    get() =
        failure != null &&
            (status == MessageStatus.Failed || attachment?.state == TransferState.Failed)
