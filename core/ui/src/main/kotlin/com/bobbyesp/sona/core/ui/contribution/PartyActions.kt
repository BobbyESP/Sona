/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.ui.contribution

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import com.bobbyesp.sona.core.navigation.Navigator

/**
 * Someone an action can be about: a contact, a history entry, a keypad suggestion. What the user
 * would dial, as it is shown: `102`, `+34 600 11 22 33`, `sip:alice@lab.local`.
 *
 * @property address What to call or write to, as typed or stored; the feature that acts resolves it
 *   against the account.
 * @property name How the screen names them, when it knows.
 * @property account The account the entry belongs to (a history entry's), by id; `null` for the
 *   default one.
 */
@Immutable
data class PartyRef(val address: String, val name: String? = null, val account: String? = null)

/**
 * One action on a party, as the screen that hosts it shows it: a button or a menu item.
 *
 * @property enabled Shown but not usable when `false`; [disabledReason] then says why.
 */
@Immutable
data class PartyAction(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val disabledReason: String? = null,
    val onClick: () -> Unit,
)

/**
 * What a feature adds wherever a party is shown: *Video call* (calls), *Message* (messages). The
 * screen that shows a party asks every provider ([org.koin.core.Koin.getAll]), sorted by [order],
 * and lays out the actions it gets; it never names the feature. See docs/architecture.md,
 * Contribution points.
 *
 * The action is built in composition so it can ask for a permission first (the camera, for a video
 * call) and read what it needs from the composition ([LocalCallScreen]).
 */
interface PartyActionProvider {
    /** Lower first. Calls are 0–99, messages 100–199. */
    val order: Int

    /**
     * The action for [party], or `null` where it does not apply (no engine capability, no address
     * it could use). [navigator] opens the feature's own screens.
     */
    @Composable fun rememberAction(party: PartyRef, navigator: Navigator): PartyAction?
}

/**
 * Every [PartyActionProvider], sorted by [PartyActionProvider.order]. The shell provides them (it
 * collects them from Koin), so a screen that shows a party asks for none by name.
 */
val LocalPartyActionProviders = staticCompositionLocalOf<List<PartyActionProvider>> { emptyList() }

/**
 * The actions every provider has for [party], in their order; those that do not apply are left out.
 * What a host lays out, as buttons or menu items ([PartyActionButtons]).
 */
@Composable
fun rememberPartyActions(party: PartyRef, navigator: Navigator): List<PartyAction> =
    LocalPartyActionProviders.current.mapNotNull { provider ->
        key(provider) { provider.rememberAction(party, navigator) }
    }
