/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.messages

import com.bobbyesp.sona.feature.messages.conversation.BubblePosition
import com.bobbyesp.sona.feature.messages.conversation.ThreadItem
import com.bobbyesp.sona.feature.messages.conversation.canRetry
import com.bobbyesp.sona.feature.messages.conversation.toThread
import com.bobbyesp.sona.messaging.domain.Attachment
import com.bobbyesp.sona.messaging.domain.ConversationId
import com.bobbyesp.sona.messaging.domain.FailureReason
import com.bobbyesp.sona.messaging.domain.Message
import com.bobbyesp.sona.messaging.domain.MessageDirection
import com.bobbyesp.sona.messaging.domain.MessageFailure
import com.bobbyesp.sona.messaging.domain.MessageId
import com.bobbyesp.sona.messaging.domain.MessageStatus
import com.bobbyesp.sona.messaging.domain.TransferState
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadTest {

    private val utc = ZoneOffset.UTC
    private val day = 24L * 60 * 60 * 1000
    private val minute = 60_000L
    private var next = 1L

    private fun message(at: Long, outgoing: Boolean = false, text: String = "hi") =
        Message(
            id = MessageId(next++),
            conversation = ConversationId(1),
            direction = if (outgoing) MessageDirection.Outgoing else MessageDirection.Incoming,
            text = text,
            at = at,
            status = if (outgoing) MessageStatus.Sent else MessageStatus.Received,
        )

    @Test
    fun `a heading opens each day, and a side's run is one block`() {
        val thread =
            listOf(
                    message(0),
                    message(1 * minute),
                    message(2 * minute),
                    message(3 * minute, outgoing = true),
                    message(day + 10 * minute, outgoing = true),
                )
                .toThread(utc)

        val days = thread.filterIsInstance<ThreadItem.Day>().map { it.day }
        assertEquals(listOf(LocalDate.of(1970, 1, 1), LocalDate.of(1970, 1, 2)), days)
        assertTrue(thread.first() is ThreadItem.Day)

        val positions = thread.filterIsInstance<ThreadItem.Bubble>().map { it.position }
        assertEquals(
            listOf(
                BubblePosition.First,
                BubblePosition.Middle,
                BubblePosition.Last,
                // The other side, then a new day: each alone.
                BubblePosition.Single,
                BubblePosition.Single,
            ),
            positions,
        )
        // The time and status go under the last of a run only.
        assertEquals(
            listOf(false, false, true, true, true),
            thread.filterIsInstance<ThreadItem.Bubble>().map { it.last },
        )
    }

    @Test
    fun `a pause breaks a run`() {
        val thread = listOf(message(0), message(6 * minute)).toThread(utc)
        assertEquals(
            listOf(BubblePosition.Single, BubblePosition.Single),
            thread.filterIsInstance<ThreadItem.Bubble>().map { it.position },
        )
    }

    @Test
    fun `keys are stable and unique, whatever changes in a message`() {
        val first = message(0)
        val thread = listOf(first, message(minute)).toThread(utc)
        val again = listOf(first.copy(status = MessageStatus.Read), message(minute)).toThread(utc)
        assertEquals(
            thread.first { it is ThreadItem.Bubble }.key,
            again.first { it is ThreadItem.Bubble }.key,
        )
        assertEquals(thread.size, thread.map { it.key }.toSet().size)
    }

    @Test
    fun `a file's address comes from where it is`() {
        val file = message(0).copy(attachment = attachment(TransferState.Done))
        val thread = listOf(file).toThread(utc) { "content://x/${it.id.value}" }
        assertEquals("content://x/${file.id.value}", (thread.last() as ThreadItem.Bubble).uri)
    }

    @Test
    fun `retry is offered where it can do something`() {
        val failed =
            message(0, outgoing = true)
                .copy(
                    status = MessageStatus.Failed,
                    failure = MessageFailure(FailureReason.NoAnswer),
                )
        assertTrue(failed.canRetry)
        assertFalse(message(0, outgoing = true).canRetry)
        val transfer =
            message(0)
                .copy(
                    attachment = attachment(TransferState.Failed),
                    failure = MessageFailure(FailureReason.TimedOut),
                )
        assertTrue(transfer.canRetry)
        val gone =
            message(0, outgoing = true)
                .copy(
                    attachment = attachment(TransferState.Failed),
                    failure = MessageFailure(FailureReason.FileMissing),
                )
        assertFalse(gone.canRetry)
    }

    private fun attachment(state: TransferState) =
        Attachment(
            transferId = "t",
            name = "a.jpg",
            mime = "image/jpeg",
            size = 10,
            sha256 = "",
            chunkSize = 10,
            chunks = 1,
            state = state,
            file = "received/t/a.jpg",
        )
}
