/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.history

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme
import com.bobbyesp.sona.core.ui.TopLevelEmptyState
import com.bobbyesp.sona.core.ui.TopLevelScaffold

/**
 * Recents: the call history. Empty until calls exist (phase 2); filled from the directory in
 * phase 3.
 */
@Composable
internal fun RecentsScreen(modifier: Modifier = Modifier) {
    TopLevelScaffold(title = stringResource(R.string.history_title), modifier = modifier) {
        padding,
        contentModifier ->
        TopLevelEmptyState(
            title = stringResource(R.string.history_empty_title),
            description = stringResource(R.string.history_empty_description),
            icon = Icons.Rounded.History,
            padding = padding,
            contentModifier = contentModifier,
        )
    }
}

@PreviewLightDark
@Composable
private fun RecentsScreenPreview() {
    SonaTheme { RecentsScreen() }
}
