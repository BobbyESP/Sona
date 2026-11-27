/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.ui.contribution

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.bobbyesp.sona.core.navigation.Navigator

/**
 * What the features add for [party] (*Message*, *Video call*), laid out by the screen that shows
 * the party without it knowing which features they come from. Nothing at all when none applies.
 *
 * @param compact Icon buttons only, labelled for TalkBack, for a row that has little room (beside a
 *   number); otherwise tonal buttons with their labels, wrapping onto a second line when they do
 *   not fit, so nothing is cut at a large font. A disabled action says why under the buttons.
 * @param onAction Called after an action is used: a sheet showing the party closes.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PartyActionButtons(
    party: PartyRef,
    navigator: Navigator,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onAction: () -> Unit = {},
) {
    val actions = rememberPartyActions(party, navigator)
    if (actions.isEmpty()) return
    if (compact) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            actions.forEach { action ->
                FilledTonalIconButton(
                    onClick = {
                        action.onClick()
                        onAction()
                    },
                    enabled = action.enabled,
                    shapes = IconButtonDefaults.shapes(),
                    modifier =
                        Modifier.semantics {
                            action.disabledReason?.let { stateDescription = it }
                        },
                ) {
                    Icon(action.icon, contentDescription = action.label)
                }
            }
        }
        return
    }
    Column(modifier) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            actions.forEach { action ->
                FilledTonalButton(
                    onClick = {
                        action.onClick()
                        onAction()
                    },
                    enabled = action.enabled,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(
                        action.icon,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(action.label)
                }
            }
        }
        actions
            .mapNotNull { action -> action.disabledReason?.takeIf { !action.enabled } }
            .forEach { reason ->
                Text(
                    reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
    }
}
