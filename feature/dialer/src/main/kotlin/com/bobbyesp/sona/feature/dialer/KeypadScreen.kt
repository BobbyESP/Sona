/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.dialer

import android.content.ClipData
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.sona.calling.ui.problemText
import com.bobbyesp.sona.core.designsystem.components.CallButton
import com.bobbyesp.sona.core.designsystem.components.Dialpad
import com.bobbyesp.sona.core.designsystem.components.DialpadKey
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
    // A call needs the microphone, and notifications to show it: asked for at the first call.
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
 * The keypad tab: what was typed with the address it calls under it, the dialpad, and the call
 * button. When nothing could be dialed, the reason replaces the address.
 *
 * The keys type extensions and numbers. A SIP address is pasted (a long press on the field), or
 * typed: the keyboard button swaps the dialpad for the system's keyboard, and a hardware keyboard
 * types into the field as it is, Enter calling.
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
    var typing by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val paste: () -> Unit = {
        scope.launch { clipboard.text()?.let { onIntent(KeypadIntent.Paste(it)) } }
    }
    // Keys of a hardware keyboard reach the screen while it holds the focus; with the system's
    // keyboard up, the text field has it and takes them itself.
    val keys = remember { FocusRequester() }
    LaunchedEffect(typing) { if (!typing) runCatching { keys.requestFocus() } }

    val field: @Composable () -> Unit = {
        DialField(
            state = state,
            typing = typing,
            onTyping = { typing = it },
            onIntent = onIntent,
            onPaste = paste,
        )
    }
    val callButton: @Composable () -> Unit = {
        CallButton(
            onClick = { onIntent(KeypadIntent.Call) },
            enabled = state.canCall,
            contentDescription = stringResource(R.string.dialer_call),
        )
    }
    val onLongPress: (DialpadKey) -> Boolean = { key ->
        if (key.symbol == '1' && state.voicemail != null) {
            onIntent(KeypadIntent.Voicemail)
            true
        } else {
            false
        }
    }

    TopLevelScaffold(title = stringResource(R.string.dialer_title), modifier = modifier) {
        padding,
        contentModifier ->
        BoxWithConstraints(
            contentModifier
                .padding(padding)
                .padding(horizontal = 24.dp)
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
                    if (!typing) {
                        Dialpad(
                            onKey = { onIntent(KeypadIntent.Type(it)) },
                            keyAspectRatio = null,
                            onLongPress = onLongPress,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            } else {
                Column(
                    // With the keyboard up, the field and the call button stay above it.
                    modifier = Modifier.fillMaxSize().imePadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Spacer(Modifier.weight(1f))
                    field()
                    Spacer(Modifier.size(24.dp))
                    if (!typing) {
                        Dialpad(
                            onKey = { onIntent(KeypadIntent.Type(it)) },
                            onLongPress = onLongPress,
                        )
                        Spacer(Modifier.size(24.dp))
                    }
                    callButton()
                    Spacer(Modifier.size(24.dp))
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
 * What was typed, with the button that swaps the dialpad and the keyboard before it and delete
 * after it. From the keys it is a number that shrinks to fit rather than scroll, and a long press
 * pastes; with the keyboard it is a text field, whose action calls.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialField(
    state: KeypadState,
    typing: Boolean,
    onTyping: (Boolean) -> Unit,
    onIntent: (KeypadIntent) -> Unit,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val input = state.input
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val onDelete = { onIntent(KeypadIntent.Delete) }
    val onClear = { onIntent(KeypadIntent.Clear) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = modifier.fillMaxWidth().heightIn(min = 72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onTyping(!typing) }) {
                Icon(
                    imageVector = if (typing) Icons.Rounded.Dialpad else Icons.Rounded.Keyboard,
                    contentDescription =
                        stringResource(
                            if (typing) R.string.dialer_use_keypad
                            else R.string.dialer_use_keyboard,
                        ),
                    tint = colors.onSurfaceVariant,
                )
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (typing) {
                    TypedField(input, onIntent)
                } else {
                    val pasteLabel = stringResource(R.string.dialer_paste)
                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .combinedClickable(
                                    interactionSource = null,
                                    indication = null,
                                    onLongClickLabel = pasteLabel,
                                    onLongClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onPaste()
                                    },
                                    onClick = {},
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
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
                                autoSize =
                                    TextAutoSize.StepBased(
                                        minFontSize = 20.sp,
                                        maxFontSize = 45.sp,
                                    ),
                            )
                        }
                    }
                }
            }
            // It keeps its room when hidden, so the number stays centered between the two buttons.
            Box(Modifier.size(48.dp)) {
                androidx.compose.animation.AnimatedVisibility(
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
        val problem = state.problem?.problemText()
        val line = problem ?: state.preview
        Text(
            text = line.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (problem == null) SonaTheme.typography.monospace else null,
            color = if (problem != null) colors.error else colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.heightIn(min = 24.dp),
        )
        SuggestionRow(
            state.suggestions,
            onPick = { onIntent(KeypadIntent.Pick(it)) },
            onCall = { onIntent(KeypadIntent.CallNumber(it)) },
        )
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
    var value by remember { mutableStateOf(TextFieldValue(input, TextRange(input.length))) }
    // What this field sent and has not yet seen come back. The state answers a moment after each
    // key: an answer to an earlier key must not replace what was typed since (seen on a phone,
    // where fast typing lost characters). Only text this field did not send is taken from the
    // state: the delete button, a suggestion, a paste.
    val sent = remember { ArrayDeque<String>() }
    LaunchedEffect(input) {
        val echo = sent.indexOf(input)
        if (echo >= 0) {
            repeat(echo + 1) { sent.removeFirst() }
        } else if (input != value.text) {
            sent.clear()
            value = TextFieldValue(input, TextRange(input.length))
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BasicTextField(
        value = value,
        onValueChange = { typed ->
            // The field's rules are applied here, at once, so what shows is what is kept.
            val kept = DialInput.typed(typed.text)
            val changed = kept != value.text
            value = if (kept == typed.text) typed else TextFieldValue(kept, TextRange(kept.length))
            if (changed) {
                sent.addLast(kept)
                onIntent(KeypadIntent.Edit(kept))
            }
        },
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        textStyle =
            MaterialTheme.typography.headlineSmall.copy(
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            ),
        singleLine = true,
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Go,
            ),
        keyboardActions = KeyboardActions(onGo = { onIntent(KeypadIntent.Call) }),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.Center) {
                if (value.text.isEmpty()) {
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

@PreviewLightDark
@Composable
private fun KeypadScreenPreview() {
    SonaTheme {
        KeypadScreen(state = KeypadState(input = "102", domain = "lab.local"), onIntent = {})
    }
}

/**
 * Who the typed digits could be: chips to tap, so a contact is dialed without typing it all. The
 * chip fills the field; the phone at its end calls at once.
 */
@Composable
private fun SuggestionRow(
    suggestions: List<Suggestion>,
    onPick: (String) -> Unit,
    onCall: (String) -> Unit,
) {
    if (suggestions.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        items(suggestions, key = { it.number }) { suggestion ->
            val who = suggestion.name ?: suggestion.number
            AssistChip(
                onClick = { onPick(suggestion.number) },
                label = {
                    Text(
                        text =
                            suggestion.name?.let { "$it · ${suggestion.number}" }
                                ?: suggestion.number,
                        maxLines = 1,
                    )
                },
                leadingIcon = {
                    if (suggestion.fromContact && suggestion.name != null) {
                        Avatar(suggestion.name!!, size = 28.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                trailingIcon = {
                    IconButton(
                        onClick = { onCall(suggestion.number) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription =
                                stringResource(R.string.dialer_call_suggestion, who),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        }
    }
}
