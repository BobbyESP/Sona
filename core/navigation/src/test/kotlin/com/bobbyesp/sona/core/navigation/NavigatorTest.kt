/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Keys shaped like the app's, so these tests read as the app is used, without the navigation
// depending on the features that own the real ones.
private data object Recents : NavKey

private data object Contacts : NavKey

private data object Keypad : NavKey

private data class CallDetail(val recordId: String) : NavKey

private data class ContactDetail(val contactId: String) : NavKey

/**
 * The back stack is plain state, so the rules about what may be done to it live in
 * [BackStackNavigator], and this is what stops them from quietly disappearing.
 */
class NavigatorTest {

    private fun backStack(vararg keys: NavKey) = NavBackStack(*keys)

    private val call = CallDetail("1")

    @Test
    fun `going somewhere puts it on top, leaving the rest to come back to`() {
        val stack = backStack(Recents)

        BackStackNavigator(stack).goTo(call)

        assertEquals(listOf(Recents, call), stack.toList())
    }

    /** A double tap on a row must not open the same call twice. */
    @Test
    fun `going to the destination already on top does nothing`() {
        val stack = backStack(Recents, call)

        BackStackNavigator(stack).goTo(call)

        assertEquals(listOf(Recents, call), stack.toList())
    }

    @Test
    fun `going back never empties the stack`() {
        val stack = backStack(Recents)

        BackStackNavigator(stack).goBack()

        assertEquals(listOf(Recents), stack.toList())
    }

    @Test
    fun `going back while a predicate holds stops at the root`() {
        val stack = backStack(Recents, call, CallDetail("2"))

        BackStackNavigator(stack).goBackWhile { it is CallDetail }

        assertEquals(listOf(Recents), stack.toList())
    }

    @Test
    fun `a destination whose subject is gone is removed wherever it sits`() {
        val contact = ContactDetail("a")
        val stack = backStack(Recents, contact, call, contact)

        BackStackNavigator(stack).removeDestination(contact)

        assertEquals(listOf(Recents, call), stack.toList())
    }

    @Test
    fun `switching to a tab keeps the start tab under it`() {
        val stack = backStack(Recents, call)

        BackStackNavigator(stack).switchTopLevel(Contacts)

        assertEquals(listOf(Recents, Contacts), stack.toList())
    }

    @Test
    fun `switching between two other tabs replaces one with the other`() {
        val stack = backStack(Recents, Contacts, ContactDetail("a"))

        BackStackNavigator(stack).switchTopLevel(Keypad)

        assertEquals(listOf(Recents, Keypad), stack.toList())
    }

    @Test
    fun `switching to the start tab leaves only it`() {
        val stack = backStack(Recents, Keypad)

        BackStackNavigator(stack).switchTopLevel(Recents)

        assertEquals(listOf(Recents), stack.toList())
    }

    @Test
    fun `selecting the tab already shown returns it to its root`() {
        val stack = backStack(Recents, Contacts, ContactDetail("a"))

        BackStackNavigator(stack).switchTopLevel(Contacts)

        assertEquals(listOf(Recents, Contacts), stack.toList())
    }

    @Test
    fun `the current tab is the last top-level key on the stack`() {
        val tabs = listOf(Recents, Contacts, Keypad)

        assertEquals(Contacts, listOf(Recents, Contacts, ContactDetail("a")).currentTopLevel(tabs))
        assertEquals(Recents, listOf(Recents, call).currentTopLevel(tabs))
        assertNull(listOf<NavKey>(call).currentTopLevel(tabs))
    }
}
