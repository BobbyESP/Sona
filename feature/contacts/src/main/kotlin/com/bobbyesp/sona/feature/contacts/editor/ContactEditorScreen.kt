/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.contacts.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.sona.core.designsystem.components.ListHeadline
import com.bobbyesp.sona.core.designsystem.components.rememberSyncedTextFieldState
import com.bobbyesp.sona.core.designsystem.components.segmentedListColors
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme
import com.bobbyesp.sona.core.ui.events.CollectEventsEffect
import com.bobbyesp.sona.feature.contacts.R

@Composable
internal fun ContactEditorRoute(viewModel: ContactEditorViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    viewModel.effects.CollectEventsEffect { effect ->
        when (effect) {
            ContactEditorEffect.Leave -> onBack()
        }
    }
    ContactEditorScreen(state = state, onIntent = viewModel::onSendIntent, onBack = onBack)
}

/**
 * Adding or editing a contact, in Material 3 Expressive: the numbers as a segmented group (each
 * with the usual labels one touch away), the favorite as a switch row, and the one action that
 * matters, Save, always in reach under the keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ContactEditorScreen(
    state: ContactEditorState,
    onIntent: (ContactEditorIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) R.string.contact_editor_new
                            else R.string.contact_editor_edit,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.contact_editor_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Box(
                Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Button(
                    onClick = { onIntent(ContactEditorIntent.Save) },
                    shapes = ButtonDefaults.shapes(),
                    contentPadding =
                        ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                    modifier =
                        Modifier.widthIn(max = 840.dp)
                            .fillMaxWidth()
                            .size(
                                width = Dp.Unspecified,
                                height = ButtonDefaults.MediumContainerHeight,
                            ),
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null)
                    Spacer(
                        Modifier.size(
                            ButtonDefaults.iconSpacingFor(ButtonDefaults.MediumContainerHeight),
                        ),
                    )
                    Text(stringResource(R.string.contact_editor_save))
                }
            }
        },
    ) { padding ->
        if (!state.loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            return@Scaffold
        }
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 840.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    state =
                        rememberSyncedTextFieldState(state.name) {
                            onIntent(ContactEditorIntent.Name(it))
                        },
                    label = { Text(stringResource(R.string.contact_editor_name)) },
                    isError = state.showProblems && state.nameMissing,
                    supportingText =
                        if (state.showProblems && state.nameMissing) {
                            { Text(stringResource(R.string.contact_editor_need_name)) }
                        } else null,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.contact_editor_numbers),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp).semantics { heading() },
                )
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    state.numbers.forEachIndexed { index, field ->
                        NumberCard(
                            field = field,
                            index = index,
                            count = state.numbers.size,
                            problem = state.showProblems && state.numberMissing && index == 0,
                            onIntent = onIntent,
                        )
                    }
                }
                FilledTonalButton(
                    onClick = { onIntent(ContactEditorIntent.AddNumber) },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(ButtonDefaults.MinHeight)))
                    Text(stringResource(R.string.contact_editor_add_number))
                }
                SegmentedListItem(
                    checked = state.favorite,
                    onCheckedChange = { onIntent(ContactEditorIntent.Favorite(it)) },
                    shapes = ListItemDefaults.segmentedShapes(0, 1),
                    colors = segmentedListColors(),
                    leadingContent = { Icon(Icons.Rounded.Star, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = state.favorite,
                            onCheckedChange = null,
                            thumbContent =
                                if (state.favorite) {
                                    {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(SwitchDefaults.IconSize),
                                        )
                                    }
                                } else null,
                        )
                    },
                    supportingContent = {
                        Text(stringResource(R.string.contact_editor_favorite_hint))
                    },
                    content = { ListHeadline(stringResource(R.string.contact_editor_favorite)) },
                )
            }
        }
    }
}

/** One number: its usual labels to pick from, the label, the number, and the way to remove it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NumberCard(
    field: NumberField,
    index: Int,
    count: Int,
    problem: Boolean,
    onIntent: (ContactEditorIntent) -> Unit,
) {
    val labels =
        listOf(
            stringResource(R.string.contact_label_extension),
            stringResource(R.string.contact_label_mobile),
            stringResource(R.string.contact_label_home),
            stringResource(R.string.contact_label_work),
        )
    Surface(
        shape = ListItemDefaults.segmentedShapes(index, count).shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.forEach { label ->
                    FilterChip(
                        selected = field.label == label,
                        onClick = { onIntent(ContactEditorIntent.Label(index, label)) },
                        label = { Text(label) },
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                OutlinedTextField(
                    state =
                        rememberSyncedTextFieldState(field.value) {
                            onIntent(ContactEditorIntent.Value(index, it))
                        },
                    label = { Text(stringResource(R.string.contact_editor_number)) },
                    isError = problem,
                    supportingText =
                        if (problem) {
                            { Text(stringResource(R.string.contact_editor_need_number)) }
                        } else null,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Done,
                        ),
                    modifier = Modifier.weight(1f),
                )
                if (count > 1) {
                    IconButton(onClick = { onIntent(ContactEditorIntent.RemoveNumber(index)) }) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription =
                                stringResource(R.string.contact_editor_remove_number),
                        )
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun ContactEditorPreview() {
    SonaTheme {
        ContactEditorScreen(
            state =
                ContactEditorState(
                    name = "Ana",
                    numbers = listOf(NumberField("Extension", "102"), NumberField("", "")),
                ),
            onIntent = {},
            onBack = {},
        )
    }
}
