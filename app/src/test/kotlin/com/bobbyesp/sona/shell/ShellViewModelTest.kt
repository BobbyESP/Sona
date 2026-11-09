/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.shell

import com.bobbyesp.sona.accounts.domain.AccountDraft
import com.bobbyesp.sona.accounts.domain.AccountEngineSync
import com.bobbyesp.sona.accounts.domain.InMemoryAccountsRepository
import com.bobbyesp.sona.accounts.domain.SaveAccount
import com.bobbyesp.sona.accounts.domain.SaveOutcome
import com.bobbyesp.sona.core.designsystem.components.RegistrationIndicator
import com.bobbyesp.sona.core.designsystem.theme.SonaThemeConfig
import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.Secret
import com.bobbyesp.sona.core.model.settings.AppSettings
import com.bobbyesp.sona.core.model.settings.AppearanceSettings
import com.bobbyesp.sona.core.model.settings.ThemeMode
import com.bobbyesp.sona.core.testing.InMemorySettingsStore
import com.bobbyesp.sona.core.testing.MainDispatcherRule
import com.bobbyesp.sona.credentials.api.InMemoryCredentialVault
import com.bobbyesp.sona.sip.api.EngineConfig
import com.bobbyesp.sona.sip.api.SipStatus
import com.bobbyesp.sona.sip.fake.FakeSipEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShellViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val settings = InMemorySettingsStore()
    private val accounts = InMemoryAccountsRepository()
    private val vault = InMemoryCredentialVault()
    private val save = SaveAccount(accounts, vault) { AccountId("lab") }

    @Test
    fun `startup waits for the settings, then follows them`() =
        runTest(main.dispatcher) {
            settings.set(AppearanceSettings.Theme, ThemeMode.Dark)
            val viewModel = ShellViewModel(settings, FakeSipEngine(this), accounts)

            assertNull(viewModel.startup.value)
            advanceUntilIdle()
            assertEquals(
                SonaThemeConfig(themeMode = ThemeMode.Dark),
                viewModel.startup.value?.theme,
            )
        }

    @Test
    fun `a first start without accounts is welcomed, and adding one ends that`() =
        runTest(main.dispatcher) {
            val viewModel = ShellViewModel(settings, FakeSipEngine(this), accounts)
            advanceUntilIdle()
            assertEquals(true, viewModel.startup.value?.needsOnboarding)

            save(AccountDraft(server = "lab.local", username = "102"), Secret("x"))
            advanceUntilIdle()

            assertEquals(false, viewModel.startup.value?.needsOnboarding)
            assertTrue(settings.observe(AppSettings.OnboardingDone).first())
        }

    @Test
    fun `the chip follows the default account's registration`() =
        runTest(main.dispatcher) {
            val engine = FakeSipEngine(this)
            engine.start(EngineConfig())
            val sync = launch { AccountEngineSync(accounts, vault, engine).run() }
            val viewModel = ShellViewModel(settings, engine, accounts)
            val collecting = backgroundScope.launch { viewModel.accountChip.collect {} }
            advanceUntilIdle()
            assertNull(viewModel.accountChip.value)

            val account =
                (save(AccountDraft(server = "lab.local", username = "102"), Secret("x"))
                        as SaveOutcome.Saved)
                    .account
            advanceUntilIdle()
            assertEquals(RegistrationIndicator.Registered, viewModel.accountChip.value?.indicator)
            assertEquals("lab.local · 102", viewModel.accountChip.value?.label)

            engine.failRegistration(account.id, SipStatus(403, "Forbidden"))
            advanceUntilIdle()
            assertEquals(RegistrationIndicator.Failed, viewModel.accountChip.value?.indicator)
            collecting.cancel()
            sync.cancel()
        }
}
