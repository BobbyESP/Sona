/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.domain

import com.bobbyesp.sona.core.model.settings.MessageSettings
import com.bobbyesp.sona.core.model.settings.MessageSizeLimit
import com.bobbyesp.sona.messaging.domain.envelope.Envelope
import com.bobbyesp.sona.sip.api.MessageSendResult
import com.bobbyesp.sona.sip.api.SipStatus
import com.bobbyesp.sona.sip.fake.FakeMessaging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Texts between two phones wired through a scripted server: the messenger as scenarios. */
@OptIn(ExperimentalCoroutinesApi::class)
class MessengerTest {

    private suspend fun TestScope.phones(): Pair<Phone, Phone> {
        val a = Phone(this, "101").apply { boot() }
        val b = Phone(this, "102").apply { boot() }
        link(a, b)
        runCurrent()
        return a to b
    }

    private suspend fun Phone.conversationWith(peer: Phone): Conversation? =
        store.findConversation(account, peer.address)

    /** [a] and [b] know each other as Sona phones: [b] said hello first. */
    private suspend fun TestScope.sonaPhones(): Triple<Phone, Phone, ConversationId> {
        val (a, b) = phones()
        val conversation = a.with(b)
        a.messaging.deliver(from = b.address, body = "sona:1:hi", account = a.account)
        runCurrent()
        return Triple(a, b, conversation)
    }

    @Test
    fun `a text to a peer not known to be Sona goes as typed, and the server's 202 makes it sent`() =
        runTest {
            val (a, b) = phones()
            val conversation = a.with(b)

            val outcome = a.messenger.sendText(conversation, "hola, ¿qué tal?")
            runCurrent()

            assertTrue(outcome is SendOutcome.Queued)
            assertEquals(listOf("hola, ¿qué tal?"), a.messaging.sentTo(b.address))
            assertEquals(MessageStatus.Sent, a.only(conversation).status)
            val there = b.conversationWith(a)!!
            assertEquals(PeerKind.Plain, there.peerKind)
            assertEquals("hola, ¿qué tal?", b.only(there.id).text)
            assertEquals(1, b.messenger.totalUnread.first())
        }

    @Test
    fun `a long text to a plain peer goes in parts cut at words, each one a message there`() =
        runTest {
            val (a, b) = phones()
            val conversation = a.with(b)
            val text = "palabra ".repeat(200)

            a.messenger.sendText(conversation, text)
            runCurrent()

            val bodies = a.messaging.sentTo(b.address)
            assertTrue(bodies.size > 1)
            assertTrue(bodies.all { it.toByteArray().size <= 700 })
            assertEquals(text, bodies.joinToString(""))
            assertEquals(bodies.size, a.only(conversation).partsSent)
            assertEquals(bodies.size, b.messages(b.conversationWith(a)!!.id).size)
        }

    @Test
    fun `a refusal is a failure with the server's line, and is not tried again`() = runTest {
        val (a, b) = phones()
        link(a, b, answer = { _, _ -> MessageSendResult.Failed(SipStatus(404, "Not Found")) })
        val conversation = a.with(b)

        a.messenger.sendText(conversation, "¿hay alguien?")
        wait(60)

        val message = a.only(conversation)
        assertEquals(MessageStatus.Failed, message.status)
        assertEquals(FailureReason.NoSuchPeer, message.failure?.reason)
        assertEquals("404 Not Found", message.failure?.technical)
        assertEquals(1, a.messaging.sent.size)
    }

    @Test
    fun `a busy server is asked again with back-off, and the text goes when it answers`() =
        runTest {
            val (a, b) = phones()
            var busy = 2
            link(a, b, answer = { _, _ ->
                if (busy-- > 0) MessageSendResult.Failed(SipStatus(503, "Service Unavailable"))
                else FakeMessaging.ACCEPTED
            })
            val conversation = a.with(b)

            a.messenger.sendText(conversation, "insisto")
            runCurrent()
            assertEquals(MessageStatus.Sending, a.only(conversation).status)
            wait(10)

            assertEquals(MessageStatus.Sent, a.only(conversation).status)
            assertEquals(3, a.messaging.sent.size)
            assertEquals("insisto", b.only(b.conversationWith(a)!!.id).text)
        }

    @Test
    fun `nothing is sent from an account that is not registered, and the user is told so`() =
        runTest {
            val (a, b) = phones()
            val conversation = a.with(b)
            a.engine.goOffline()
            runCurrent()

            a.messenger.sendText(conversation, "hola")
            wait(1)

            assertEquals(FailureReason.AccountOffline, a.only(conversation).failure?.reason)
            assertTrue(a.messaging.sent.isEmpty())
        }

    @Test
    fun `retrying a failed text sends it again`() = runTest {
        val (a, b) = phones()
        var refuse = true
        link(a, b, answer = { _, _ ->
            if (refuse) MessageSendResult.Failed(SipStatus(480, "Temporarily Unavailable"))
            else FakeMessaging.ACCEPTED
        })
        val conversation = a.with(b)
        a.messenger.sendText(conversation, "luego")
        runCurrent()
        assertEquals(FailureReason.PeerOffline, a.only(conversation).failure?.reason)

        refuse = false
        a.messenger.retry(a.only(conversation).id)
        runCurrent()

        assertEquals(MessageStatus.Sent, a.only(conversation).status)
        assertNull(a.only(conversation).failure)
    }

    @Test
    fun `a blank text is not sent, nor one too long for the server`() = runTest {
        val (a, b) = phones()
        val conversation = a.with(b)

        assertEquals(SendOutcome.Empty, a.messenger.sendText(conversation, "  \n"))
        val tooLong = a.messenger.sendText(conversation, "x".repeat(100_000))
        assertTrue(tooLong is SendOutcome.TooLong)
        assertTrue(a.messages(conversation).isEmpty())
    }

    @Test
    fun `with a Sona peer a text carries an id, and comes back delivered then read`() = runTest {
        val (a, b, conversation) = sonaPhones()

        a.messenger.sendText(conversation, "¿me lees?")
        wait(1)

        val sent = a.sentTo(b).filterIsInstance<Envelope.Text>().single()
        assertEquals("¿me lees?", sent.text)
        assertEquals(MessageStatus.Delivered, a.only(conversation).status)
        val there = b.conversationWith(a)!!
        assertEquals(PeerKind.Sona, there.peerKind)

        b.messenger.setVisibleConversation(there.id)
        wait(1)

        assertEquals(MessageStatus.Read, a.only(conversation).status)
        assertEquals(0, b.messenger.totalUnread.first())
    }

    @Test
    fun `read receipts are not sent when the user turned them off`() = runTest {
        val (a, b, conversation) = sonaPhones()
        b.settings.set(MessageSettings.ReadReceipts, false)

        a.messenger.sendText(conversation, "secreto")
        wait(1)
        b.messenger.markRead(b.conversationWith(a)!!.id)
        wait(1)

        assertEquals(MessageStatus.Delivered, a.only(conversation).status)
        assertTrue(b.sentTo(a).none { it is Envelope.Receipt && it.read })
    }

    @Test
    fun `a long text to a Sona peer is put back together as one message`() = runTest {
        val (a, b, conversation) = sonaPhones()
        val text = "Érase una vez 🐧 ".repeat(150)

        a.messenger.sendText(conversation, text)
        wait(1)

        assertTrue(a.sentTo(b).filterIsInstance<Envelope.Text>().size > 1)
        val there = b.only(b.conversationWith(a)!!.id)
        assertEquals(text, there.text)
        assertEquals(0, b.store.partsKept)
        assertEquals(MessageStatus.Delivered, a.only(conversation).status)
    }

    @Test
    fun `a text sent again because its answer was lost is shown once`() = runTest {
        val (a, b) = phones()
        val body = "otra vez\nsona:1:t:abcde23456:1/1"
        repeat(2) { b.messaging.deliver(from = a.address, body = body, account = b.account) }
        runCurrent()

        val there = b.conversationWith(a)!!
        assertEquals("otra vez", b.only(there.id).text)
    }

    @Test
    fun `an envelope that is not quite one is shown as the text it is`() = runTest {
        val (a, b) = phones()
        b.messaging.deliver(from = a.address, body = "sona:1:t:nope", account = b.account)
        runCurrent()

        val there = b.conversationWith(a)!!
        assertEquals("sona:1:t:nope", b.only(there.id).text)
        assertEquals(PeerKind.Plain, there.peerKind)
    }

    @Test
    fun `typing reaches a Sona peer for six seconds, at most every four, and never a plain one`() =
        runTest {
            val (a, b, conversation) = sonaPhones()
            a.messenger.sendText(conversation, "hola")
            wait(1)
            val there = b.conversationWith(a)!!.id

            a.messenger.notifyTyping(conversation)
            a.messenger.notifyTyping(conversation)
            runCurrent()

            assertEquals(1, a.sentTo(b).count { it == Envelope.Typing })
            assertTrue(b.messenger.typing(there).first())
            wait(7)
            assertFalse(b.messenger.typing(there).first())

            // A phone not known to be Sona is never told.
            val plain = Phone(this, "103").apply { boot() }
            link(a, plain)
            val other = a.with(plain)
            a.messenger.notifyTyping(other)
            runCurrent()
            assertTrue(a.messaging.sentTo(plain.address).isEmpty())
        }

    @Test
    fun `typing is not sent when the user turned it off`() = runTest {
        val (a, b, conversation) = sonaPhones()
        a.settings.set(MessageSettings.TypingIndicators, false)

        a.messenger.notifyTyping(conversation)
        runCurrent()

        assertTrue(a.sentTo(b).none { it == Envelope.Typing })
    }

    @Test
    fun `an arrival is announced, and says whether its conversation is on screen`() = runTest {
        val (a, b) = phones()
        val events = mutableListOf<MessengerEvent>()
        backgroundScope.launch { b.messenger.events.collect { events += it } }
        runCurrent()
        val conversation = a.with(b)

        a.messenger.sendText(conversation, "uno")
        runCurrent()
        val there = b.conversationWith(a)!!.id
        b.messenger.setVisibleConversation(there)
        runCurrent()
        a.messenger.sendText(conversation, "dos")
        runCurrent()

        val received = events.filterIsInstance<MessengerEvent.Received>()
        assertEquals(listOf("uno", "dos"), received.map { it.message.text })
        assertEquals(listOf(false, true), received.map { it.visible })
        assertEquals(0, b.messenger.totalUnread.first())
    }

    @Test
    fun `the largest message the server relays is a setting`() = runTest {
        val (a, b) = phones()
        a.settings.set(MessageSettings.LargestMessage, MessageSizeLimit.Kb4)
        val conversation = a.with(b)

        a.messenger.sendText(conversation, "palabra ".repeat(200))
        runCurrent()

        assertEquals(1, a.messaging.sentTo(b.address).size)
    }

    @Test
    fun `a text left sending when the app closed goes once the account is back`() = runTest {
        val (a, b) = phones()
        val conversation = a.with(b)
        a.engine.goOffline()
        runCurrent()
        a.store.add(
            Message(
                conversation = conversation,
                direction = MessageDirection.Outgoing,
                text = "pendiente",
                at = 0,
                status = MessageStatus.Sending,
                sizeLimit = 700,
            ),
        )

        a.restart()
        wait(5)
        assertEquals(MessageStatus.Sending, a.only(conversation).status)
        a.engine.goOnline()
        wait(5)

        assertEquals(MessageStatus.Sent, a.only(conversation).status)
        assertEquals("pendiente", b.only(b.conversationWith(a)!!.id).text)
    }

    @Test
    fun `texts of a conversation go out in the order they were written`() = runTest {
        val (a, b) = phones()
        val conversation = a.with(b)

        repeat(5) { a.messenger.sendText(conversation, "m$it") }
        runCurrent()

        assertEquals(List(5) { "m$it" }, a.messaging.sentTo(b.address))
        assertEquals(List(5) { "m$it" }, b.messages(b.conversationWith(a)!!.id).map { it.text })
    }

    @Test
    fun `deleting a conversation takes its messages with it`() = runTest {
        val (a, b) = phones()
        val conversation = a.with(b)
        a.messenger.sendText(conversation, "adiós")
        runCurrent()

        a.messenger.deleteConversation(conversation)

        assertNull(a.messenger.conversation(conversation).first())
        assertTrue(a.store.all.isEmpty())
    }
}
