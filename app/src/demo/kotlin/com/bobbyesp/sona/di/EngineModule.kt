/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.di

import com.bobbyesp.sona.sip.api.SipEngine
import com.bobbyesp.sona.sip.fake.FakeSipEngine
import org.koin.dsl.module

/**
 * The demo flavor: the fake engine, which accepts any account after a moment as a real server
 * would. Accounts, onboarding and every screen work as in the full app, with no server at all.
 */
val engineModule = module { single<SipEngine> { FakeSipEngine(get<ApplicationScope>()) } }
