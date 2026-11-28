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
 * Someone an action can be about: a contact, a history entry, a keypad suggestion.
 *
 * @property address As typed or stored (`102`, `sip:alice@lab.local`); the acting feature resolves
 *   it.
 * @property account The id of a history entry's account; `null` for the default one.
 */
@Immutable
data class PartyRef(val address: String, val name: String? = null, val account: String? = null)

/** One action on a party; [disabledReason] says why it is not [enabled]. */
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
 * What a feature adds wherever a party is shown (*Video call*, *Message*), so the screen never
 * names the feature (docs/architecture.md, Contribution points). The action is built in
 * composition, so it can ask for a permission first.
 */
interface PartyActionProvider {
    /** Lower first. Calls are 0–99, messages 100–199. */
    val order: Int

    /**
     * The action for [party], or `null` where it does not apply. [navigator] opens the feature's
     * own screens.
     */
    @Composable fun rememberAction(party: PartyRef, navigator: Navigator): PartyAction?
}

/** Every provider, sorted by [PartyActionProvider.order]; the shell collects them from Koin. */
val LocalPartyActionProviders = staticCompositionLocalOf<List<PartyActionProvider>> { emptyList() }

/** The actions that apply to [party], in order. */
@Composable
fun rememberPartyActions(party: PartyRef, navigator: Navigator): List<PartyAction> =
    LocalPartyActionProviders.current.mapNotNull { provider ->
        key(provider) { provider.rememberAction(party, navigator) }
    }
