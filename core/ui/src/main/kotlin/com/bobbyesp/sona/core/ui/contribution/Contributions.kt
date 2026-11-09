/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.ui.contribution

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.sona.core.navigation.Navigator

/*
 * Contribution points: what a feature adds to screens it does not own. Each feature binds its
 * contributions in its Koin module (`single { RecentsSection } bind NavSection::class`), and the
 * screen that shows them asks for all of them (`getAll<NavSection>()`). No screen names a feature,
 * so adding a feature edits no other. See docs/architecture.md, Contribution points.
 *
 * Each contribution is an object of its own: Koin keeps one definition per type, and a type per
 * contribution is what lets several be bound to the same contract.
 */

/** A feature's destinations, registered on the shell's entry provider. */
interface NavSection {
    fun EntryProviderScope<NavKey>.register(navigator: Navigator)
}

/** A tab of the navigation bar (or rail), shown in [order]. */
interface TopLevelDestination {
    /** The destination the tab opens; registered by the same feature's [NavSection]. */
    val key: NavKey

    /** Lower first. Recents is 0, and is the start destination. */
    val order: Int

    @get:StringRes val label: Int

    val icon: ImageVector

    val selectedIcon: ImageVector
}

/**
 * The account chip every top bar shows on its end (the default account, its color and whether it is
 * registered). The shell provides it; a screen only places it, so no feature knows accounts.
 */
val LocalAccountChip = staticCompositionLocalOf<@Composable () -> Unit> { {} }
