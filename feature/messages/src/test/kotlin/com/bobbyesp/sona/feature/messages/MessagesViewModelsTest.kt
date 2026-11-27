/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.messages

import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.core.navigation.keys.NewConversation
import com.bobbyesp.sona.core.testing.MainDispatcherRule
import com.bobbyesp.sona.directory.domain.Contact
import com.bobbyesp.sona.directory.domain.ContactNumber
import com.bobbyesp.sona.feature.messages.conversation.ConversationIntent
import com.bobbyesp.sona.feature.messages.conversation.ConversationViewModel
import com.bobbyesp.sona.feature.messages.conversation.ThreadItem
import com.bobbyesp.sona.feature.messages.list.ConversationsIntent
import com.bobbyesp.sona.feature.messages.list.ConversationsViewModel
import com.bobbyesp.sona.feature.messages.start.NewConversationIntent
import com.bobbyesp.sona.feature.messages.start.NewConversationViewModel
import com.bobbyesp.sona.feature.messages.start.StartProblem
import com.bobbyesp.sona.messaging.domain.ConversationId
import com.bobbyesp.sona.messaging.domain.MessageStatus
import com.bobbyesp.sona.messaging.ui.R as WordsR
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MessagesViewModelsTest {

    @get:Rule val main = MainDispatcherRule()

    private fun TestScope.settle() {
        advanceTimeBy(1.seconds)
        runCurrent()
    }

    private suspend fun TestScope.lab(
        contacts: List<Contact> = emptyList(),
        second: Boolean = false,
    ): Lab {
        val lab = Lab(main.dispatcher, contacts, second)
        lab.boot()
        settle()
        return lab
    }

    // --- The list -------------------------------------------------------------------------------

    @Test
    fun `the list shows the most recent first, with what is unread, and searches`() =
        runTest(main.dispatcher) {
            val lab = lab()
            lab.engine.messaging.deliver(
                from = lab.peer("103"),
                body = "hello",
                account = lab.lab.id,
            )
            settle()
            val other = lab.messenger.open(lab.lab.id, lab.peer("104"))!!
            lab.messenger.sendText(other, "lunch?")
            settle()

            val list = ConversationsViewModel(lab.messenger, lab.accounts)
            settle()
            val rows = list.state.value.rows
            assertEquals(listOf("104", "103"), rows.map { it.title })
            assertEquals(1, rows[1].summary.unread)
            assertEquals("lunch?", rows[0].summary.last?.text)
            // One account: no account is marked.
            assertNull(rows[0].account)

            list.onSendIntent(ConversationsIntent.Query("hel"))
            settle()
            assertEquals(listOf("103"), list.state.value.rows.map { it.title })
            assertEquals(2, list.state.value.total)
        }

    @Test
    fun `deleting a conversation asks first`() =
        runTest(main.dispatcher) {
            val lab = lab()
            lab.messenger.open(lab.lab.id, lab.peer("104"))
            settle()
            val list = ConversationsViewModel(lab.messenger, lab.accounts)
            settle()
            val row = list.state.value.rows.single()

            list.onSendIntent(ConversationsIntent.AskDelete(row))
            assertEquals(row, list.state.value.confirmDelete)
            list.onSendIntent(ConversationsIntent.DismissDelete)
            settle()
            assertEquals(1, list.state.value.rows.size)

            list.onSendIntent(ConversationsIntent.AskDelete(row))
            list.onSendIntent(ConversationsIntent.ConfirmDelete)
            settle()
            assertTrue(list.state.value.rows.isEmpty())
            assertNull(list.state.value.confirmDelete)
        }

    // --- New message ----------------------------------------------------------------------------

    @Test
    fun `a party's Message opens the conversation at once with one account`() =
        runTest(main.dispatcher) {
            val lab = lab()
            val start = newConversation(lab, NewConversation(address = "105"))
            settle()

            val opened = start.state.value.opened!!
            val summary = lab.messenger.conversation(opened).first()!!
            assertEquals(SipAddress("105", "lab.local"), summary.conversation.peer)
            assertEquals(lab.lab.id, summary.conversation.account)
        }

    @Test
    fun `with several accounts, the account is chosen before writing`() =
        runTest(main.dispatcher) {
            val lab = lab(second = true)
            val start = newConversation(lab, NewConversation(address = "205"))
            settle()
            assertNull(start.state.value.opened)
            assertTrue(start.state.value.choosesAccount)
            // The default is chosen until the user picks another.
            assertEquals(lab.lab.id, start.state.value.accountId)

            start.onSendIntent(NewConversationIntent.ChooseAccount(lab.office.id))
            assertEquals("sip:205@office.example", start.state.value.typedAddress)
            start.onSendIntent(NewConversationIntent.WriteTyped)
            settle()
            val summary = lab.messenger.conversation(start.state.value.opened!!).first()!!
            assertEquals(lab.office.id, summary.conversation.account)
            assertEquals(SipAddress("205", "office.example"), summary.conversation.peer)
        }

    @Test
    fun `a history entry's account is the one written from`() =
        runTest(main.dispatcher) {
            val lab = lab(second = true)
            val start =
                newConversation(
                    lab,
                    NewConversation(address = "sip:207@office.example", accountId = "office"),
                )
            settle()
            val summary = lab.messenger.conversation(start.state.value.opened!!).first()!!
            assertEquals(lab.office.id, summary.conversation.account)
        }

    @Test
    fun `contacts are searched by name and number, and what is not an address is said`() =
        runTest(main.dispatcher) {
            val ana = Contact("local:1", "Ana Gómez", listOf(ContactNumber("Lab", "106")))
            val luis = Contact("local:2", "Luis", listOf(ContactNumber("Lab", "107")))
            val lab = lab(contacts = listOf(ana, luis))
            val start = newConversation(lab, NewConversation())
            settle()
            assertEquals(2, start.state.value.contacts.size)

            start.onSendIntent(NewConversationIntent.Query("ana"))
            settle()
            assertEquals(listOf(ana), start.state.value.contacts)
            start.onSendIntent(NewConversationIntent.Query("107"))
            settle()
            assertEquals(listOf(luis), start.state.value.contacts)

            start.onSendIntent(NewConversationIntent.Query("not an address!"))
            settle()
            assertNull(start.state.value.typedAddress)
            start.onSendIntent(NewConversationIntent.WriteTyped)
            settle()
            assertEquals(StartProblem.NotAnAddress, start.state.value.problem)
            assertNull(start.state.value.opened)

            start.onSendIntent(NewConversationIntent.Pick("107"))
            settle()
            assertNotNull(start.state.value.opened)
        }

    // --- A conversation -------------------------------------------------------------------------

    @Test
    fun `what is written is sent, and the field empties`() =
        runTest(main.dispatcher) {
            val lab = lab()
            val id = lab.messenger.open(lab.lab.id, lab.peer("108"))!!
            val conversation = conversation(lab, id)
            settle()
            val header = conversation.state.value.header!!
            assertEquals("108", header.title)
            assertEquals("sip:108@lab.local", header.address)
            // Nothing was heard from 108: the note about plain phones is shown.
            assertTrue(header.plainPeer)

            conversation.onSendIntent(ConversationIntent.Draft("Hola"))
            conversation.onSendIntent(ConversationIntent.Send)
            settle()
            assertEquals("", conversation.state.value.draft)
            val bubble =
                conversation.state.value.items.filterIsInstance<ThreadItem.Bubble>().single()
            assertEquals("Hola", bubble.message.text)
            assertEquals(MessageStatus.Sent, bubble.message.status)
            assertTrue(conversation.state.value.items.first() is ThreadItem.Day)
        }

    @Test
    fun `a text too long for the server stays in the field, with why`() =
        runTest(main.dispatcher) {
            val lab = lab()
            val id = lab.messenger.open(lab.lab.id, lab.peer("108"))!!
            val conversation = conversation(lab, id)
            settle()

            val long = "palabra ".repeat(10_000)
            conversation.onSendIntent(ConversationIntent.Draft(long))
            conversation.onSendIntent(ConversationIntent.Send)
            settle()
            assertEquals(long, conversation.state.value.draft)
            assertEquals(
                WordsR.string.send_too_long,
                conversation.state.value.problem?.sentence?.res,
            )
            assertTrue(conversation.state.value.items.isEmpty())

            conversation.onSendIntent(ConversationIntent.Draft("short"))
            assertNull(conversation.state.value.problem)
        }

    @Test
    fun `while shown, what arrives is read`() =
        runTest(main.dispatcher) {
            val lab = lab()
            lab.engine.messaging.deliver(from = lab.peer("109"), body = "one", account = lab.lab.id)
            settle()
            val id = lab.messenger.conversations.first().single().id
            assertEquals(1, lab.messenger.totalUnread.first())

            val conversation = conversation(lab, id)
            conversation.onSendIntent(ConversationIntent.Visible(true))
            settle()
            assertEquals(0, lab.messenger.totalUnread.first())

            lab.engine.messaging.deliver(from = lab.peer("109"), body = "two", account = lab.lab.id)
            settle()
            assertEquals(0, lab.messenger.totalUnread.first())

            conversation.onSendIntent(ConversationIntent.Visible(false))
            lab.engine.messaging.deliver(
                from = lab.peer("109"),
                body = "three",
                account = lab.lab.id,
            )
            settle()
            assertEquals(1, lab.messenger.totalUnread.first())
        }

    @Test
    fun `a message is deleted, and a deleted conversation leaves`() =
        runTest(main.dispatcher) {
            val lab = lab()
            val id = lab.messenger.open(lab.lab.id, lab.peer("110"))!!
            lab.messenger.sendText(id, "first")
            lab.messenger.sendText(id, "second")
            val conversation = conversation(lab, id)
            settle()
            val first =
                conversation.state.value.items.filterIsInstance<ThreadItem.Bubble>().first().message

            conversation.onSendIntent(ConversationIntent.Details(first.id))
            assertEquals(first.id, conversation.state.value.details?.id)
            conversation.onSendIntent(ConversationIntent.Delete(first.id))
            settle()
            assertNull(conversation.state.value.details)
            assertEquals(
                listOf("second"),
                conversation.state.value.items.filterIsInstance<ThreadItem.Bubble>().map {
                    it.message.text
                },
            )

            assertFalse(conversation.state.value.gone)
            lab.messenger.deleteConversation(id)
            settle()
            assertTrue(conversation.state.value.gone)
        }

    @Test
    fun `the camera writes where the captures say, and a picture taken is sent`() =
        runTest(main.dispatcher) {
            val lab = lab()
            val id = lab.messenger.open(lab.lab.id, lab.peer("111"))!!
            lab.files.pick("content://captures/1", "IMG_1.jpg", ByteArray(2_000) { it.toByte() })
            val conversation = conversation(lab, id)
            settle()

            conversation.onSendIntent(
                ConversationIntent.PictureTaken("content://captures/1", taken = true),
            )
            settle()
            val bubble =
                conversation.state.value.items.filterIsInstance<ThreadItem.Bubble>().single()
            assertEquals("IMG_1.jpg", bubble.message.attachment?.name)
            assertFalse(conversation.state.value.preparing)

            // Not taken (the user went back): nothing is sent.
            conversation.onSendIntent(
                ConversationIntent.PictureTaken("content://captures/2", taken = false),
            )
            settle()
            assertEquals(1, conversation.state.value.items.count { it is ThreadItem.Bubble })
        }

    private fun newConversation(lab: Lab, key: NewConversation) =
        NewConversationViewModel(key, lab.messenger, lab.directory, lab.accounts)

    private fun conversation(lab: Lab, id: ConversationId) =
        ConversationViewModel(id.value, lab.messenger, lab.accounts, lab.captures, lab.dispatchers)
}
