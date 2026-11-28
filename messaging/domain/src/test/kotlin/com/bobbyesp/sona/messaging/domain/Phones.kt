/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.bobbyesp.sona.messaging.domain

import com.bobbyesp.sona.accounts.domain.Account
import com.bobbyesp.sona.accounts.domain.InMemoryAccountsRepository
import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.Secret
import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.core.testing.InMemorySettingsStore
import com.bobbyesp.sona.messaging.domain.envelope.Envelope
import com.bobbyesp.sona.messaging.domain.envelope.EnvelopeCodec
import com.bobbyesp.sona.sip.api.AccountSpec
import com.bobbyesp.sona.sip.api.EngineConfig
import com.bobbyesp.sona.sip.api.MessageSendResult
import com.bobbyesp.sona.sip.api.ServerEndpoint
import com.bobbyesp.sona.sip.api.Transport
import com.bobbyesp.sona.sip.fake.FakeMessaging
import com.bobbyesp.sona.sip.fake.FakeSipEngine
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent

/** The lab server's domain, where every test extension is. */
const val LAB = "lab.local"

/**
 * One phone of a test: an extension registered on [LAB], with its own engine, storage and
 * messenger. [restart] makes a new messenger on the same storage, as the app does after being
 * closed.
 */
class Phone(
    private val test: TestScope,
    val user: String,
    val store: InMemoryMessageStore = InMemoryMessageStore(),
    val files: InMemoryAttachmentFiles = InMemoryAttachmentFiles(),
    val settings: InMemorySettingsStore = InMemorySettingsStore(),
) {
    val account = AccountId("acc-$user")
    val address = SipAddress(user, LAB)
    val accounts = InMemoryAccountsRepository()
    val engine = FakeSipEngine(test.backgroundScope, registrationDelay = 0.seconds)
    val messaging: FakeMessaging
        get() = engine.messaging

    private var seed = user.hashCode()
    private var run: Job? = null
    lateinit var messenger: Messenger
        private set

    suspend fun boot() {
        accounts.upsert(
            Account(
                id = account,
                label = "Lab",
                color = 0,
                username = user,
                domain = LAB,
                server = ServerEndpoint(LAB, 5060, Transport.Udp),
                isDefault = true,
            ),
        )
        engine.start(EngineConfig())
        engine.setAccounts(
            listOf(
                AccountSpec(
                    id = account,
                    label = "Lab",
                    identity = address,
                    password = Secret("x"),
                    registrar = ServerEndpoint(LAB, 5060, Transport.Udp),
                ),
            ),
        )
        start()
    }

    private fun start() {
        val random = Random(seed++)
        val parent = test.backgroundScope.coroutineContext
        val job = Job(parent[Job]).also { run = it }
        messenger =
            Messenger(
                engine = engine,
                store = store,
                files = files,
                preparer = files.preparer,
                accounts = accounts,
                settings = settings,
                scope = CoroutineScope(parent + job),
                clock = { test.testScheduler.currentTime },
                newId = { EnvelopeCodec.newId(random) },
            )
        messenger.start()
        test.runCurrent()
    }

    /** The app is closed and opened again: what was in memory is gone, the storage is not. */
    fun restart() {
        run?.cancel()
        test.runCurrent()
        start()
    }

    /** The conversation with [peer], opened from here. */
    suspend fun with(peer: Phone): ConversationId = messenger.open(account, peer.address)!!

    suspend fun messages(conversation: ConversationId): List<Message> =
        store.messages(conversation).first()

    suspend fun only(conversation: ConversationId): Message = messages(conversation).single()

    /** What this phone sent to [to], decoded. */
    fun sentTo(to: Phone): List<Envelope> = messaging.sentTo(to.address).map(EnvelopeCodec::decode)
}

/**
 * Wires [a] and [b] through a server that takes every body ([FakeMessaging.ACCEPTED]) and brings it
 * to the other, unless [lose] says the body is lost on its way (the server still took it).
 */
fun link(
    a: Phone,
    b: Phone,
    lose: (from: Phone, body: String) -> Boolean = { _, _ -> false },
    answer: (from: Phone, body: String) -> MessageSendResult = { _, _ -> FakeMessaging.ACCEPTED },
) {
    fun route(from: Phone, to: Phone) {
        from.messaging.server = { sent ->
            val result = answer(from, sent.body)
            if (
                result is MessageSendResult.Accepted &&
                    sent.to == to.address &&
                    !lose(from, sent.body)
            ) {
                to.messaging.deliver(from = from.address, body = sent.body, account = to.account)
            }
            result
        }
    }
    route(a, b)
    route(b, a)
}

/** Lets the phones talk for [seconds] of virtual time. */
fun TestScope.wait(seconds: Int) {
    advanceTimeBy(seconds.seconds)
    runCurrent()
}
