/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.contacts

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme
import com.bobbyesp.sona.core.ui.TopLevelEmptyState
import com.bobbyesp.sona.core.ui.TopLevelScaffold

/** Contacts: the device's and Sona's own. Empty until the directory arrives (phase 3). */
@Composable
internal fun ContactsScreen(modifier: Modifier = Modifier) {
    TopLevelScaffold(title = stringResource(R.string.contacts_title), modifier = modifier) {
        padding,
        contentModifier ->
        TopLevelEmptyState(
            title = stringResource(R.string.contacts_empty_title),
            description = stringResource(R.string.contacts_empty_description),
            icon = Icons.Rounded.Contacts,
            padding = padding,
            contentModifier = contentModifier,
        )
    }
}

@PreviewLightDark
@Composable
private fun ContactsScreenPreview() {
    SonaTheme { ContactsScreen() }
}
