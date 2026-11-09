/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.di

import com.bobbyesp.sona.BuildConfig
import com.bobbyesp.sona.accounts.data.accountsRepository
import com.bobbyesp.sona.accounts.domain.AccountEngineSync
import com.bobbyesp.sona.accounts.domain.AccountsRepository
import com.bobbyesp.sona.accounts.domain.DeleteAccount
import com.bobbyesp.sona.accounts.domain.SaveAccount
import com.bobbyesp.sona.accounts.domain.SetAccountEnabled
import com.bobbyesp.sona.core.common.DefaultDispatchers
import com.bobbyesp.sona.core.common.DispatcherProvider
import com.bobbyesp.sona.core.common.StringProvider
import com.bobbyesp.sona.core.data.settings.createSettingsStore
import com.bobbyesp.sona.core.database.SonaDatabase
import com.bobbyesp.sona.core.database.createSonaDatabase
import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.settings.SettingsStore
import com.bobbyesp.sona.core.ui.AndroidStringProvider
import com.bobbyesp.sona.credentials.api.CredentialVault
import com.bobbyesp.sona.credentials.keystore.createCredentialVault
import com.bobbyesp.sona.shell.ShellViewModel
import com.bobbyesp.sona.sip.api.EngineConfig
import com.bobbyesp.sona.sip.api.SipEngine
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The scope of work that outlives every screen: the engine and its accounts today, calls later. A
 * type of its own, so it is injected by type and not by a name that could be mistyped.
 */
class ApplicationScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Starts the SIP engine, and keeps its accounts equal to the stored ones from then on. */
class EngineBootstrap(
    private val scope: ApplicationScope,
    private val engine: SipEngine,
    private val sync: AccountEngineSync,
) {
    fun start() {
        scope.launch {
            engine.start(EngineConfig(userAgent = "Sona", verboseLogs = BuildConfig.DEBUG))
            sync.run()
        }
    }
}

/** What every flavor shares. The flavor's `engineModule` says which [SipEngine] it runs on. */
val appModule = module {
    single { ApplicationScope() }
    single<DispatcherProvider> { DefaultDispatchers }
    single<StringProvider> { AndroidStringProvider(androidContext()) }
    single<SettingsStore> { androidContext().createSettingsStore() }

    // Accounts: stored on the phone, their passwords in the vault.
    single<SonaDatabase> { androidContext().createSonaDatabase() }
    single<AccountsRepository> { get<SonaDatabase>().accountsRepository() }
    single<CredentialVault> { androidContext().createCredentialVault() }
    factory { SaveAccount(get(), get()) { AccountId(UUID.randomUUID().toString()) } }
    factory { DeleteAccount(get(), get()) }
    factory { SetAccountEnabled(get()) }
    factory { AccountEngineSync(get(), get(), get()) }
    single { EngineBootstrap(get(), get(), get()) }

    viewModelOf(::ShellViewModel)
}
