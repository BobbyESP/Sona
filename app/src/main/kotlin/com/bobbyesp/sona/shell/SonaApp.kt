/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.shell

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import com.bobbyesp.sona.accounts.ui.AddAccountChip
import com.bobbyesp.sona.core.designsystem.components.RegistrationChip
import com.bobbyesp.sona.core.navigation.SonaNavDisplay
import com.bobbyesp.sona.core.navigation.currentTopLevel
import com.bobbyesp.sona.core.navigation.keys.AccountEditor
import com.bobbyesp.sona.core.navigation.keys.AccountSwitcher
import com.bobbyesp.sona.core.navigation.keys.Onboarding
import com.bobbyesp.sona.core.navigation.overlay.OverlaySceneStrategy
import com.bobbyesp.sona.core.navigation.overlay.rememberOverlaySceneStrategy
import com.bobbyesp.sona.core.navigation.pane.sharingTheWindow
import com.bobbyesp.sona.core.navigation.rememberNavigator
import com.bobbyesp.sona.core.ui.contribution.LocalAccountChip
import com.bobbyesp.sona.core.ui.contribution.NavSection
import com.bobbyesp.sona.core.ui.contribution.TopLevelDestination
import org.koin.compose.getKoin

/**
 * The shell: the navigation bar (a rail on wider windows) built from the features' tabs, and the
 * one back stack every feature's destinations are drawn from.
 *
 * - The tabs and destinations are contributions ([TopLevelDestination], [NavSection]); the shell
 *   names no feature, so a new one adds itself.
 * - The back stack survives configuration changes and process death ([rememberNavBackStack]), and
 *   starts at the first tab. Back from another tab returns to it; back from it leaves the app.
 * - Each top bar shows the account chip, provided here through [LocalAccountChip]: it opens the
 *   account switcher, or the editor when there is no account yet.
 * - On a first start ([needsOnboarding]) the welcome rises over the first tab.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SonaApp(
    accountChip: AccountChipState?,
    needsOnboarding: Boolean,
    modifier: Modifier = Modifier,
) {
    val koin = getKoin()
    val destinations = remember { koin.getAll<TopLevelDestination>().sortedBy { it.order } }
    val sections = remember { koin.getAll<NavSection>() }
    val topLevelKeys = remember(destinations) { destinations.map { it.key } }

    val backStack = rememberNavBackStack(destinations.first().key)
    val navigator = rememberNavigator(backStack)
    val current = backStack.currentTopLevel(topLevelKeys)

    val overlayStrategy = rememberOverlaySceneStrategy<NavKey>()
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()
    val sceneStrategies =
        remember(overlayStrategy, listDetailStrategy) {
            listOf(overlayStrategy, listDetailStrategy.sharingTheWindow())
        }
    // Remembered: entries are equal only while their content is the same instance, and a finished
    // back gesture needs the scene it showed to equal the one the stack ends on.
    val entries =
        remember(sections, navigator) {
            entryProvider<NavKey> {
                sections.forEach { section -> with(section) { register(navigator) } }
            }
        }

    // Also after a rotation or process death: the welcome is opened only if it is not already
    // somewhere on the stack, and only while there is no account and it was not skipped.
    LaunchedEffect(needsOnboarding) {
        if (needsOnboarding && Onboarding !in backStack) navigator.goTo(Onboarding)
    }

    // The bar is for moving between tabs: a full-screen destination that is not one (the welcome,
    // the account editor) has the screen to itself. A sheet over a tab keeps it.
    val top = backStack.lastOrNull()
    val showsBar =
        top == null ||
            top in topLevelKeys ||
            OverlaySceneStrategy.isShownAsOverlay(entries(top), backStack.size)
    val suiteType =
        if (showsBar)
            NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfo())
        else NavigationSuiteType.None
    val chip: @Composable () -> Unit =
        remember(accountChip, navigator) {
            {
                if (accountChip != null) {
                    RegistrationChip(
                        label = accountChip.label,
                        indicator = accountChip.indicator,
                        onClick = { navigator.goTo(AccountSwitcher) },
                        accent = accountChip.color?.let(::Color),
                    )
                } else {
                    AddAccountChip(onClick = { navigator.goTo(AccountEditor()) })
                }
            }
        }

    NavigationSuiteScaffold(
        modifier = modifier,
        navigationSuiteType = suiteType,
        navigationItems = {
            destinations.forEach { destination ->
                val selected = destination.key == current
                NavigationSuiteItem(
                    selected = selected,
                    onClick = { navigator.switchTopLevel(destination.key) },
                    icon = {
                        Icon(
                            imageVector =
                                if (selected) destination.selectedIcon else destination.icon,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(destination.label)) },
                    navigationSuiteType = suiteType,
                )
            }
        },
    ) {
        CompositionLocalProvider(LocalAccountChip provides chip) {
            SonaNavDisplay(
                backStack = backStack,
                navigator = navigator,
                sceneStrategies = sceneStrategies,
                entryProvider = entries,
            )
        }
    }
}
