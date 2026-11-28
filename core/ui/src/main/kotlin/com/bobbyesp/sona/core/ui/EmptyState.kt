/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.bobbyesp.sona.core.designsystem.components.ScreenPlaceholderCard

/**
 * What a top-level destination shows when it has nothing yet: the placeholder card, centered where
 * it fits and scrollable where it does not (a phone in landscape), never cut off.
 *
 * @param padding and [modifier] are what [TopLevelScaffold] gives its content.
 */
@Composable
fun TopLevelEmptyState(
    title: String,
    description: String,
    icon: ImageVector,
    padding: PaddingValues,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier.padding(padding)) {
        Box(
            modifier =
                Modifier.verticalScroll(rememberScrollState())
                    .fillMaxWidth()
                    .heightIn(min = maxHeight)
                    .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            ScreenPlaceholderCard(
                title = title,
                description = description,
                icon = icon,
                modifier = Modifier.widthIn(max = 560.dp),
            )
        }
    }
}
