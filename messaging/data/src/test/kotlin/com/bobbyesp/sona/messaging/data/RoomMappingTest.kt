/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.data

import com.bobbyesp.sona.core.database.ConversationEntity
import com.bobbyesp.sona.core.database.MessageWithAttachment
import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.messaging.domain.Attachment
import com.bobbyesp.sona.messaging.domain.ConversationId
import com.bobbyesp.sona.messaging.domain.FailureReason
import com.bobbyesp.sona.messaging.domain.Message
import com.bobbyesp.sona.messaging.domain.MessageDirection
import com.bobbyesp.sona.messaging.domain.MessageFailure
import com.bobbyesp.sona.messaging.domain.MessageId
import com.bobbyesp.sona.messaging.domain.MessageStatus
import com.bobbyesp.sona.messaging.domain.PeerKind
import com.bobbyesp.sona.messaging.domain.TransferState
import com.bobbyesp.sona.sip.api.SipStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What is written to Room reads back as it was. */
class RoomMappingTest {

    @Test
    fun `a message with its file and its failure reads back as it was written`() {
        val message =
            Message(
                id = MessageId(7),
                conversation = ConversationId(3),
                direction = MessageDirection.Outgoing,
                text = "",
                at = 1_000,
                status = MessageStatus.Failed,
                envelopeId = "abcde23456",
                failure =
                    MessageFailure(FailureReason.NoSuchPeer, SipStatus(404, "Not Found"), "x"),
                attachment =
                    Attachment(
                        transferId = "abcde23456",
                        name = "a.jpg",
                        mime = "image/jpeg",
                        size = 10,
                        sha256 = "f".repeat(64),
                        chunkSize = 504,
                        chunks = 1,
                        width = 2,
                        height = 3,
                        state = TransferState.Failed,
                        file = "sent/1/a.jpg",
                    ),
                parts = 1,
                partsSent = 1,
                sizeLimit = 700,
            )
        val row = MessageWithAttachment(message.toEntity(), message.attachment!!.toEntity(7))

        assertEquals(message, row.toMessage())
    }

    @Test
    fun `a value no longer known reads as a safe one, and a bad address as nothing`() {
        val row = ConversationEntity(1, "lab", "sip:102@lab.local", null, "Martian", 0)
        assertEquals(PeerKind.Unknown, row.toConversation()!!.peerKind)
        assertEquals(SipAddress("102", "lab.local"), row.toConversation()!!.peer)
        assertEquals(AccountId("lab"), row.toConversation()!!.account)
        assertNull(row.copy(peerUri = "::::").toConversation())
    }
}
