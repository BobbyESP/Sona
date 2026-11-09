/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.dialer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.sona.core.designsystem.components.CallButton
import com.bobbyesp.sona.core.designsystem.components.Dialpad
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme
import com.bobbyesp.sona.core.ui.TopLevelScaffold
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun KeypadRoute(viewModel: KeypadViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    KeypadScreen(state = state, onIntent = viewModel::onSendIntent)
}

/**
 * The keypad tab: what was typed, the dialpad, and the call button. Typing, deleting and clearing
 * work today; the call button is enabled once there are accounts to call with (phase 1 and 2).
 *
 * Stacked where there is height for it; side by side where width is what there is (a phone in
 * landscape), with the keys sharing the height. Decided from the room this screen was given, never
 * from the window, which it may share.
 */
@Composable
internal fun KeypadScreen(
    state: KeypadState,
    onIntent: (KeypadIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val field: @Composable () -> Unit = {
        DialField(
            input = state.input,
            onDelete = { onIntent(KeypadIntent.Delete) },
            onClear = { onIntent(KeypadIntent.Clear) },
        )
    }
    val callButton: @Composable () -> Unit = {
        CallButton(
            onClick = {},
            enabled = false,
            contentDescription = stringResource(R.string.dialer_call_unavailable),
        )
    }

    TopLevelScaffold(title = stringResource(R.string.dialer_title), modifier = modifier) {
        padding,
        contentModifier ->
        BoxWithConstraints(contentModifier.padding(padding).padding(horizontal = 24.dp)) {
            if (maxWidth > maxHeight) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        field()
                        Spacer(Modifier.size(16.dp))
                        callButton()
                    }
                    Dialpad(
                        onKey = { onIntent(KeypadIntent.Type(it)) },
                        keyAspectRatio = null,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Spacer(Modifier.weight(1f))
                    field()
                    Spacer(Modifier.size(24.dp))
                    Dialpad(onKey = { onIntent(KeypadIntent.Type(it)) })
                    Spacer(Modifier.size(24.dp))
                    callButton()
                    Spacer(Modifier.size(24.dp))
                }
            }
        }
    }
}

/** The typed number, shrinking to fit rather than scrolling, with delete at its end. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialField(
    input: String,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 72.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // As wide as the delete button, so the number stays centered.
        Spacer(Modifier.size(48.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (input.isEmpty()) {
                Text(
                    text = stringResource(R.string.dialer_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                BasicText(
                    text = input,
                    modifier =
                        Modifier.semantics {
                            contentDescription = input.toList().joinToString(" ")
                        },
                    style =
                        MaterialTheme.typography.displayMedium.copy(
                            color = colors.onSurface,
                            textAlign = TextAlign.Center,
                        ),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 45.sp),
                )
            }
        }
        AnimatedVisibility(
            visible = input.isNotEmpty(),
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f),
        ) {
            val deleteLabel = stringResource(R.string.dialer_delete)
            val clearLabel = stringResource(R.string.dialer_clear)
            Box(
                modifier =
                    Modifier.size(48.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            interactionSource = null,
                            indication = ripple(),
                            onClick = onDelete,
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onClear()
                            },
                        )
                        .clearAndSetSemantics {
                            contentDescription = deleteLabel
                            onClick {
                                onDelete()
                                true
                            }
                            onLongClick(label = clearLabel) {
                                onClear()
                                true
                            }
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Backspace,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun KeypadScreenPreview() {
    SonaTheme { KeypadScreen(state = KeypadState(input = "102"), onIntent = {}) }
}
