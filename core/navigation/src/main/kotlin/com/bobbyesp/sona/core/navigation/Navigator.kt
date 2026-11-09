/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * The only thing a feature is given of the back stack.
 *
 * Deliberately not the stack itself: a feature has no business reading it, reordering it, or
 * deciding what sits underneath. Saying where it wants to go is enough, and spares the shell a
 * callback per edge of the graph.
 */
@Stable
interface Navigator {

    /** Goes to [key]. Asking for the destination already on top does nothing. */
    fun goTo(key: NavKey)

    /** Leaves the current destination. At the last one this does nothing rather than emptying. */
    fun goBack()

    /**
     * Leaves every destination on top that [predicate] accepts, for dismissing a related group at
     * once — a confirmation and the menu that opened it. Counting [goBack] calls instead would
     * depend on how the user got there.
     */
    fun goBackWhile(predicate: (NavKey) -> Boolean)

    /**
     * Takes [key] off the stack wherever it sits, for a destination whose subject has ceased to
     * exist. Not [goBack]: wanting yourself gone is not asking to pop whatever is on top, which
     * need not be you.
     */
    fun removeDestination(key: NavKey)

    /**
     * Shows the top-level destination [key] (a tab of the navigation bar), leaving whatever was
     * open on top of the previous one. The first destination of the stack, Recents, stays at the
     * bottom, so back from any other tab returns to it and back from it leaves the app, as Material
     * asks of top-level navigation.
     */
    fun switchTopLevel(key: NavKey)
}

@Composable
fun rememberNavigator(backStack: NavBackStack<NavKey>): Navigator =
    remember(backStack) { BackStackNavigator(backStack) }

/**
 * The [Navigator] over a back stack. Public so that its rules, and those of the navigation built on
 * it in the app and in features, can be asserted without a composition. A feature never builds one:
 * it is handed a [Navigator], and the back stack it would need belongs to the app's shell.
 */
class BackStackNavigator(private val backStack: NavBackStack<NavKey>) : Navigator {

    override fun goTo(key: NavKey) {
        if (backStack.lastOrNull() == key) return

        backStack.add(key)
    }

    /**
     * `NavDisplay` requires a non-empty back stack, so the root is not poppable. Refusing here
     * rather than in each caller means no destination has to know whether it is the root.
     */
    override fun goBack() {
        if (backStack.size <= 1) return

        backStack.removeLastOrNull()
    }

    override fun goBackWhile(predicate: (NavKey) -> Boolean) {
        while (backStack.size > 1 && predicate(backStack.last())) backStack.removeLastOrNull()
    }

    /**
     * Every occurrence: one destination can be reached twice, and a deleted contact is gone from
     * both.
     */
    override fun removeDestination(key: NavKey) {
        while (backStack.size > 1 && backStack.contains(key)) backStack.remove(key)
    }

    override fun switchTopLevel(key: NavKey) {
        val start = backStack.firstOrNull()
        if (backStack.size == 1 && start == key) return
        if (backStack.size == 2 && backStack.last() == key && start != key) return

        // Two steps, never empty: NavDisplay requires an entry at every moment.
        while (backStack.size > 1) backStack.removeLastOrNull()
        if (start != key) backStack.add(key)
    }
}

/** The top-level destination now shown: the last of [topLevelKeys] on the stack, or `null`. */
fun List<NavKey>.currentTopLevel(topLevelKeys: Collection<NavKey>): NavKey? = lastOrNull {
    it in topLevelKeys
}
