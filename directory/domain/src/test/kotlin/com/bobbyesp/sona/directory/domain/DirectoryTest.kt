/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.directory.domain

import com.bobbyesp.sona.calling.domain.Call
import com.bobbyesp.sona.calling.domain.CallState
import com.bobbyesp.sona.calling.domain.EndReason
import com.bobbyesp.sona.core.model.CallId
import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.sip.api.CallDirection
import com.bobbyesp.sona.sip.api.SipStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class T9Test {
    @Test
    fun `letters type as the keys of a phone`() {
        assertEquals("5862", T9.keys("Luna"))
        assertEquals("2662", T9.keys("anna"))
    }

    @Test
    fun `the keys find the start of the name or of any word in it`() {
        assertTrue(T9.matchesName("5862", "Luna Gómez"))
        assertTrue(T9.matchesName("466", "Ana Gómez"))
        assertFalse(T9.matchesName("5863", "Luna Gómez"))
    }

    @Test
    fun `accents and case do not matter`() {
        assertTrue(T9.matchesName("2662", "ÁNNA"))
        assertTrue(T9.matchesName("6263", "Ñandú"))
    }

    @Test
    fun `an input that is not digits finds no one`() {
        assertFalse(T9.matchesName("", "Luna"))
        assertFalse(T9.matchesName("lu", "Luna"))
    }
}

class ContactTest {
    private val ext = SipAddress("102", "lab.local")

    @Test
    fun `an extension matches a call to its user`() {
        assertTrue(ContactNumber("Lab", "102").matches(ext))
        assertFalse(ContactNumber("Lab", "103").matches(ext))
    }

    @Test
    fun `a phone number matches with spaces and dashes ignored`() {
        val phone = SipAddress("+34600111222", "lab.local")
        assertTrue(ContactNumber("Mobile", "+34 600-111-222").matches(phone))
    }

    @Test
    fun `a SIP address matches on user and host`() {
        assertTrue(ContactNumber("SIP", "sip:102@LAB.local").matches(ext))
        assertFalse(ContactNumber("SIP", "sip:102@other.net").matches(ext))
    }

    @Test
    fun `Sona's own contact wins over the phone's`() {
        val device =
            Contact(
                "device:1",
                "Lab 102",
                listOf(ContactNumber("", "102")),
                source = ContactSource.Device,
            )
        val mine = Contact("local:1", "Ana", listOf(ContactNumber("", "102")))
        assertEquals(mine, listOf(device, mine).find(ext))
    }

    @Test
    fun `no number, no contact`() {
        assertNull(listOf(Contact("local:1", "Ana", emptyList())).find(ext))
    }
}

class SuggestionsTest {
    private val ana = Contact("local:1", "Ana Gómez", listOf(ContactNumber("Lab", "102")))
    private val luna =
        Contact(
            "local:2",
            "Luna",
            listOf(ContactNumber("Lab", "103"), ContactNumber("Mobile", "600111")),
        )

    private fun record(user: String, name: String? = null) =
        CallRecord(
            account = null,
            direction = CallDirection.Outgoing,
            outcome = CallOutcome.Answered,
            address = SipAddress(user, "lab.local"),
            name = name,
            startedAt = 1,
            durationMillis = 1,
        )

    @Test
    fun `letters typed on the keys find contacts by name`() {
        val found = suggestions("5862", listOf(ana, luna), emptyList(), limit = 4)
        assertEquals(listOf("103", "600111"), found.map { it.number })
        assertTrue(found.all { it.name == "Luna" })
    }

    @Test
    fun `digits find contacts by number and numbers called before`() {
        val found = suggestions("10", listOf(ana), listOf(record("105")), limit = 4)
        assertEquals(listOf("102", "105"), found.map { it.number })
        assertEquals(listOf(true, false), found.map { it.fromContact })
    }

    @Test
    fun `a number a contact has is offered once`() {
        val found = suggestions("102", listOf(ana), listOf(record("102")), limit = 4)
        assertEquals(1, found.size)
        assertEquals("Ana Gómez", found.single().name)
    }

    @Test
    fun `at most the limit`() {
        val many = (1..9).map { record("10$it") }
        assertEquals(3, suggestions("10", emptyList(), many, limit = 3).size)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CallRecorderTest {
    private val remote = SipAddress("102", "lab.local")

    private fun call(
        id: String,
        direction: CallDirection,
        state: CallState,
        connectedAt: Long? = null,
    ) =
        Call(
            id = CallId(id),
            account = null,
            direction = direction,
            remote = remote,
            remoteName = null,
            state = state,
            connectedAt = connectedAt,
        )

    private class World(
        val calls: MutableStateFlow<List<Call>> = MutableStateFlow(emptyList()),
        val history: InMemoryCallHistory = InMemoryCallHistory(),
        var now: Long = 1_000,
    )

    private fun TestScopeWorld(
        scope: kotlinx.coroutines.CoroutineScope,
    ): Pair<World, CallRecorder> {
        val world = World()
        val recorder =
            CallRecorder(
                world.calls,
                world.history,
                scope,
                names = { "Ana" },
                clock = { world.now },
            )
        recorder.start()
        return world to recorder
    }

    @Test
    fun `an answered call is recorded with its talk time`() = runTest {
        val (world, _) = TestScopeWorld(backgroundScope)
        world.calls.value =
            listOf(call("a", CallDirection.Outgoing, CallState.Active, connectedAt = 2_000))
        runCurrent()
        world.now = 62_000
        world.calls.value =
            listOf(
                call(
                    "a",
                    CallDirection.Outgoing,
                    CallState.Ended(EndReason.HungUp(byMe = true)),
                    connectedAt = 2_000,
                ),
            )
        runCurrent()

        val record = world.history.records.first().single()
        assertEquals(CallOutcome.Answered, record.outcome)
        assertEquals(60_000, record.durationMillis)
        assertEquals("Ana", record.name)
    }

    @Test
    fun `a call that ended is recorded once however long it lingers`() = runTest {
        val (world, _) = TestScopeWorld(backgroundScope)
        val ended = call("a", CallDirection.Incoming, CallState.Ended(EndReason.Missed))
        world.calls.value = listOf(ended)
        runCurrent()
        world.calls.value = listOf(ended.copy(muted = true))
        runCurrent()

        val record = world.history.records.first().single()
        assertEquals(CallOutcome.Missed, record.outcome)
        assertFalse(record.seen)
    }

    @Test
    fun `a busy extension is recorded with its status`() = runTest {
        val (world, _) = TestScopeWorld(backgroundScope)
        world.calls.value =
            listOf(
                call(
                    "a",
                    CallDirection.Outgoing,
                    CallState.Ended(EndReason.Busy(SipStatus(486, "Busy Here"))),
                ),
            )
        runCurrent()

        val record = world.history.records.first().single()
        assertEquals(CallOutcome.Busy, record.outcome)
        assertEquals("486 Busy Here", record.statusLine)
    }

    @Test
    fun `an incoming call refused here is declined, not missed`() = runTest {
        val (world, _) = TestScopeWorld(backgroundScope)
        world.calls.value =
            listOf(
                call("a", CallDirection.Incoming, CallState.Ended(EndReason.HungUp(byMe = true))),
            )
        runCurrent()

        assertEquals(CallOutcome.Declined, world.history.records.first().single().outcome)
    }

    @Test
    fun `a dialed call cancelled before it was answered is cancelled`() = runTest {
        val (world, _) = TestScopeWorld(backgroundScope)
        world.calls.value =
            listOf(call("a", CallDirection.Outgoing, CallState.Ended(EndReason.Cancelled)))
        runCurrent()

        assertEquals(CallOutcome.Cancelled, world.history.records.first().single().outcome)
    }
}

class RecentGroupsTest {
    private fun record(user: String, direction: CallDirection, outcome: CallOutcome, at: Long) =
        CallRecord(
            account = null,
            direction = direction,
            outcome = outcome,
            address = SipAddress(user, "lab.local"),
            name = null,
            startedAt = at,
            durationMillis = 0,
        )

    @Test
    fun `calls in a row with the same person and kind are one group`() {
        val list =
            listOf(
                record("102", CallDirection.Incoming, CallOutcome.Missed, 5),
                record("102", CallDirection.Incoming, CallOutcome.Missed, 4),
                record("102", CallDirection.Outgoing, CallOutcome.Answered, 3),
                record("103", CallDirection.Outgoing, CallOutcome.Answered, 2),
                record("102", CallDirection.Outgoing, CallOutcome.Answered, 1),
            )
        assertEquals(listOf(2, 1, 1, 1), list.groupedConsecutive().map { it.count })
    }

    @Test
    fun `a missed call does not group with an answered one`() {
        val list =
            listOf(
                record("102", CallDirection.Incoming, CallOutcome.Missed, 2),
                record("102", CallDirection.Incoming, CallOutcome.Answered, 1),
            )
        assertEquals(2, list.groupedConsecutive().size)
    }
}

class InMemoryHistoryTest {
    @Test
    fun `marking seen clears the unseen missed calls`() = runTest {
        val history = InMemoryCallHistory()
        history.add(
            CallRecord(
                account = null,
                direction = CallDirection.Incoming,
                outcome = CallOutcome.Missed,
                address = SipAddress("102", "lab.local"),
                name = null,
                startedAt = 1,
                durationMillis = 0,
                seen = false,
            ),
        )
        assertEquals(1, history.unseenMissed.first().size)
        history.markSeen()
        assertTrue(history.unseenMissed.first().isEmpty())
    }
}
