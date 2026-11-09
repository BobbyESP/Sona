/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.di

import com.bobbyesp.sona.core.common.DefaultDispatchers
import com.bobbyesp.sona.core.common.DispatcherProvider
import com.bobbyesp.sona.core.common.StringProvider
import com.bobbyesp.sona.core.data.settings.createSettingsStore
import com.bobbyesp.sona.core.model.settings.SettingsStore
import com.bobbyesp.sona.core.ui.AndroidStringProvider
import com.bobbyesp.sona.shell.ShellViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The scope of work that outlives every screen: the engine's registrations today, calls later. A
 * type of its own, so it is injected by type and not by a name that could be mistyped.
 */
class ApplicationScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Starts the SIP engine with its accounts. Each flavor says how. */
fun interface EngineBootstrap {
    fun start()
}

/** What every flavor shares. */
val appModule = module {
    single { ApplicationScope() }
    single<DispatcherProvider> { DefaultDispatchers }
    single<StringProvider> { AndroidStringProvider(androidContext()) }
    single<SettingsStore> { androidContext().createSettingsStore() }
    viewModelOf(::ShellViewModel)
}
