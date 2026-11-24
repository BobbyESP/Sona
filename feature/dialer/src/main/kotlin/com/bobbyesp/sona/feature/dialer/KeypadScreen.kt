/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.dialer

import android.content.ClipData
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.sona.calling.ui.problemText
import com.bobbyesp.sona.core.designsystem.components.CallButton
import com.bobbyesp.sona.core.designsystem.components.Dialpad
import com.bobbyesp.sona.core.designsystem.components.DialpadKey
import com.bobbyesp.sona.core.designsystem.components.StandardDialpad
import com.bobbyesp.sona.core.designsystem.components.withVoicemail
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme
import com.bobbyesp.sona.core.ui.TopLevelScaffold
import com.bobbyesp.sona.core.ui.contribution.LocalCallScreen
import com.bobbyesp.sona.core.ui.events.CollectEventsEffect
import com.bobbyesp.sona.core.ui.rememberCallStarter
import com.bobbyesp.sona.directory.domain.Suggestion
import com.bobbyesp.sona.directory.ui.Avatar
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun KeypadRoute(viewModel: KeypadViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openCall = LocalCallScreen.current
    viewModel.effects.CollectEventsEffect { effect ->
        when (effect) {
            KeypadEffect.OpenCall -> openCall()
        }
    }

    val callStarter = rememberCallStarter()
    KeypadScreen(
        state = state,
        onIntent = { intent ->
            if (intent.startsCall) callStarter.start { viewModel.onSendIntent(intent) }
            else viewModel.onSendIntent(intent)
        },
    )
}

/**
 * The keypad tab, from the top: what was typed, loud and round, with the address it calls in a pill
 * under it (or, when nothing could be dialed, the reason in its place); who it could be; the
 * dialpad; and the action row, the call button between the keyboard switch and delete.
 *
 * The keys type extensions and numbers. A SIP address is pasted (a long press on the number), or
 * typed: the keyboard switch swaps the dialpad for the system's keyboard, and a hardware keyboard
 * types into the screen as it is, Enter calling.
 *
 * Stacked where there is height for it; side by side where width is what there is (a phone in
 * landscape), with the keys sharing the height. Decided from the room this screen was given, never
 * from the window, which it may share.
 *
 * This screen is the reference for how Sona speaks Material 3 Expressive: type from the theme's
 * roles (the numerals for numbers), shapes that morph on the theme's spatial springs, fades on its
 * effects springs, and colors only from the scheme, each container with its own content color.
 */
@Composable
internal fun KeypadScreen(
    state: KeypadState,
    onIntent: (KeypadIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var typing by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val paste: () -> Unit = {
        scope.launch { clipboard.text()?.let { onIntent(KeypadIntent.Paste(it)) } }
    }

    val keys = remember { FocusRequester() }
    LaunchedEffect(typing) { if (!typing) runCatching { keys.requestFocus() } }

    val voicemailLabel = stringResource(R.string.dialer_voicemail)
    val hasVoicemail = state.voicemail != null
    val dialpadKeys =
        remember(hasVoicemail, voicemailLabel) {
            if (hasVoicemail) StandardDialpad.withVoicemail(voicemailLabel) else StandardDialpad
        }
    val onLongPress: (DialpadKey) -> Boolean = { key ->
        if (key.symbol == '1' && hasVoicemail) {
            onIntent(KeypadIntent.Voicemail)
            true
        } else {
            false
        }
    }

    val number: @Composable () -> Unit = {
        DialNumber(state = state, typing = typing, onIntent = onIntent, onPaste = paste)
    }
    val address: @Composable () -> Unit = {
        Box(Modifier.padding(bottom = 8.dp)) { AddressLine(state) }
    }
    val suggestions: @Composable () -> Unit = {
        SuggestionRow(
            suggestions = state.suggestions,
            onPick = { onIntent(KeypadIntent.Pick(it)) },
            onCall = { onIntent(KeypadIntent.CallNumber(it)) },
        )
    }
    val actions: @Composable () -> Unit = {
        KeypadActions(
            typing = typing,
            onTyping = { typing = it },
            canCall = state.canCall,
            canDelete = state.input.isNotEmpty(),
            onIntent = onIntent,
        )
    }

    TopLevelScaffold(title = stringResource(R.string.dialer_title), modifier = modifier) {
        padding,
        contentModifier ->
        BoxWithConstraints(
            contentModifier
                .padding(padding)
                .padding(horizontal = ScreenMargin)
                .focusRequester(keys)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (typing || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val typed = event.utf16CodePoint.toChar()
                    when {
                        event.key == Key.Enter || event.key == Key.NumPadEnter ->
                            onIntent(KeypadIntent.Call)
                        event.key == Key.Backspace -> onIntent(KeypadIntent.Delete)
                        event.isCtrlPressed && event.key == Key.V -> paste()
                        event.isCtrlPressed || typed.isISOControl() || typed.isWhitespace() ->
                            return@onPreviewKeyEvent false
                        else -> onIntent(KeypadIntent.Type(typed))
                    }
                    true
                },
        ) {
            val motion = MaterialTheme.motionScheme
            if (maxWidth > maxHeight) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        number()
                        address()
                        suggestions()
                        Spacer(Modifier.height(12.dp))
                        actions()
                    }
                    AnimatedVisibility(
                        visible = !typing,
                        modifier = Modifier.weight(1f, fill = false),
                        enter =
                            fadeIn(motion.defaultEffectsSpec()) +
                                expandHorizontally(motion.defaultSpatialSpec()),
                        exit =
                            fadeOut(motion.fastEffectsSpec()) +
                                shrinkHorizontally(motion.fastSpatialSpec()),
                    ) {
                        Dialpad(
                            onKey = { onIntent(KeypadIntent.Type(it)) },
                            keys = dialpadKeys,
                            tones = state.dialpadTones,
                            keyAspectRatio = null,
                            onLongPress = onLongPress,
                            modifier = Modifier.fillMaxHeight(),
                        )
                    }
                }
            } else {
                // Where height is short (a small phone, a large font), the keys flatten rather than
                // push the call button off the screen.
                val keyAspectRatio = if (maxHeight < CompactKeypadHeight) 2.1f else 1.6f
                StackedKeypad(
                    // With the keyboard up, the number and the action row stay above it.
                    modifier = Modifier.fillMaxSize().imePadding(),
                    number = number,
                    address = address,
                    suggestions = suggestions,
                    actions = { Box(Modifier.padding(vertical = 20.dp)) { actions() } },
                ) {
                    AnimatedVisibility(
                        visible = !typing,
                        enter =
                            fadeIn(motion.defaultEffectsSpec()) +
                                expandVertically(motion.defaultSpatialSpec()) +
                                scaleIn(motion.defaultSpatialSpec(), initialScale = 0.9f),
                        exit =
                            fadeOut(motion.fastEffectsSpec()) +
                                shrinkVertically(motion.fastSpatialSpec()) +
                                scaleOut(motion.fastSpatialSpec(), targetScale = 0.9f),
                    ) {
                        Dialpad(
                            onKey = { onIntent(KeypadIntent.Type(it)) },
                            keys = dialpadKeys,
                            tones = state.dialpadTones,
                            keyAspectRatio = keyAspectRatio,
                            onLongPress = onLongPress,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The keypad stacked: the number, the address it calls, who it could be, the dialpad, the action
 * row. The address, the suggestions and the actions always get the height they ask for, so a
 * suggestion never squeezes the address. What gives way is the number, which shrinks to fit down to
 * [NumberMinHeight], so the keys stay where they are while one types; only below that do the keys
 * flatten. Height to spare goes above the number.
 */
@Composable
private fun StackedKeypad(
    number: @Composable () -> Unit,
    address: @Composable () -> Unit,
    suggestions: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dialpad: @Composable () -> Unit,
) {
    Layout(
        contents = listOf(number, address, suggestions, dialpad, actions),
        modifier = modifier,
    ) { (numberM, addressM, suggestionsM, dialpadM, actionsM), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        fun List<Measurable>.measureAll(c: Constraints) = map { it.measure(c) }
        fun List<Placeable>.height() = sumOf { it.height }
        fun room(used: Int) =
            loose.copy(maxHeight = (constraints.maxHeight - used).coerceAtLeast(0))

        val actionsP = actionsM.measureAll(loose)
        val suggestionsP = suggestionsM.measureAll(loose)
        val addressP = addressM.measureAll(loose)
        val fixed = actionsP.height() + suggestionsP.height() + addressP.height()
        val dialpadP = dialpadM.measureAll(room(fixed + NumberMinHeight.roundToPx()))
        val numberP = numberM.measureAll(room(fixed + dialpadP.height()))

        val width = constraints.maxWidth
        layout(width, constraints.maxHeight) {
            // From the bottom up: the actions, the dialpad, the suggestions, the address, the
            // number.
            var bottom = constraints.maxHeight
            for (group in listOf(actionsP, dialpadP, suggestionsP, addressP, numberP)) {
                for (placeable in group.asReversed()) {
                    bottom -= placeable.height
                    placeable.place((width - placeable.width) / 2, bottom.coerceAtLeast(0))
                }
            }
        }
    }
}

/** The text on the clipboard, when what is there is text. */
private suspend fun Clipboard.text(): String? {
    val data: ClipData = getClipEntry()?.clipData ?: return null
    if (data.itemCount == 0) return null
    return data.getItemAt(0).text?.toString()?.takeIf { it.isNotBlank() }
}

/**
 * What was typed. From the keys it is a number in the theme's large numerals, shrinking to fit
 * rather than scroll (in width, and in height where the screen is short of it), that gives a small
 * spring at each new key; a long press pastes. With the keyboard it is a text field, whose action
 * calls.
 */
@Composable
private fun DialNumber(
    state: KeypadState,
    typing: Boolean,
    onIntent: (KeypadIntent) -> Unit,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val pasteLabel = stringResource(R.string.dialer_paste)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = NumberHeight)
                .then(
                    if (typing) {
                        Modifier
                    } else {
                        Modifier.combinedClickable(
                            interactionSource = null,
                            indication = null,
                            onLongClickLabel = pasteLabel,
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPaste()
                            },
                            onClick = {},
                        )
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (typing) TypedField(state.input, onIntent) else DialedNumber(state.input)
    }
}

/** The number from the keys, or the hint while there is none. */
@Composable
private fun DialedNumber(input: String) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = input.isEmpty(),
        transitionSpec = {
            (fadeIn(motion.defaultEffectsSpec()) +
                scaleIn(motion.defaultSpatialSpec(), initialScale = 0.8f)) togetherWith
                fadeOut(motion.fastEffectsSpec())
        },
        contentAlignment = Alignment.Center,
        label = "DialedNumber",
    ) { empty ->
        if (empty) {
            Text(
                text = stringResource(R.string.dialer_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            PoppingNumber(input)
        }
    }
}

/**
 * The number, which grows a little and springs back each time a key is added to it: the keypad
 * answers the finger at the top of the screen as well as under it.
 */
@Composable
private fun PoppingNumber(input: String) {
    val motion = MaterialTheme.motionScheme
    val pop = remember { Animatable(1f) }
    var shownLength by remember { mutableIntStateOf(input.length) }
    LaunchedEffect(input) {
        val grew = input.length > shownLength
        shownLength = input.length
        if (grew) {
            pop.snapTo(PopScale)
            pop.animateTo(1f, motion.fastSpatialSpec())
        }
    }
    BasicText(
        text = input,
        modifier =
            Modifier.graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
                .semantics { contentDescription = input.toList().joinToString(" ") },
        style =
            SonaTheme.typography.numerals.large.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            ),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 22.sp, maxFontSize = 52.sp),
    )
}

/** What the line under the number says: the address it calls, or why the last call failed. */
private data class AddressLineContent(val text: String, val isProblem: Boolean)

/**
 * The address the number calls, in a tonal pill, or why nothing was dialed, in an error container,
 * spoken by TalkBack as it appears. A change of kind springs; a change of the address as it is
 * typed only replaces the text, so the pill does not jump at every key.
 */
@Composable
private fun AddressLine(state: KeypadState) {
    val motion = MaterialTheme.motionScheme
    val problem = state.problem?.problemText()
    val preview = state.preview
    val content =
        when {
            problem != null -> AddressLineContent(problem, isProblem = true)
            preview != null -> AddressLineContent(preview, isProblem = false)
            else -> null
        }
    AnimatedContent(
        targetState = content,
        modifier = Modifier.heightIn(min = AddressLineHeight),
        contentKey = { it?.isProblem },
        transitionSpec = {
            (fadeIn(motion.defaultEffectsSpec()) +
                scaleIn(motion.fastSpatialSpec(), initialScale = 0.85f)) togetherWith
                (fadeOut(motion.fastEffectsSpec()) +
                    scaleOut(motion.fastSpatialSpec(), targetScale = 0.85f)) using
                SizeTransform(clip = false)
        },
        contentAlignment = Alignment.Center,
        label = "AddressLine",
    ) { line ->
        if (line != null) AddressPill(line)
    }
}

@Composable
private fun AddressPill(line: AddressLineContent) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = if (line.isProblem) MaterialTheme.shapes.large else CircleShape,
        color = if (line.isProblem) colors.errorContainer else colors.surfaceContainer,
        contentColor = if (line.isProblem) colors.onErrorContainer else colors.onSurfaceVariant,
        modifier =
            Modifier.widthIn(max = PillMaxWidth).semantics {
                if (line.isProblem) liveRegion = LiveRegionMode.Polite
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector =
                    if (line.isProblem) Icons.Rounded.ErrorOutline
                    else Icons.Rounded.AlternateEmail,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = line.text,
                style =
                    if (line.isProblem) MaterialTheme.typography.bodyMedium
                    else MaterialTheme.typography.labelLarge,
                fontFamily = if (line.isProblem) null else SonaTheme.typography.monospace,
                maxLines = if (line.isProblem) 3 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The field while the system's keyboard types into it. What the field's rules refuse (a space)
 * never shows: the text comes back from the state as it was kept.
 */
@Composable
private fun TypedField(input: String, onIntent: (KeypadIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    val state = rememberTextFieldState(input, TextRange(input.length))
    val latestInput by rememberUpdatedState(input)

    // What the state sent that has not come back yet: its echo is not a change from outside.
    val sent = remember { ArrayDeque<String>() }
    LaunchedEffect(input) {
        val echo = sent.indexOf(input)
        if (echo >= 0) {
            repeat(echo + 1) { sent.removeFirst() }
        } else if (input != state.text.toString()) {
            sent.clear()
            state.setTextAndPlaceCursorAtEnd(input)
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            .collect { text ->
                if (text != latestInput) {
                    sent.addLast(text)
                    onIntent(KeypadIntent.Edit(text))
                }
            }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BasicTextField(
        state = state,
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        // The field's rules are applied as it is typed, so what shows is what is kept.
        inputTransformation = DialInputTransformation,
        textStyle =
            MaterialTheme.typography.headlineMediumEmphasized.copy(
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            ),
        lineLimits = TextFieldLineLimits.SingleLine,
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Go,
            ),
        onKeyboardAction = KeyboardActionHandler { onIntent(KeypadIntent.Call) },
        decorator = { inner ->
            Box(contentAlignment = Alignment.Center) {
                if (state.text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.dialer_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                inner()
            }
        },
    )
}

/** Keeps what [DialInput] allows, with the cursor at the end of what was kept. */
private val DialInputTransformation = InputTransformation {
    val kept = DialInput.typed(asCharSequence().toString())
    if (kept != asCharSequence().toString()) {
        replace(0, length, kept)
        placeCursorAtEnd()
    }
}

/**
 * The action row: the keyboard switch, the call button, delete. The call button is the largest
 * thing on the screen after the number; the two beside it are tonal and a size down, so it is never
 * in doubt which one calls. Delete keeps its room while hidden, so the call button stays centered.
 */
@Composable
private fun KeypadActions(
    typing: Boolean,
    onTyping: (Boolean) -> Unit,
    canCall: Boolean,
    canDelete: Boolean,
    onIntent: (KeypadIntent) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val sideSize = IconButtonDefaults.mediumContainerSize()
    Row(
        horizontalArrangement = Arrangement.spacedBy(ActionSpacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconToggleButton(
            checked = typing,
            onCheckedChange = onTyping,
            shapes = IconButtonDefaults.toggleableShapes(),
            modifier = Modifier.size(sideSize),
        ) {
            Icon(
                imageVector = if (typing) Icons.Rounded.Dialpad else Icons.Rounded.Keyboard,
                contentDescription =
                    stringResource(
                        if (typing) R.string.dialer_use_keypad else R.string.dialer_use_keyboard,
                    ),
                modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
            )
        }
        CallButton(
            onClick = { onIntent(KeypadIntent.Call) },
            enabled = canCall,
            contentDescription = stringResource(R.string.dialer_call),
        )
        Box(Modifier.size(sideSize), contentAlignment = Alignment.Center) {
            // Not the row's own overload: this one is in the box, and shrinks nothing around it.
            androidx.compose.animation.AnimatedVisibility(
                visible = canDelete,
                enter =
                    fadeIn(motion.defaultEffectsSpec()) +
                        scaleIn(motion.fastSpatialSpec(), initialScale = 0.4f),
                exit =
                    fadeOut(motion.fastEffectsSpec()) +
                        scaleOut(motion.fastSpatialSpec(), targetScale = 0.4f),
            ) {
                DeleteButton(
                    onDelete = { onIntent(KeypadIntent.Delete) },
                    onClear = { onIntent(KeypadIntent.Clear) },
                    modifier = Modifier.size(sideSize),
                )
            }
        }
    }
}

/**
 * Delete, and clear on a long press: Material's tonal icon button has no long press, so this is one
 * made the same way, round at rest and squaring under the finger on the spatial spring.
 */
@Composable
private fun DeleteButton(onDelete: () -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val corner =
        animateFloatAsState(
            targetValue = if (pressed) 30f else 50f,
            animationSpec = motion.fastSpatialSpec(),
            label = "DeleteCorner",
        )
    val container = colors.secondaryContainer
    val deleteLabel = stringResource(R.string.dialer_delete)
    val clearLabel = stringResource(R.string.dialer_clear)
    Box(
        modifier =
            modifier
                .graphicsLayer {
                    // A percent of the shorter side, worked out here: the key's size is only known
                    // now.
                    shape = RoundedCornerShape(size.minDimension * corner.value / 100f)
                    clip = true
                }
                .drawBehind { drawRect(container) }
                .combinedClickable(
                    interactionSource = interactionSource,
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
            tint = colors.onSecondaryContainer,
            modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
        )
    }
}

/**
 * Who the typed digits could be: pills to tap, so a contact is dialed without typing it all. The
 * pill fills the number; the call button at its end calls at once. The row grows in and out on the
 * spring, and each pill finds its place in it as the list changes.
 */
@Composable
private fun SuggestionRow(
    suggestions: List<Suggestion>,
    onPick: (String) -> Unit,
    onCall: (String) -> Unit,
) {
    val motion = MaterialTheme.motionScheme

    val lastShown = remember { LastShown(suggestions) }
    if (suggestions.isNotEmpty()) lastShown.value = suggestions
    val shown = lastShown.value
    AnimatedVisibility(
        visible = suggestions.isNotEmpty(),
        enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
        exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.fastSpatialSpec()),
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            items(shown, key = { it.number }) { suggestion ->
                SuggestionPill(
                    suggestion = suggestion,
                    onPick = onPick,
                    onCall = onCall,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

private class LastShown(var value: List<Suggestion>)

@Composable
private fun SuggestionPill(
    suggestion: Suggestion,
    onPick: (String) -> Unit,
    onCall: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val name = suggestion.name
    Surface(
        onClick = { onPick(suggestion.number) },
        shape = CircleShape,
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (suggestion.fromContact && name != null) {
                Avatar(name, size = SuggestionMarkSize)
            } else {
                Surface(
                    shape = CircleShape,
                    color = colors.tertiaryContainer,
                    contentColor = colors.onTertiaryContainer,
                    modifier = Modifier.size(SuggestionMarkSize),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Column(Modifier.widthIn(max = SuggestionTextMaxWidth)) {
                Text(
                    text = name ?: suggestion.number,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        if (name != null) suggestion.number
                        else stringResource(R.string.dialer_suggestion_recent),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledTonalIconButton(
                onClick = { onCall(suggestion.number) },
                shapes = IconButtonDefaults.shapes(),
                colors =
                    IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary,
                    ),
                modifier = Modifier.size(IconButtonDefaults.smallContainerSize()),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Call,
                    contentDescription =
                        stringResource(R.string.dialer_call_suggestion, name ?: suggestion.number),
                    modifier = Modifier.size(IconButtonDefaults.smallIconSize),
                )
            }
        }
    }
}

private val ScreenMargin = 24.dp
private val NumberHeight = 72.dp

/** The least the number keeps where height is short, before the keys give any of theirs. */
private val NumberMinHeight = 44.dp
private val AddressLineHeight = 36.dp
private val PillMaxWidth = 360.dp
private val ActionSpacing = 20.dp
private val SuggestionMarkSize = 36.dp
private val SuggestionTextMaxWidth = 160.dp

/** Below this the keys flatten, so the action row keeps its place on the screen. */
private val CompactKeypadHeight = 560.dp

/** How much the number grows at a new key before it springs back. */
private const val PopScale = 1.06f

@PreviewLightDark
@Composable
private fun KeypadScreenPreview() {
    SonaTheme {
        KeypadScreen(
            state =
                KeypadState(
                    input = "102",
                    domain = "lab.local",
                    voicemail = "*97",
                    suggestions =
                        listOf(
                            Suggestion("Ana Gómez", "102", fromContact = true),
                            Suggestion(null, "1024", fromContact = false),
                        ),
                ),
            onIntent = {},
        )
    }
}

@Preview(name = "Empty")
@Composable
private fun KeypadScreenEmptyPreview() {
    SonaTheme { KeypadScreen(state = KeypadState(domain = "lab.local"), onIntent = {}) }
}

@Preview(name = "Landscape", widthDp = 800, heightDp = 360)
@Composable
private fun KeypadScreenLandscapePreview() {
    SonaTheme {
        KeypadScreen(state = KeypadState(input = "600111222", domain = "lab.local"), onIntent = {})
    }
}
