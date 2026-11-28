/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.call.video

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bobbyesp.sona.calling.domain.CallQuality
import com.bobbyesp.sona.calling.ui.label
import com.bobbyesp.sona.feature.call.R

internal val CallQuality?.bars: Int
    get() =
        when (this) {
            null -> 0
            CallQuality.Poor -> 1
            CallQuality.Fair -> 2
            CallQuality.Good -> 3
        }

/**
 * Three bars lit by [quality]: their count, not only the color, tells the grade, and TalkBack gets
 * its word.
 *
 * @param showLabel Writes the word beside the bars.
 */
@Composable
internal fun QualityIndicator(
    quality: CallQuality?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val colors = MaterialTheme.colorScheme
    val description = quality?.label() ?: stringResource(R.string.call_quality_unknown)
    val lit by
        animateColorAsState(
            targetValue =
                when (quality) {
                    CallQuality.Poor -> colors.error
                    CallQuality.Fair -> colors.tertiary
                    else -> colors.primary
                },
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "QualityColor",
        )
    val unlit = colors.outlineVariant
    val count = quality.bars
    val content: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Canvas(Modifier.size(width = 18.dp, height = 16.dp)) {
                val gap = size.width / 9f
                val width = (size.width - gap * 2) / 3f
                for (bar in 0 until 3) {
                    val height = size.height * (bar + 1) / 3f
                    drawRoundRect(
                        color = if (bar < count) lit else unlit,
                        topLeft = Offset(bar * (width + gap), size.height - height),
                        size = Size(width, height),
                        cornerRadius = CornerRadius(width / 2f),
                    )
                }
            }
            if (showLabel) {
                Text(description, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    val semantics =
        Modifier.heightIn(min = 48.dp).semantics {
            contentDescription = description
            if (onClick != null) role = Role.Button
        }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = container,
            modifier = modifier.then(semantics),
            content = content,
        )
    } else {
        Surface(
            shape = CircleShape,
            color = container,
            modifier = modifier.then(semantics),
            content = content,
        )
    }
}
