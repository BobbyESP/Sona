/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.bobbyesp.sona.feature.messages

import com.bobbyesp.sona.accounts.domain.Account
import com.bobbyesp.sona.accounts.domain.InMemoryAccountsRepository
import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.Secret
import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.core.testing.InMemorySettingsStore
import com.bobbyesp.sona.core.testing.TestDispatchers
import com.bobbyesp.sona.directory.domain.Contact
import com.bobbyesp.sona.directory.domain.Directory
import com.bobbyesp.sona.directory.domain.FakeDeviceContacts
import com.bobbyesp.sona.directory.domain.InMemoryCallHistory
import com.bobbyesp.sona.directory.domain.InMemoryLocalContacts
import com.bobbyesp.sona.messaging.domain.CameraCaptures
import com.bobbyesp.sona.messaging.domain.InMemoryAttachmentFiles
import com.bobbyesp.sona.messaging.domain.InMemoryMessageStore
import com.bobbyesp.sona.messaging.domain.Messenger
import com.bobbyesp.sona.sip.api.AccountSpec
import com.bobbyesp.sona.sip.api.EngineConfig
import com.bobbyesp.sona.sip.api.ServerEndpoint
import com.bobbyesp.sona.sip.api.Transport
import com.bobbyesp.sona.sip.fake.FakeSipEngine
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher

/**
 * A phone for a screen's test: extension 102 on `lab.local` (and, with [second], 202 on another
 * server), registered on the fake engine, with a real messenger on in-memory storage. Everything
 * runs on [dispatcher], so the test controls time.
 */
class Lab(
    val dispatcher: TestDispatcher,
    contacts: List<Contact> = emptyList(),
    second: Boolean = false,
) {
    private val scope = CoroutineScope(dispatcher)
    val accounts = InMemoryAccountsRepository()
    val engine = FakeSipEngine(scope, registrationDelay = 0.seconds)
    val store = InMemoryMessageStore()
    val files = InMemoryAttachmentFiles()
    val settings = InMemorySettingsStore()
    val dispatchers = TestDispatchers(dispatcher)
    val captures = CameraCaptures { "content://captures/1" }
    val directory =
        Directory(
            InMemoryLocalContacts(contacts),
            FakeDeviceContacts(),
            InMemoryCallHistory(),
            settings,
        )
    val messenger =
        Messenger(
            engine = engine,
            store = store,
            files = files,
            preparer = files.preparer,
            accounts = accounts,
            settings = settings,
            scope = scope,
            clock = { dispatcher.scheduler.currentTime },
        )

    val lab = account("lab", "Lab", "102", "lab.local", default = true)
    val office = account("office", "Office", "202", "office.example")

    private val all = listOfNotNull(lab, office.takeIf { second })

    suspend fun boot() {
        all.forEach { accounts.upsert(it) }
        engine.start(EngineConfig())
        engine.setAccounts(
            all.map {
                AccountSpec(it.id, it.label, it.identity, Secret("x"), it.server)
            },
        )
        messenger.start()
    }

    fun peer(user: String) = SipAddress(user, "lab.local")

    private fun account(
        id: String,
        label: String,
        user: String,
        domain: String,
        default: Boolean = false,
    ) =
        Account(
            id = AccountId(id),
            label = label,
            color = 0,
            username = user,
            domain = domain,
            server = ServerEndpoint(domain, 5060, Transport.Udp),
            isDefault = default,
        )
}
