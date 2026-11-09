/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme

/** One key of the dialpad: what it types, the letters under it, and what a long press types. */
@Immutable
data class DialpadKey(val symbol: Char, val letters: String = "", val longPress: Char? = null)

/** The phone keypad, row by row. Long press on 0 types +; on 1 the caller decides (voicemail). */
val StandardDialpad: List<List<DialpadKey>> =
    listOf(
        listOf(DialpadKey('1'), DialpadKey('2', "ABC"), DialpadKey('3', "DEF")),
        listOf(DialpadKey('4', "GHI"), DialpadKey('5', "JKL"), DialpadKey('6', "MNO")),
        listOf(DialpadKey('7', "PQRS"), DialpadKey('8', "TUV"), DialpadKey('9', "WXYZ")),
        listOf(DialpadKey('*'), DialpadKey('0', "+", longPress = '+'), DialpadKey('#')),
    )

/**
 * The dialpad. Each key is a shape that squares a little under the finger, on the theme's spring,
 * and takes the primary container while pressed; it ticks under the finger when [haptics] is on.
 * The width is bounded so the keys stay a thumb's reach apart on a tablet.
 *
 * @param onKey A key was tapped, or long pressed and it types something else
 *   ([DialpadKey.longPress]).
 * @param onLongPress A long press on a key with nothing of its own to type; return whether it was
 *   used (the voicemail shortcut on 1).
 * @param keyAspectRatio Each key's width to height, which sets the pad's height from its width.
 *   `null` makes the keys share the height the pad is given instead, for where height is what is
 *   short (a phone in landscape): the pad must then have a bounded height.
 */
@Composable
fun Dialpad(
    onKey: (Char) -> Unit,
    modifier: Modifier = Modifier,
    keys: List<List<DialpadKey>> = StandardDialpad,
    showLetters: Boolean = true,
    haptics: Boolean = true,
    keyAspectRatio: Float? = 1.6f,
    onLongPress: (DialpadKey) -> Boolean = { false },
) {
    Column(
        modifier = modifier.widthIn(max = 420.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        keys.forEach { row ->
            Row(
                modifier = if (keyAspectRatio == null) Modifier.weight(1f) else Modifier,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { key ->
                    DialpadKeyButton(
                        key = key,
                        showLetters = showLetters,
                        haptics = haptics,
                        onClick = { onKey(key.symbol) },
                        onLongClick = {
                            val other = key.longPress
                            if (other != null) {
                                onKey(other)
                                true
                            } else {
                                onLongPress(key)
                            }
                        },
                        modifier =
                            Modifier.weight(1f)
                                .then(
                                    if (keyAspectRatio != null) {
                                        Modifier.aspectRatio(keyAspectRatio)
                                    } else {
                                        Modifier.fillMaxHeight()
                                    },
                                ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialpadKeyButton(
    key: DialpadKey,
    showLetters: Boolean,
    haptics: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme

    // A full pill at rest, a rounded square under the finger.
    val cornerPercent by
        animateIntAsState(
            targetValue = if (pressed) 28 else 50,
            animationSpec = motion.fastSpatialSpec(),
            label = "DialpadKeyCorner",
        )
    val container by
        animateColorAsState(
            targetValue = if (pressed) colors.primaryContainer else colors.surfaceContainerHigh,
            animationSpec = motion.fastEffectsSpec(),
            label = "DialpadKeyContainer",
        )
    val content = if (pressed) colors.onPrimaryContainer else colors.onSurface
    val shape = RoundedCornerShape(percent = cornerPercent)

    val spokenLetters = key.letters.toList().joinToString(" ")
    val description =
        if (spokenLetters.isEmpty()) "${key.symbol}" else "${key.symbol}, $spokenLetters"

    Box(
        modifier =
            modifier
                .clip(shape)
                .background(container)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = {
                        if (haptics)
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        onClick()
                    },
                    onLongClick = {
                        if (onLongClick() && haptics) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                )
                .clearAndSetSemantics {
                    contentDescription = description
                    onClick {
                        onClick()
                        true
                    }
                    onLongClick { onLongClick() }
                },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = key.symbol.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium,
                color = content,
            )
            if (showLetters && key.letters.isNotEmpty()) {
                Text(
                    text = key.letters,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (pressed) content else colors.onSurfaceVariant,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun DialpadPreview() {
    SonaTheme { Surface { Dialpad(onKey = {}, modifier = Modifier.padding(16.dp)) } }
}
