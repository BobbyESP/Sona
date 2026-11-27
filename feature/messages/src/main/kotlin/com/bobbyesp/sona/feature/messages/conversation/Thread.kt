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

/** A row of a conversation: a day's heading, or a message. Keys are stable across updates. */
@Immutable
internal sealed interface ThreadItem {
    val key: String

    /** The heading of a day: *Today*, *Yesterday*, the date. */
    data class Day(val day: LocalDate, val at: Long) : ThreadItem {
        override val key: String = "day-$day"
    }

    /**
     * A message, where it sits in its run ([position]) and whether its time and status are shown
     * under it ([last] of its run).
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

/**
 * Where a bubble is among the messages one side wrote in a row: the corners towards its neighbours
 * are tightened, so a run reads as one block.
 */
internal enum class BubblePosition {
    Single,
    First,
    Middle,
    Last,
}

/** Messages a side writes this close together are one run. */
internal const val RUN_GAP_MILLIS = 5 * 60 * 1000L

/**
 * The conversation as it is drawn, oldest first: a heading at each new day, and each message with
 * its place in its run. A run is what one side wrote the same day, each within [RUN_GAP_MILLIS] of
 * the one before.
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

/** Whether *Retry* can do something for [this]: a text or offer that failed, or a transfer. */
internal val Message.canRetry: Boolean
    get() {
        val file = attachment
        if (file != null && file.state == TransferState.Failed) {
            return failure?.reason != FailureReason.FileMissing
        }
        return outgoing && status == MessageStatus.Failed
    }

/** Whether [this] failed in a way the user should read about under it. */
internal val Message.hasFailed: Boolean
    get() =
        failure != null &&
            (status == MessageStatus.Failed || attachment?.state == TransferState.Failed)
