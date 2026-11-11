/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bobbyesp.sona.core.designsystem.R
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme

/**
 * Sona's text field: a filled, borderless container in Material 3 Expressive.
 *
 * At rest it is a tonal surface, round, with its label where the text will go. Focused, it takes
 * the primary container and squares up a little on the theme's spatial spring, as everything in
 * Sona does under the finger; the label rises and shrinks above the text. With a problem it takes
 * the error container and says what is wrong under it, read out by TalkBack as it appears.
 *
 * Everything that moves (the container's color and corners, the label's place and size) is read in
 * the draw phase: typing and focusing never measure the field again.
 *
 * @param label What the field is: always shown, in the field when it is empty and above the text
 *   otherwise, and read by TalkBack with the text.
 * @param placeholder An example, shown only while the field is focused and empty.
 * @param supportingText A line under the field, until [error] takes its place.
 * @param error What is wrong with the value, or `null` when nothing is.
 * @param leadingIcon A mark for what the field holds, which takes the field's focused color.
 * @param trailingContent What acts on the value (a password's eye). Without it, the field offers a
 *   clear button while it is focused and holds text, when [clearable].
 * @param shapes Its shape at rest and focused. [SonaTextFieldDefaults.segmentedShapes] joins fields
 *   into one group, as a segmented list joins its items.
 * @param supportingOnFocus Whether [supportingText] shows only while the field is focused: in a
 *   group, so the group stays one piece at rest. An [error] always shows.
 */
@Composable
fun SonaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String? = null,
    supportingText: String? = null,
    error: String? = null,
    leadingIcon: ImageVector? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    clearable: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    colors: SonaTextFieldColors = SonaTextFieldDefaults.colors(),
    shapes: SonaTextFieldShapes = SonaTextFieldDefaults.shapes(),
    supportingOnFocus: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val isError = error != null
    val motion = MaterialTheme.motionScheme

    // What the field looks like now; each value animates to it.
    val container =
        animateColorAsState(
            targetValue = colors.container(enabled, focused, isError),
            animationSpec = motion.defaultEffectsSpec(),
            label = "FieldContainer",
        )
    val content by
        animateColorAsState(
            targetValue = colors.content(enabled, focused, isError),
            animationSpec = motion.defaultEffectsSpec(),
            label = "FieldContent",
        )
    val accent by
        animateColorAsState(
            targetValue = colors.accent(enabled, focused, isError),
            animationSpec = motion.defaultEffectsSpec(),
            label = "FieldAccent",
        )
    // 0 at rest, 1 focused, on the spatial spring: the corners give a little past their mark and
    // settle, as a key does under the finger. Each corner goes its own way (in a group the outer
    // ones and the inner ones meet in the middle).
    val focus =
        animateFloatAsState(
            targetValue = if (focused) 1f else 0f,
            animationSpec = motion.fastSpatialSpec(),
            label = "FieldCorner",
        )
    // 0 with the label in the field, 1 with it above the text. Effects, not spatial: a label that
    // overshoots its place reads as a glitch, not as play.
    val raised =
        animateFloatAsState(
            targetValue = if (focused || value.isNotEmpty()) 1f else 0f,
            animationSpec = motion.fastEffectsSpec(),
            label = "FieldLabel",
        )

    val textStyle = MaterialTheme.typography.bodyLarge
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().semantics { if (error != null) error(error) },
        enabled = enabled,
        textStyle = textStyle.copy(color = content),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        interactionSource = interactions,
        cursorBrush = SolidColor(colors.cursor(isError)),
        decorationBox = { innerTextField ->
            Column {
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .graphicsLayer {
                                shape = shapes.at(focus.value, size, this)
                                clip = true
                            }
                            .drawBehind { drawRect(container.value) }
                            .heightIn(min = FieldMinHeight)
                            .padding(
                                start = if (leadingIcon != null) 16.dp else 20.dp,
                                end = 8.dp,
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leadingIcon != null) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(LeadingIconSize),
                        )
                        Spacer(Modifier.width(14.dp))
                    }
                    Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        Text(
                            text = label,
                            style = textStyle,
                            // In the empty field it is a prompt, a step quieter than what is typed.
                            color = accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                Modifier.graphicsLayer {
                                    val progress = raised.value
                                    val scale = lerp(1f, RaisedLabelScale, progress)
                                    scaleX = scale
                                    scaleY = scale
                                    transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0f)
                                    translationY = lerp(RestingLabelOffset.toPx(), 0f, progress)
                                },
                        )
                        Box(Modifier.padding(top = TextTop)) {
                            if (value.isEmpty() && focused && placeholder != null) {
                                Text(
                                    text = placeholder,
                                    style = textStyle,
                                    color = content.copy(alpha = PlaceholderAlpha),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            innerTextField()
                        }
                    }
                    CompositionLocalProvider(LocalContentColor provides accent) {
                        when {
                            trailingContent != null -> trailingContent()
                            clearable ->
                                ClearButton(
                                    visible = focused && value.isNotEmpty() && enabled,
                                    onClear = { onValueChange("") },
                                )
                            else -> Spacer(Modifier.width(12.dp))
                        }
                    }
                }
                SupportingLine(
                    supportingText = supportingText?.takeIf { focused || !supportingOnFocus },
                    error = error,
                    colors = colors,
                )
            }
        },
    )
}

/** The clear button: it keeps its room while hidden, so the text does not move as it comes. */
@Composable
private fun ClearButton(visible: Boolean, onClear: () -> Unit) {
    val motion = MaterialTheme.motionScheme
    Box(Modifier.size(TrailingSize), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = visible,
            enter =
                fadeIn(motion.fastEffectsSpec()) +
                    scaleIn(motion.fastSpatialSpec(), initialScale = 0.5f),
            exit =
                fadeOut(motion.fastEffectsSpec()) +
                    scaleOut(motion.fastSpatialSpec(), targetScale = 0.5f),
        ) {
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(IconButtonDefaults.smallContainerSize()),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.text_field_clear),
                    modifier = Modifier.size(IconButtonDefaults.smallIconSize),
                )
            }
        }
    }
}

/** What is said under the field: the error, with its mark, or the supporting text. */
@Composable
private fun SupportingLine(supportingText: String?, error: String?, colors: SonaTextFieldColors) {
    val motion = MaterialTheme.motionScheme
    val line =
        when {
            error != null -> SupportLine(error, isError = true)
            supportingText != null -> SupportLine(supportingText, isError = false)
            else -> null
        }
    AnimatedContent(
        targetState = line,
        transitionSpec = {
            (fadeIn(motion.defaultEffectsSpec()) +
                expandVertically(motion.fastSpatialSpec())) togetherWith
                (fadeOut(motion.fastEffectsSpec()) +
                    shrinkVertically(motion.fastSpatialSpec())) using
                SizeTransform(clip = false)
        },
        contentKey = { it?.isError },
        label = "FieldSupport",
    ) { shown ->
        if (shown == null) return@AnimatedContent
        Row(
            modifier =
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 6.dp).semantics {
                    if (shown.isError) liveRegion = LiveRegionMode.Polite
                },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val color = if (shown.isError) colors.errorText else colors.supportingText
            if (shown.isError) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(text = shown.text, style = MaterialTheme.typography.bodySmall, color = color)
        }
    }
}

private data class SupportLine(val text: String, val isError: Boolean)

/**
 * The colors of a [SonaTextField] in each of its states. Every pair is a container and the content
 * Material made for it, so the field reads in light, dark, and the wallpaper's colors alike.
 */
@Immutable
data class SonaTextFieldColors(
    val container: Color,
    val focusedContainer: Color,
    val errorContainer: Color,
    val disabledContainer: Color,
    val content: Color,
    val focusedContent: Color,
    val errorContent: Color,
    val disabledContent: Color,
    /** The label above the text and the icons, at rest. */
    val accent: Color,
    val focusedAccent: Color,
    val errorAccent: Color,
    val cursor: Color,
    val errorCursor: Color,
    val supportingText: Color,
    val errorText: Color,
) {
    internal fun container(enabled: Boolean, focused: Boolean, error: Boolean): Color =
        when {
            !enabled -> disabledContainer
            error -> errorContainer
            focused -> focusedContainer
            else -> container
        }

    internal fun content(enabled: Boolean, focused: Boolean, error: Boolean): Color =
        when {
            !enabled -> disabledContent
            error -> errorContent
            focused -> focusedContent
            else -> content
        }

    internal fun accent(enabled: Boolean, focused: Boolean, error: Boolean): Color =
        when {
            !enabled -> disabledContent
            error -> errorAccent
            focused -> focusedAccent
            else -> accent
        }

    internal fun cursor(error: Boolean): Color = if (error) errorCursor else cursor
}

/**
 * A [SonaTextField]'s shape at rest and while focused. Between the two each corner moves on its
 * own, so any two rounded shapes morph into each other.
 */
@Immutable
class SonaTextFieldShapes(val shape: CornerBasedShape, val focusedShape: CornerBasedShape) {
    /** The shape [progress] of the way from [shape] to [focusedShape], for a field of [size]. */
    internal fun at(progress: Float, size: Size, density: Density): Shape {
        fun corner(rest: CornerSize, focused: CornerSize): Float =
            lerp(rest.toPx(size, density), focused.toPx(size, density), progress).coerceAtLeast(0f)
        return RoundedCornerShape(
            topStart = corner(shape.topStart, focusedShape.topStart),
            topEnd = corner(shape.topEnd, focusedShape.topEnd),
            bottomEnd = corner(shape.bottomEnd, focusedShape.bottomEnd),
            bottomStart = corner(shape.bottomStart, focusedShape.bottomStart),
        )
    }

    override fun equals(other: Any?): Boolean =
        other is SonaTextFieldShapes && shape == other.shape && focusedShape == other.focusedShape

    override fun hashCode(): Int = 31 * shape.hashCode() + focusedShape.hashCode()
}

object SonaTextFieldDefaults {
    /** The room between the fields of a group: a segmented list's gap. */
    val SegmentedGap: Dp = ListItemDefaults.SegmentedGap

    /** A field on its own: round at rest, a little squarer focused. */
    @Composable
    fun shapes(): SonaTextFieldShapes =
        SonaTextFieldShapes(MaterialTheme.shapes.extraLarge, MaterialTheme.shapes.large)

    /**
     * The field at [index] of [count] joined into one group: the group's outer corners round, the
     * ones between fields small, as Sona's segmented lists are. Focused, a field rounds all its
     * corners alike, so the one being typed in stands out from its group as it morphs.
     */
    @Composable
    fun segmentedShapes(index: Int, count: Int): SonaTextFieldShapes {
        val outer = MaterialTheme.shapes.largeIncreased
        val inner = MaterialTheme.shapes.extraSmall
        val top = if (index == 0) outer else inner
        val bottom = if (index == count - 1) outer else inner
        return SonaTextFieldShapes(
            shape =
                RoundedCornerShape(
                    topStart = top.topStart,
                    topEnd = top.topEnd,
                    bottomEnd = bottom.bottomEnd,
                    bottomStart = bottom.bottomStart,
                ),
            focusedShape = MaterialTheme.shapes.large,
        )
    }

    /**
     * At rest, the surface container one tone above a grouped list's, so a field reads as a place
     * to type among list items; focused, the primary container; wrong, the error container.
     */
    @Composable
    fun colors(scheme: ColorScheme = MaterialTheme.colorScheme): SonaTextFieldColors =
        remember(scheme) {
            SonaTextFieldColors(
                container = scheme.surfaceContainerHighest,
                focusedContainer = scheme.primaryContainer,
                errorContainer = scheme.errorContainer,
                disabledContainer = scheme.onSurface.copy(alpha = DisabledContainerAlpha),
                content = scheme.onSurface,
                focusedContent = scheme.onPrimaryContainer,
                errorContent = scheme.onErrorContainer,
                disabledContent = scheme.onSurface.copy(alpha = DisabledContentAlpha),
                accent = scheme.onSurfaceVariant,
                focusedAccent = scheme.onPrimaryContainer,
                errorAccent = scheme.onErrorContainer,
                cursor = scheme.onPrimaryContainer,
                errorCursor = scheme.error,
                supportingText = scheme.onSurfaceVariant,
                errorText = scheme.error,
            )
        }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

private val FieldMinHeight = 64.dp
private val LeadingIconSize = 24.dp
private val TrailingSize = 48.dp

/** Where the text starts under the raised label. */
private val TextTop = 18.dp

/** How far below its raised place the label sits while it is in the field: centered in it. */
private val RestingLabelOffset = 10.dp
private const val RaisedLabelScale = 0.75f
private const val PlaceholderAlpha = 0.6f
private const val DisabledContainerAlpha = 0.06f
private const val DisabledContentAlpha = 0.38f

@PreviewLightDark
@Composable
private fun SonaTextFieldPreview() {
    SonaTheme {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SonaTextField(
                    value = "",
                    onValueChange = {},
                    label = "Server",
                    leadingIcon = Icons.Rounded.Dns,
                )
                SonaTextField(
                    value = "192.168.1.10",
                    onValueChange = {},
                    label = "Server",
                    leadingIcon = Icons.Rounded.Dns,
                    supportingText = "Your Asterisk's address",
                )
                SonaTextField(
                    value = "lab example",
                    onValueChange = {},
                    label = "Server",
                    leadingIcon = Icons.Rounded.Dns,
                    error = "Not a server address",
                )
                SonaTextField(
                    value = "secret",
                    onValueChange = {},
                    label = "Password",
                    trailingContent = {
                        IconButton(onClick = {}) { Icon(Icons.Rounded.Visibility, null) }
                    },
                )
            }
        }
    }
}
