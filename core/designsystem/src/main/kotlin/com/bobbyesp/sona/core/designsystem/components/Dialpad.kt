/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme

/**
 * One key of the dialpad: what it types, the letters under it, and what a long press does.
 *
 * @property longPress What a long press types instead (`+` on 0), or `null` when the long press is
 *   the caller's to decide ([Dialpad]'s `onLongPress`).
 * @property badge A mark under the digit for what its long press does (voicemail on 1), in place of
 *   letters.
 * @property longPressLabel What TalkBack names the long press, when it does something.
 */
@Immutable
data class DialpadKey(
    val symbol: Char,
    val letters: String = "",
    val longPress: Char? = null,
    val badge: ImageVector? = null,
    val longPressLabel: String? = null,
)

/** The phone keypad, row by row. Long press on 0 types +; on 1 the caller decides (voicemail). */
val StandardDialpad: List<List<DialpadKey>> =
    listOf(
        listOf(DialpadKey('1'), DialpadKey('2', "ABC"), DialpadKey('3', "DEF")),
        listOf(DialpadKey('4', "GHI"), DialpadKey('5', "JKL"), DialpadKey('6', "MNO")),
        listOf(DialpadKey('7', "PQRS"), DialpadKey('8', "TUV"), DialpadKey('9', "WXYZ")),
        listOf(DialpadKey('*'), DialpadKey('0', "+", longPress = '+'), DialpadKey('#')),
    )

/** These keys with the voicemail mark on 1, for when its long press calls voicemail. */
fun List<List<DialpadKey>>.withVoicemail(label: String): List<List<DialpadKey>> = map { row ->
    row.map { key ->
        if (key.symbol == '1') key.copy(badge = Icons.Rounded.Voicemail, longPressLabel = label)
        else key
    }
}

/**
 * The dialpad. Each key is a pill that squashes into a rounded square under the finger and springs
 * back on release, on the theme's expressive spatial spring, filling with the primary container
 * while held. The digits are [SonaTheme]'s numerals, a weight heavier while pressed; `*` and `#`
 * sit on the secondary container, so the keys for service codes are told from the digits.
 *
 * It ticks under the finger when [haptics] is on, and sounds the key's tone when [tones] is on. The
 * width is bounded so the keys stay a thumb's reach apart on a tablet.
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
    tones: Boolean = false,
    keyAspectRatio: Float? = 1.6f,
    onLongPress: (DialpadKey) -> Boolean = { false },
) {
    val dialTones = rememberDialpadTones(tones)
    Column(
        modifier = modifier.widthIn(max = DialpadMaxWidth).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KeySpacing),
    ) {
        keys.forEach { row ->
            Row(
                modifier = if (keyAspectRatio == null) Modifier.weight(1f) else Modifier,
                horizontalArrangement = Arrangement.spacedBy(KeySpacing),
            ) {
                row.forEach { key ->
                    DialpadKeyButton(
                        key = key,
                        showLetters = showLetters,
                        haptics = haptics,
                        onClick = {
                            dialTones?.play(key.symbol)
                            onKey(key.symbol)
                        },
                        onLongClick = {
                            val other = key.longPress
                            if (other != null) {
                                dialTones?.play(other)
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
    val symbol = !key.symbol.isDigit()

    val corner =
        animateFloatAsState(
            targetValue = if (pressed) PressedCornerPercent else RestingCornerPercent,
            animationSpec = motion.fastSpatialSpec(),
            label = "DialpadKeyCorner",
        )
    val scale =
        animateFloatAsState(
            targetValue = if (pressed) PressedScale else 1f,
            animationSpec = motion.fastSpatialSpec(),
            label = "DialpadKeyScale",
        )
    val restingContainer = if (symbol) colors.secondaryContainer else colors.surfaceContainerHigh
    val container =
        animateColorAsState(
            targetValue = if (pressed) colors.primaryContainer else restingContainer,
            animationSpec = motion.fastEffectsSpec(),
            label = "DialpadKeyContainer",
        )
    val content =
        when {
            pressed -> colors.onPrimaryContainer
            symbol -> colors.onSecondaryContainer
            else -> colors.onSurface
        }
    val secondaryContent = if (pressed || symbol) content else colors.onSurfaceVariant

    val spokenLetters = key.letters.toList().joinToString(" ")
    val description =
        if (spokenLetters.isEmpty()) "${key.symbol}" else "${key.symbol}, $spokenLetters"

    BoxWithConstraints(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    // A percent of the shorter side, worked out here: the key's size is only known
                    // now.
                    shape = RoundedCornerShape(size.minDimension * corner.value / 100f)
                    clip = true
                }
                .drawBehind { drawRect(container.value) }
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
                    onLongClick(label = key.longPressLabel) { onLongClick() }
                },
        contentAlignment = Alignment.Center,
    ) {
        val roomy = maxHeight >= KeyHeightForSecondLine
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = key.symbol.toString(),
                style = SonaTheme.typography.numerals.medium,
                fontWeight = if (pressed) FontWeight.Bold else FontWeight.Medium,
                color = content,
            )
            val badge = key.badge
            when {
                !roomy -> Unit
                badge != null ->
                    Icon(
                        imageVector = badge,
                        contentDescription = null,
                        tint = secondaryContent,
                        modifier = Modifier.size(BadgeSize),
                    )
                showLetters && key.letters.isNotEmpty() ->
                    Text(
                        text = key.letters,
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = LettersTracking,
                        color = secondaryContent,
                    )
            }
        }
    }
}

private val DialpadMaxWidth = 420.dp
private val KeySpacing = 10.dp
private val BadgeSize = 16.dp
private val KeyHeightForSecondLine = 64.dp
private val LettersTracking = 1.5.sp

/** A full pill at rest; a rounded square under the finger. */
private const val RestingCornerPercent = 50f
private const val PressedCornerPercent = 26f

/** How far a key gives under the finger: enough to feel, not enough to look broken. */
private const val PressedScale = 0.92f

@PreviewLightDark
@Composable
private fun DialpadPreview() {
    SonaTheme {
        Surface {
            Dialpad(
                onKey = {},
                keys = StandardDialpad.withVoicemail("Voicemail"),
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
