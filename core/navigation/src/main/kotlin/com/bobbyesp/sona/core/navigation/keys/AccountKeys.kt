/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.navigation.keys

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/*
 * Keys of destinations other features open: the shell opens the switcher from the account chip
 * and onboarding on first start, and onboarding opens the editor. Ids, never objects.
 */

/** The accounts, as a sheet over what was open: choose the default, turn one off, add one. */
@Serializable data object AccountSwitcher : NavKey

/** Adding an account ([accountId] `null`), or editing one. */
@Serializable data class AccountEditor(val accountId: String? = null) : NavKey

/** The confirmation before deleting an account. */
@Serializable data class DeleteAccount(val accountId: String) : NavKey

/** The first start: what Sona is, and the first account. */
@Serializable data object Onboarding : NavKey

/** Whether [key] belongs to adding a first account, to leave the whole flow at once. */
fun isAccountSetup(key: NavKey): Boolean = key is AccountEditor || key == Onboarding
