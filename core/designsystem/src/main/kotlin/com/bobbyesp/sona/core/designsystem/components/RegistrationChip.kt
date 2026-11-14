/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.bobbyesp.sona.core.designsystem.R
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme

/** What the chip says about an account's registration. The shell maps the engine's states to it. */
enum class RegistrationIndicator {
    Registered,
    Registering,
    Offline,
    Failed,
}

/**
 * The account chip in every top bar: the account's name, a dot in its color, and in a word whether
 * it can make and receive calls. The word is always there: a color alone says nothing to someone
 * who does not see it.
 *
 * @param onClick What a tap opens (the account switcher), or `null` while there is nothing to open:
 *   the chip is then a label, not a button that does nothing.
 * @param accent The account's color; the theme's primary when it has none.
 * @param warning Something outside the account keeps calls from working (see *Call readiness*): the
 *   chip then carries a warning sign, and says so to a screen reader.
 */
@Composable
fun RegistrationChip(
    label: String,
    indicator: RegistrationIndicator,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    warning: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val stateText = stringResource(indicator.textRes)
    val description =
        stringResource(R.string.account_chip_description, label, stateText) +
            if (warning) ". " + stringResource(R.string.account_chip_warning) else ""

    val dotColor by
        animateColorAsState(
            targetValue =
                when (indicator) {
                    RegistrationIndicator.Registered -> accent ?: colors.primary
                    RegistrationIndicator.Registering -> colors.outline
                    RegistrationIndicator.Offline -> colors.outlineVariant
                    RegistrationIndicator.Failed -> colors.error
                },
            animationSpec = motion.defaultEffectsSpec(),
            label = "RegistrationDotColor",
        )

    val dotSize by
        animateDpAsState(
            targetValue = if (indicator == RegistrationIndicator.Registered) 10.dp else 8.dp,
            animationSpec = motion.fastSpatialSpec(),
            label = "RegistrationDotSize",
        )
    val pulse = rememberInfiniteTransition(label = "RegistrationPulse")
    val pulseAlpha by
        pulse.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 700), RepeatMode.Reverse),
            label = "RegistrationPulseAlpha",
        )

    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier.size(dotSize)
                        .graphicsLayer {
                            alpha =
                                if (indicator == RegistrationIndicator.Registering) pulseAlpha
                                else 1f
                        }
                        .clip(CircleShape)
                        .background(dotColor),
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stateText,
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        if (indicator == RegistrationIndicator.Failed) colors.error
                        else colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (warning) {
                Icon(
                    Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = colors.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    val shape = CircleShape
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier =
                modifier.heightIn(min = 48.dp).clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                },
            shape = shape,
            color = colors.surfaceContainerHigh,
            contentColor = colors.onSurface,
            content = content,
        )
    } else {
        Surface(
            modifier =
                modifier.heightIn(min = 40.dp).clearAndSetSemantics {
                    contentDescription = description
                },
            shape = shape,
            color = colors.surfaceContainerHigh,
            contentColor = colors.onSurface,
            content = content,
        )
    }
}

private val RegistrationIndicator.textRes: Int
    get() =
        when (this) {
            RegistrationIndicator.Registered -> R.string.registration_registered
            RegistrationIndicator.Registering -> R.string.registration_registering
            RegistrationIndicator.Offline -> R.string.registration_offline
            RegistrationIndicator.Failed -> R.string.registration_failed
        }

@PreviewLightDark
@Composable
private fun RegistrationChipPreview() {
    SonaTheme {
        Surface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RegistrationIndicator.entries.forEach {
                    RegistrationChip(label = "Lab Asterisk · 102", indicator = it, onClick = {})
                }
            }
        }
    }
}
