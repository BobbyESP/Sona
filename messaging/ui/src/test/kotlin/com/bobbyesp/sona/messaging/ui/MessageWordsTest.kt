/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.ui

import com.bobbyesp.sona.messaging.domain.Attachment
import com.bobbyesp.sona.messaging.domain.ConversationId
import com.bobbyesp.sona.messaging.domain.FailureReason
import com.bobbyesp.sona.messaging.domain.Message
import com.bobbyesp.sona.messaging.domain.MessageDirection
import com.bobbyesp.sona.messaging.domain.MessageFailure
import com.bobbyesp.sona.messaging.domain.MessageStatus
import com.bobbyesp.sona.messaging.domain.SendOutcome
import com.bobbyesp.sona.messaging.domain.TransferState
import com.bobbyesp.sona.sip.api.SipStatus
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageWordsTest {

    private val lab = FailureSubject("Lab Asterisk", "205", "192.168.1.10:5060 (UDP)")

    @Test
    fun `every failure has its sentence, naming what it is about`() {
        assertEquals(
            Sentence(R.string.failure_no_such_peer, listOf("205", "Lab Asterisk")),
            MessageFailure(FailureReason.NoSuchPeer, SipStatus(404, "Not Found")).sentence(lab),
        )
        assertEquals(
            Sentence(
                R.string.failure_unreachable,
                listOf("Lab Asterisk", "192.168.1.10:5060 (UDP)"),
            ),
            MessageFailure(FailureReason.ServerUnreachable).sentence(lab),
        )
        assertEquals(
            Sentence(R.string.failure_account_offline, listOf("Lab Asterisk")),
            MessageFailure(FailureReason.AccountOffline).sentence(lab),
        )
        // Each reason says something of its own.
        val sentences = FailureReason.entries.map { MessageFailure(it).sentence(lab).res }
        assertEquals(FailureReason.entries.size, sentences.toSet().size)
    }

    @Test
    fun `the technical line is the status and what was tried`() {
        assertEquals(
            "404 Not Found",
            MessageFailure(FailureReason.NoSuchPeer, SipStatus(404, "Not Found")).technical,
        )
        assertNull(MessageFailure(FailureReason.Corrupted).technical)
    }

    @Test
    fun `a send that added nothing says why, with its numbers`() {
        assertNull(SendOutcome.Empty.problem())
        val big = SendOutcome.FileTooLarge(size = 25L * 1024 * 1024, limit = 10L * 1024 * 1024)
        val problem = big.problem()!!
        assertEquals(R.string.send_file_too_large, problem.sentence.res)
        assertEquals(2, problem.sentence.args.size)
        assertEquals(
            "disk full",
            SendOutcome.FileUnreadable("disk full").problem()!!.technical,
        )
        assertTrue(SendOutcome.TooLong(80_000, 700).problem()!!.technical!!.contains("700"))
    }

    @Test
    fun `sizes read as people read them, in their numbers`() {
        assertEquals("512 B", fileSize(512, Locale.US))
        assertEquals("184 KB", fileSize(184L * 1024, Locale.US))
        assertEquals("1.2 MB", fileSize(1_258_291, Locale.US))
        assertEquals("1,2 MB", fileSize(1_258_291, Locale.forLanguageTag("es-ES")))
        assertEquals("10 MB", fileSize(10L * 1024 * 1024, Locale.US))
    }

    @Test
    fun `a file's kind comes from its type`() {
        assertEquals(FileKind.Picture, FileKind.of("image/jpeg"))
        assertEquals(FileKind.Pdf, FileKind.of("application/pdf"))
        assertEquals(FileKind.Spreadsheet, FileKind.of("text/csv"))
        assertEquals(FileKind.Text, FileKind.of("text/plain; charset=utf-8"))
        assertEquals(
            FileKind.Text,
            FileKind.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        )
        assertEquals(FileKind.Archive, FileKind.of("application/zip"))
        assertEquals(FileKind.Contact, FileKind.of("text/vcard"))
        assertEquals(FileKind.Other, FileKind.of("application/octet-stream"))
    }

    @Test
    fun `a file's line follows its transfer`() {
        val sending = file(TransferState.Sending, outgoing = true).copyFile { copy(done = 5) }
        assertEquals(Sentence(R.string.transfer_sending, listOf(50)), sending.transferLine("102"))
        assertEquals(
            Sentence(R.string.transfer_waiting, listOf("102")),
            file(TransferState.WaitingForPeer, outgoing = true).transferLine("102"),
        )
        assertEquals(
            R.string.transfer_offered,
            file(TransferState.Offered, outgoing = false).transferLine("102")!!.res,
        )
        assertNull(file(TransferState.Done, outgoing = false).transferLine("102"))
        assertTrue(sending.isTransferring)
        assertFalse(file(TransferState.Done, outgoing = true).isTransferring)
    }

    @Test
    fun `a picture is previewed as a photo, any other file by its name`() {
        assertEquals(
            Sentence(R.string.preview_picture),
            file(TransferState.Done, outgoing = false).preview(),
        )
        val pdf =
            file(TransferState.Done, outgoing = false).copyFile { copy(mime = "application/pdf") }
        assertEquals(Sentence(R.string.preview_file, listOf("photo.jpg")), pdf.preview())
        assertNull(file(TransferState.Done, false).copy(attachment = null).preview())
    }

    private fun file(state: TransferState, outgoing: Boolean) =
        Message(
            conversation = ConversationId(1),
            direction = if (outgoing) MessageDirection.Outgoing else MessageDirection.Incoming,
            text = "",
            at = 0,
            status = if (outgoing) MessageStatus.Sent else MessageStatus.Received,
            attachment =
                Attachment(
                    transferId = "abc",
                    name = "photo.jpg",
                    mime = "image/jpeg",
                    size = 5_000,
                    sha256 = "",
                    chunkSize = 500,
                    chunks = 10,
                    state = state,
                ),
        )

    private fun Message.copyFile(change: Attachment.() -> Attachment) =
        copy(attachment = attachment!!.change())
}
