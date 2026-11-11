/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.accounts

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.sona.core.navigation.Navigator
import com.bobbyesp.sona.core.navigation.keys.AccountEditor
import com.bobbyesp.sona.core.navigation.keys.AccountSwitcher
import com.bobbyesp.sona.core.navigation.keys.CallReadiness
import com.bobbyesp.sona.core.navigation.keys.DeleteAccount
import com.bobbyesp.sona.core.navigation.keys.Onboarding
import com.bobbyesp.sona.core.navigation.keys.isAccountSetup
import com.bobbyesp.sona.core.navigation.overlay.OverlayPreference
import com.bobbyesp.sona.core.navigation.overlay.OverlaySceneStrategy
import com.bobbyesp.sona.core.ui.contribution.NavSection
import com.bobbyesp.sona.feature.accounts.delete.DeleteAccountRoute
import com.bobbyesp.sona.feature.accounts.delete.DeleteAccountViewModel
import com.bobbyesp.sona.feature.accounts.editor.AccountEditorRoute
import com.bobbyesp.sona.feature.accounts.editor.AccountEditorViewModel
import com.bobbyesp.sona.feature.accounts.switcher.AccountSwitcherRoute
import com.bobbyesp.sona.feature.accounts.switcher.AccountSwitcherViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.parameter.parametersOf
import org.koin.dsl.bind
import org.koin.dsl.module

internal object AccountsSection : NavSection {
    override fun EntryProviderScope<NavKey>.register(navigator: Navigator) {
        entry<AccountSwitcher>(
            metadata = OverlaySceneStrategy.overlay(OverlayPreference.AlwaysSheet),
        ) {
            AccountSwitcherRoute(
                viewModel = koinViewModel(),
                onReadiness = { navigator.replaceTop(CallReadiness()) },
                // The sheet gives way to the editor, rather than staying under it.
                onEdit = { id -> navigator.replaceTop(AccountEditor(id.value)) },
                onAdd = { navigator.replaceTop(AccountEditor()) },
            )
        }
        entry<AccountEditor> { key ->
            AccountEditorRoute(
                viewModel = koinViewModel(key = key.toString()) { parametersOf(key.accountId) },
                onBack = navigator::goBack,
                onLeave = {
                    // A first account added from the welcome: what calls need comes next.
                    var fromWelcome = false
                    navigator.goBackWhile { open ->
                        if (open == Onboarding) fromWelcome = true
                        isAccountSetup(open)
                    }
                    if (fromWelcome) navigator.goTo(CallReadiness(firstStart = true))
                },
                onConfirmDelete = { id -> navigator.goTo(DeleteAccount(id)) },
            )
        }
        entry<DeleteAccount>(metadata = OverlaySceneStrategy.overlay()) { key ->
            DeleteAccountRoute(
                viewModel = koinViewModel(key = key.toString()) { parametersOf(key.accountId) },
                onDismiss = navigator::goBack,
                // Its editor goes too: its subject is gone.
                onDeleted = {
                    navigator.goBack()
                    navigator.removeDestination(AccountEditor(key.accountId))
                },
            )
        }
    }
}

/** Swaps the destination on top for [key], so back skips what was replaced. */
private fun Navigator.replaceTop(key: NavKey) {
    goBack()
    goTo(key)
}

val accountsModule = module {
    single { AccountsSection } bind NavSection::class
    viewModelOf(::AccountSwitcherViewModel)
    viewModel { params ->
        AccountEditorViewModel(
            accountId = params.getOrNull(),
            savedState = get(),
            repository = get(),
            saveAccount = get(),
            engine = get(),
            settings = get(),
        )
    }
    viewModel { params -> DeleteAccountViewModel(params.get(), get(), get()) }
}
