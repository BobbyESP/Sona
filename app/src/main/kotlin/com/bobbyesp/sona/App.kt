/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona

import android.app.Application
import com.bobbyesp.sona.di.EngineBootstrap
import com.bobbyesp.sona.di.appModule
import com.bobbyesp.sona.di.engineModule
import com.bobbyesp.sona.feature.accounts.accountsModule
import com.bobbyesp.sona.feature.call.callModule
import com.bobbyesp.sona.feature.contacts.contactsModule
import com.bobbyesp.sona.feature.dialer.dialerModule
import com.bobbyesp.sona.feature.history.historyModule
import com.bobbyesp.sona.feature.onboarding.onboardingModule
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@App)
            modules(
                appModule,
                // From the flavor's source set: which SIP engine the app runs on.
                engineModule,
                // One line per feature.
                historyModule,
                contactsModule,
                dialerModule,
                accountsModule,
                onboardingModule,
                callModule,
            )
        }
        get<EngineBootstrap>().start()
    }
}
