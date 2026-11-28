/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.di

import com.bobbyesp.sona.BuildConfig
import com.bobbyesp.sona.sip.api.SipEngine
import com.bobbyesp.sona.sip.api.VideoSurfaces
import com.bobbyesp.sona.sip.linphone.createLinphoneEngine
import com.bobbyesp.sona.sip.linphone.linphoneVideoSurfaces
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** The full flavor: Linphone, registering to the user's own server. */
val engineModule = module {
    single { androidContext().createLinphoneEngine(appVersion = BuildConfig.VERSION_NAME) }
    single<VideoSurfaces> { get<SipEngine>().linphoneVideoSurfaces() }
}
