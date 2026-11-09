/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.designsystem.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme

/**
 * The round call button: large, wide, and morphing from a pill to a rounded square under the
 * finger, as Material 3 Expressive's icon buttons do. The account picker beside it, when there is
 * more than one account, comes with the dialer.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CallButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        shapes = IconButtonDefaults.shapes(),
        modifier =
            modifier.size(
                IconButtonDefaults.largeContainerSize(
                    IconButtonDefaults.IconButtonWidthOption.Wide,
                ),
            ),
    ) {
        Icon(
            imageVector = Icons.Rounded.Call,
            contentDescription = contentDescription,
            modifier = Modifier.size(IconButtonDefaults.largeIconSize),
        )
    }
}

@PreviewLightDark
@Composable
private fun CallButtonPreview() {
    SonaTheme { Surface { CallButton(onClick = {}, contentDescription = "Call") } }
}
