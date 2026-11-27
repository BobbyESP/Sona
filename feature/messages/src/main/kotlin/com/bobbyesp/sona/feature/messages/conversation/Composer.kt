/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.messages.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.bobbyesp.sona.core.designsystem.components.ListHeadline
import com.bobbyesp.sona.core.designsystem.components.segmentedListColors
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme
import com.bobbyesp.sona.feature.messages.R
import com.bobbyesp.sona.messaging.ui.SendProblem
import com.bobbyesp.sona.messaging.ui.text

/**
 * Where a message is written: the attach button, a field that grows to six lines, and send (filled
 * once there is something to send). Above it, why the last thing was not sent, with its technical
 * line, and a bar while a file is being prepared. It sits over the conversation, frosted by the
 * caller, which keeps it above the keyboard and the navigation bar.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun Composer(
    draft: String,
    problem: SendProblem?,
    preparing: Boolean,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onDismissProblem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        AnimatedVisibility(visible = preparing) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    stringResource(R.string.conversation_preparing),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
        problem?.let { ProblemLine(it, onDismissProblem) }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilledTonalIconButton(
                onClick = onAttach,
                enabled = !preparing,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.conversation_attach),
                )
            }
            TextField(
                value = draft,
                onValueChange = onDraft,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.conversation_placeholder)) },
                maxLines = 6,
                shape = MaterialTheme.shapes.extraLarge,
                keyboardOptions =
                    KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
            )
            FilledIconButton(
                onClick = onSend,
                enabled = draft.isNotBlank(),
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = stringResource(R.string.conversation_send),
                )
            }
        }
    }
}

@Composable
private fun ProblemLine(problem: SendProblem, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                problem.sentence.text(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            problem.technical?.let { line ->
                Text(
                    stringResource(R.string.conversation_sip, line),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = SonaTheme.typography.monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.conversation_dismiss),
            )
        }
    }
}

/** A short note, once, at the start of a conversation with a phone not known to be Sona. */
@Composable
internal fun PlainPeerNote(peer: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Rounded.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            stringResource(R.string.conversation_plain_peer, peer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** What can be attached. */
internal enum class AttachChoice {
    Media,
    Camera,
    File,
}

/** The attach sheet: photos and videos, the camera, any file. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AttachSheet(onChoose: (AttachChoice) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState =
            rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            val choices =
                listOf(
                    Triple(AttachChoice.Media, Icons.Rounded.PhotoLibrary, R.string.attach_media),
                    Triple(AttachChoice.Camera, Icons.Rounded.PhotoCamera, R.string.attach_camera),
                    Triple(AttachChoice.File, Icons.Rounded.AttachFile, R.string.attach_file),
                )
            choices.forEachIndexed { index, (choice, icon, label) ->
                SegmentedListItem(
                    onClick = { onChoose(choice) },
                    shapes = ListItemDefaults.segmentedShapes(index, choices.size),
                    colors = segmentedListColors(),
                    leadingContent = { Icon(icon, contentDescription = null) },
                ) {
                    ListHeadline(stringResource(label))
                }
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}
