/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobbyesp.sona.core.designsystem.components.RegistrationIndicator
import com.bobbyesp.sona.core.designsystem.theme.SonaThemeConfig
import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.settings.AppearanceSettings
import com.bobbyesp.sona.core.model.settings.SettingsStore
import com.bobbyesp.sona.sip.api.RegistrationState
import com.bobbyesp.sona.sip.api.SipEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** An account as the shell shows it: in the account chip, and later the switcher. */
data class AccountSummary(val id: AccountId, val label: String, val color: Int? = null)

/**
 * Where the shell learns which account is the default. Phase 0 has no account storage: the demo
 * flavor provides one. Phase 1 binds the accounts repository here instead.
 */
interface DefaultAccountSource {
    val defaultAccount: Flow<AccountSummary?>
}

/** The account chip: which account, and in a word whether it can call. */
data class AccountChipState(
    val label: String,
    val indicator: RegistrationIndicator,
    val color: Int? = null,
)

/**
 * What the shell needs before and while drawing anything: the theme and the account chip. Not a
 * screen's ViewModel, so it takes no intents; it lives as long as the activity.
 */
class ShellViewModel(
    settings: SettingsStore,
    engine: SipEngine,
    accounts: DefaultAccountSource,
) : ViewModel() {

    /** `null` until the settings are read, which the splash screen waits for. */
    val themeConfig: StateFlow<SonaThemeConfig?> =
        combine(
                settings.observe(AppearanceSettings.Theme),
                settings.observe(AppearanceSettings.DynamicColor),
                settings.observe(AppearanceSettings.SeedColor),
                settings.observe(AppearanceSettings.Palette),
                settings.observe(AppearanceSettings.PureBlack),
            ) { mode, dynamic, seed, palette, black ->
                SonaThemeConfig(mode, dynamic, seed, palette, black)
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val accountChip: StateFlow<AccountChipState?> =
        combine(accounts.defaultAccount, engine.registrations) { account, registrations ->
                account?.let {
                    AccountChipState(
                        label = it.label,
                        indicator = registrations[it.id].toIndicator(),
                        color = it.color,
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** What the chip says for an engine's state; an account the engine does not have is offline. */
internal fun RegistrationState?.toIndicator(): RegistrationIndicator =
    when (this) {
        RegistrationState.Registered -> RegistrationIndicator.Registered
        RegistrationState.Registering -> RegistrationIndicator.Registering
        is RegistrationState.Failed -> RegistrationIndicator.Failed
        RegistrationState.Offline,
        RegistrationState.Unregistered,
        null -> RegistrationIndicator.Offline
    }
