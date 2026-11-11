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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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

    /**
     * How many things wait behind the tab (missed calls not yet seen), shown as a badge on its
     * icon; zero shows none. The feature owns the count, the shell only draws it.
     */
    val badge: Flow<Int>
        get() = flowOf(0)
}

/**
 * The account chip every top bar shows on its end (the default account, its color and whether it is
 * registered). The shell provides it; a screen only places it, so no feature knows accounts.
 */
val LocalAccountChip = staticCompositionLocalOf<@Composable () -> Unit> { {} }

/**
 * Opens the call screen, which lives in an activity of its own (over the lock screen). The shell
 * provides it; a screen that starts a call (the keypad, a contact) only calls it.
 */
val LocalCallScreen = staticCompositionLocalOf<() -> Unit> { {} }
