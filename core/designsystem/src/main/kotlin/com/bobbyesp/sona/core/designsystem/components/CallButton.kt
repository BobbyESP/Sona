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
import androidx.compose.material3.MaterialTheme
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
    val colors = MaterialTheme.colorScheme
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        shapes = IconButtonDefaults.shapes(),
        // Disabled, it is still the call button, faded: Material's grey would read as something
        // else on a screen that is about calling.
        colors =
            IconButtonDefaults.filledIconButtonColors(
                // Both named: left out, the content color would follow the surrounding content.
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                disabledContainerColor = colors.primary.copy(alpha = DisabledContainerAlpha),
                disabledContentColor = colors.onPrimary.copy(alpha = DisabledContentAlpha),
            ),
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

/** Material's disabled opacities for a container and its content. */
private const val DisabledContainerAlpha = 0.38f
private const val DisabledContentAlpha = 0.7f

@PreviewLightDark
@Composable
private fun CallButtonPreview() {
    SonaTheme { Surface { CallButton(onClick = {}, contentDescription = "Call") } }
}
