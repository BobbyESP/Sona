/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.di

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.Secret
import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.shell.AccountSummary
import com.bobbyesp.sona.shell.DefaultAccountSource
import com.bobbyesp.sona.sip.api.AccountSpec
import com.bobbyesp.sona.sip.api.EngineConfig
import com.bobbyesp.sona.sip.api.ServerEndpoint
import com.bobbyesp.sona.sip.api.SipEngine
import com.bobbyesp.sona.sip.api.Transport
import com.bobbyesp.sona.sip.fake.FakeSipEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.koin.dsl.module

/*
 * The demo flavor: the fake engine, with one account on a lab server that does not exist. It
 * registers after a moment, as a real one would, so the app can be built and shown with no server.
 */

private val DemoAccount =
    AccountSpec(
        id = AccountId("demo"),
        label = "Lab Asterisk · 102",
        identity = SipAddress("102", "lab.local"),
        password = Secret("demo"),
        registrar = ServerEndpoint("lab.local", 5060, Transport.Udp),
    )

private object DemoAccounts : DefaultAccountSource {
    override val defaultAccount: Flow<AccountSummary?> =
        flowOf(AccountSummary(DemoAccount.id, DemoAccount.label))
}

val engineModule = module {
    single<SipEngine> { FakeSipEngine(get<ApplicationScope>()) }
    single<DefaultAccountSource> { DemoAccounts }
    single {
        val engine = get<SipEngine>()
        val scope = get<ApplicationScope>()
        EngineBootstrap {
            scope.launch {
                engine.start(EngineConfig())
                engine.setAccounts(listOf(DemoAccount))
            }
        }
    }
}
