/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.shell

import com.bobbyesp.sona.core.designsystem.components.RegistrationIndicator
import com.bobbyesp.sona.core.designsystem.theme.SonaThemeConfig
import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.Secret
import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.core.model.settings.AppearanceSettings
import com.bobbyesp.sona.core.model.settings.ThemeMode
import com.bobbyesp.sona.core.testing.InMemorySettingsStore
import com.bobbyesp.sona.core.testing.MainDispatcherRule
import com.bobbyesp.sona.sip.api.AccountSpec
import com.bobbyesp.sona.sip.api.EngineConfig
import com.bobbyesp.sona.sip.api.RegistrationState
import com.bobbyesp.sona.sip.api.ServerEndpoint
import com.bobbyesp.sona.sip.api.SipStatus
import com.bobbyesp.sona.sip.api.Transport
import com.bobbyesp.sona.sip.fake.FakeSipEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShellViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val lab =
        AccountSpec(
            id = AccountId("lab"),
            label = "Lab",
            identity = SipAddress("102", "lab.local"),
            password = Secret("x"),
            registrar = ServerEndpoint("lab.local", 5060, Transport.Udp),
        )

    private val accounts =
        object : DefaultAccountSource {
            override val defaultAccount = flowOf(AccountSummary(lab.id, lab.label))
        }

    @Test
    fun `the theme waits for the settings, then follows them`() =
        runTest(main.dispatcher) {
            val settings = InMemorySettingsStore()
            settings.set(AppearanceSettings.Theme, ThemeMode.Dark)
            val viewModel = ShellViewModel(settings, FakeSipEngine(this), accounts)

            assertEquals(null, viewModel.themeConfig.value)
            advanceUntilIdle()
            assertEquals(SonaThemeConfig(themeMode = ThemeMode.Dark), viewModel.themeConfig.value)
        }

    @Test
    fun `the chip follows the default account's registration`() =
        runTest(main.dispatcher) {
            val engine = FakeSipEngine(this)
            val viewModel = ShellViewModel(InMemorySettingsStore(), engine, accounts)
            val collecting = backgroundScope.launch { viewModel.accountChip.collect {} }

            engine.start(EngineConfig())
            engine.setAccounts(listOf(lab))
            advanceUntilIdle()
            assertEquals(RegistrationIndicator.Registered, viewModel.accountChip.value?.indicator)

            engine.failRegistration(lab.id, SipStatus(403, "Forbidden"))
            advanceUntilIdle()
            assertEquals(RegistrationIndicator.Failed, viewModel.accountChip.value?.indicator)
            collecting.cancel()
        }

    @Test
    fun `every engine state has a word on the chip`() {
        assertEquals(RegistrationIndicator.Offline, null.toIndicator())
        assertEquals(RegistrationIndicator.Offline, RegistrationState.Unregistered.toIndicator())
        assertEquals(RegistrationIndicator.Offline, RegistrationState.Offline.toIndicator())
        assertEquals(RegistrationIndicator.Registering, RegistrationState.Registering.toIndicator())
        assertEquals(
            RegistrationIndicator.Failed,
            RegistrationState.Failed(SipStatus(401, "Unauthorized")).toIndicator(),
        )
    }
}
