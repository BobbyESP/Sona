/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.di

import com.bobbyesp.sona.sip.api.SipEngine
import com.bobbyesp.sona.sip.api.VideoSurfaces
import com.bobbyesp.sona.sip.fake.FakeSipEngine
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * The demo flavor: the fake engine, which accepts any account after a moment as a real server
 * would, and plays out calls by themselves (486 is busy, 603 declines, 404 does not exist; a call
 * dialed with video is answered with video, 555 turns its camera on later, 777 is on a weak
 * network). Every screen works as in the full app, with no server at all, and the video is test
 * patterns painted on the call screen's views.
 * Messages go to the demo's other phones ([DemoPeers]): 100 is a Sona phone, 101 a plain one.
 */
val engineModule = module {
    single<SipEngine> {
        FakeSipEngine(get<ApplicationScope>(), demoCalls = true, clock = System::currentTimeMillis)
            .also { DemoPeers(androidContext(), it.messaging, get<ApplicationScope>()).install() }
    }
    single<VideoSurfaces> { DemoVideoSurfaces(get(), get<ApplicationScope>()) }
}
