/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.settings

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.sona.core.navigation.Navigator
import com.bobbyesp.sona.core.navigation.keys.Settings
import com.bobbyesp.sona.core.navigation.keys.SettingsPage
import com.bobbyesp.sona.core.ui.contribution.NavSection
import org.koin.compose.viewmodel.koinViewModel

internal object SettingsSection : NavSection {
    override fun EntryProviderScope<NavKey>.register(navigator: Navigator) {
        entry<Settings> {
            val viewModel: SettingsViewModel = koinViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            SettingsRootScreen(
                state = state,
                store = viewModel.store,
                onIntent = viewModel::onSendIntent,
                onOpenArea = { area -> navigator.goTo(SettingsPage(area.id)) },
                onOpenKey = navigator::goTo,
                onBack = navigator::goBack,
            )
        }
        entry<SettingsPage> { key ->
            val viewModel: SettingsViewModel = koinViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            SettingsAreaScreen(
                entry = state.areas.firstOrNull { it.area.id == key.areaId },
                store = viewModel.store,
                onIntent = viewModel::onSendIntent,
                onOpenKey = navigator::goTo,
                onBack = navigator::goBack,
            )
        }
    }
}
