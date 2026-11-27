/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.sona.accounts.domain.Account
import com.bobbyesp.sona.accounts.domain.AccountsRepository
import com.bobbyesp.sona.accounts.domain.defaultAccount
import com.bobbyesp.sona.accounts.ui.toIndicator
import com.bobbyesp.sona.core.designsystem.components.RegistrationIndicator
import com.bobbyesp.sona.core.designsystem.theme.SonaThemeConfig
import com.bobbyesp.sona.core.model.settings.AppSettings
import com.bobbyesp.sona.core.model.settings.AppearanceSettings
import com.bobbyesp.sona.core.model.settings.SettingsStore
import com.bobbyesp.sona.sip.api.RegistrationState
import com.bobbyesp.sona.sip.api.SipEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The account chip: which account, and in a word whether it can call. */
data class AccountChipState(
    val label: String,
    val indicator: RegistrationIndicator,
    val color: Int? = null,
)

/** What the shell needs at start: the theme, and whether to welcome the user first. */
data class ShellStartup(val theme: SonaThemeConfig, val needsOnboarding: Boolean)

/**
 * What the shell needs before and while drawing anything: the theme, whether this is a first start,
 * and the account chip. Not a screen's ViewModel, so it takes no intents.
 */
class ShellViewModel(
    private val settings: SettingsStore,
    engine: SipEngine,
    accounts: AccountsRepository,
) : ViewModel() {

    private val theme =
        combine(
            combine(
                settings.observe(AppearanceSettings.Theme),
                settings.observe(AppearanceSettings.DynamicColor),
                settings.observe(AppearanceSettings.SeedColor),
                settings.observe(AppearanceSettings.Palette),
                settings.observe(AppearanceSettings.PureBlack),
            ) { mode, dynamic, seed, palette, black ->
                SonaThemeConfig(mode, dynamic, seed, palette, black)
            },
            settings.observe(AppearanceSettings.Blur),
        ) { config, blur ->
            config.copy(blur = blur)
        }

    /** `null` until settings and accounts are read, which the splash screen waits for. */
    val startup: StateFlow<ShellStartup?> =
        combine(theme, accounts.accounts, settings.observe(AppSettings.OnboardingDone)) {
                theme,
                all,
                onboarded ->
                ShellStartup(theme = theme, needsOnboarding = all.isEmpty() && !onboarded)
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** `null` while there is no account: the chip then offers to add one. */
    val accountChip: StateFlow<AccountChipState?> =
        combine(accounts.defaultAccount(), engine.registrations) { account, registrations ->
                account?.let { chipFor(it, registrations[it.id]) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _request = MutableStateFlow<NavKey?>(null)

    /**
     * A destination asked for from outside the screens (a message's notification), standing until
     * the shell has shown it: the activity may be asked before the shell is drawn.
     */
    val request: StateFlow<NavKey?> = _request.asStateFlow()

    fun request(key: NavKey) {
        _request.value = key
    }

    /** The shell showed [key]; a request made since stands. */
    fun requestShown(key: NavKey) {
        _request.compareAndSet(key, null)
    }

    init {
        // Once an account exists, the welcome has done its work, however the account was added.
        viewModelScope.launch {
            accounts.accounts.collect { all ->
                if (all.isNotEmpty()) settings.set(AppSettings.OnboardingDone, true)
            }
        }
    }
}

internal fun chipFor(account: Account, registration: RegistrationState?) =
    AccountChipState(
        label = account.label,
        indicator =
            if (account.enabled) registration.toIndicator() else RegistrationIndicator.Offline,
        color = account.color,
    )
