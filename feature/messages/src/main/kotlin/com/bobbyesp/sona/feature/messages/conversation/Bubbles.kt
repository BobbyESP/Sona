/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.messages.conversation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme
import com.bobbyesp.sona.directory.ui.formatDayHeading
import com.bobbyesp.sona.feature.messages.R
import com.bobbyesp.sona.messaging.domain.Attachment
import com.bobbyesp.sona.messaging.domain.Message
import com.bobbyesp.sona.messaging.domain.MessageId
import com.bobbyesp.sona.messaging.domain.TransferState
import com.bobbyesp.sona.messaging.ui.FailureSubject
import com.bobbyesp.sona.messaging.ui.FileKind
import com.bobbyesp.sona.messaging.ui.MessageStatusMark
import com.bobbyesp.sona.messaging.ui.explain
import com.bobbyesp.sona.messaging.ui.fileSize
import com.bobbyesp.sona.messaging.ui.icon
import com.bobbyesp.sona.messaging.ui.isTransferring
import com.bobbyesp.sona.messaging.ui.label
import com.bobbyesp.sona.messaging.ui.text
import com.bobbyesp.sona.messaging.ui.transferLine
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What a bubble can ask for; the screen carries it out. */
internal interface BubbleActions {
    fun intent(intent: ConversationIntent)

    fun openPicture(message: MessageId)

    fun openFile(uri: String, mime: String)

    fun copy(text: String)
}

/** A day's heading: centered, quiet, read as a heading. */
@Composable
internal fun DayHeading(at: Long, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Text(
                formatDayHeading(at),
                style = MaterialTheme.typography.labelLarge,
                modifier =
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp).semantics { heading() },
            )
        }
    }
}

/**
 * A message: a bubble on the side of who wrote it, its corners towards the rest of its run
 * tightened, so a run reads as one block (Material 3 Expressive's shapes, from the theme). Under
 * the last of a run, its time and, for one sent from here, its status mark; under one that failed,
 * why, the SIP line, and *Retry*. A long press opens what can be done with it.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun MessageBubble(
    item: ThreadItem.Bubble,
    peer: String,
    subject: FailureSubject,
    actions: BubbleActions,
    modifier: Modifier = Modifier,
) {
    val message = item.message
    val outgoing = message.outgoing
    val shape = bubbleShape(outgoing, item.position)
    val colors = MaterialTheme.colorScheme
    val container = if (outgoing) colors.primaryContainer else colors.surfaceContainerHigh
    val content = if (outgoing) colors.onPrimaryContainer else colors.onSurface
    var menu by rememberSaveable { mutableStateOf(false) }
    val top =
        if (item.position == BubblePosition.Single || item.position == BubblePosition.First) 6.dp
        else 1.dp

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = top),
        horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
    ) {
        Box(
            Modifier.fillMaxWidth(BubbleWidth),
            contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box {
                Surface(
                    shape = shape,
                    color = container,
                    contentColor = content,
                    modifier =
                        Modifier.clip(shape)
                            .combinedClickable(
                                onClick = { tapped(message, item.uri, actions) },
                                onLongClick = { menu = true },
                                onLongClickLabel = stringResource(R.string.conversation_more),
                            ),
                ) {
                    Column {
                        message.attachment?.let { file ->
                            if (file.isPicture) PictureContent(message, file, item.uri, shape)
                            else FileContent(message, file, peer)
                        }
                        if (message.attachment?.isPicture == true) {
                            message.transferLine(peer)?.let { line ->
                                Text(
                                    line.text(),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier =
                                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                        if (message.text.isNotBlank()) {
                            Text(
                                message.text,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            )
                        }
                        TransferButtons(message, actions)
                    }
                }
                MessageMenu(
                    message = message,
                    expanded = menu,
                    onDismiss = { menu = false },
                    actions = actions,
                )
            }
        }
        if (message.hasFailed) {
            val failure = message.failure!!.explain(subject)
            FlowRow(
                modifier = Modifier.fillMaxWidth(BubbleWidth).padding(top = 4.dp),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp, if (outgoing) Alignment.End else Alignment.Start),
                verticalArrangement = Arrangement.Center,
            ) {
                Column(Modifier.widthIn(max = 360.dp)) {
                    Text(
                        failure.sentence,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error,
                    )
                    failure.technical?.let { line ->
                        Text(
                            stringResource(R.string.conversation_sip, line),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = SonaTheme.typography.monospace,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                if (message.canRetry) {
                    TextButton(onClick = { actions.intent(ConversationIntent.Retry(message.id)) }) {
                        Text(stringResource(R.string.conversation_retry))
                    }
                }
            }
        }
        if (item.last) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
            ) {
                Text(
                    formatTime(message.at),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
                if (outgoing) {
                    Spacer(Modifier.width(4.dp))
                    MessageStatusMark(message.status, size = 14.dp)
                }
            }
        }
    }
}

/** What a tap does: a picture opens whole, a file opens in another app, an offer downloads. */
private fun tapped(message: Message, uri: String?, actions: BubbleActions) {
    val file = message.attachment ?: return
    when {
        uri != null && file.isPicture -> actions.openPicture(message.id)
        uri != null -> actions.openFile(uri, file.mime)
        !message.outgoing && file.state == TransferState.Offered ->
            actions.intent(ConversationIntent.Download(message.id))
    }
}

/** Of the room beside the other side's, a bubble takes at most this much. */
private const val BubbleWidth = 0.82f

/**
 * The bubble's shape: the theme's extra-large corners, with those towards the run's other bubbles
 * (on the writer's side) the theme's extra-small.
 */
@Composable
private fun bubbleShape(outgoing: Boolean, position: BubblePosition): CornerBasedShape {
    val round = MaterialTheme.shapes.extraLarge
    val tight = MaterialTheme.shapes.extraSmall.topStart
    val towardsAbove = position == BubblePosition.Middle || position == BubblePosition.Last
    val towardsBelow = position == BubblePosition.Middle || position == BubblePosition.First
    return if (outgoing) {
        round.copy(
            topEnd = if (towardsAbove) tight else round.topEnd,
            bottomEnd = if (towardsBelow) tight else round.bottomEnd,
        )
    } else {
        round.copy(
            topStart = if (towardsAbove) tight else round.topStart,
            bottomStart = if (towardsBelow) tight else round.bottomStart,
        )
    }
}

/**
 * A picture, inline: decoded at the size it is shown (Coil sizes the request to the layout), with
 * its progress ring while it travels. Not here yet (offered, or coming), it is a placeholder with
 * the picture's icon.
 */
@Composable
private fun PictureContent(
    message: Message,
    file: Attachment,
    uri: String?,
    shape: CornerBasedShape,
) {
    val ratio =
        if (file.width != null && file.height != null && file.height!! > 0)
            (file.width!!.toFloat() / file.height!!).coerceIn(0.6f, 1.8f)
        else 4f / 3f
    Box(
        Modifier.widthIn(max = 280.dp).width(260.dp).aspectRatio(ratio).clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        if (uri != null) {
            AsyncImage(
                model =
                    ImageRequest.Builder(LocalContext.current).data(uri).crossfade(true).build(),
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        FileKind.Picture.icon,
                        contentDescription = file.name,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }
        if (message.isTransferring) TransferRing(message, file)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TransferRing(message: Message, file: Attachment) {
    Surface(
        shape = MaterialShapes.Cookie9Sided.toShape(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(56.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            val moving =
                file.state == TransferState.Sending || file.state == TransferState.Receiving
            if (moving) {
                CircularProgressIndicator(
                    progress = { file.progress },
                    modifier = Modifier.size(36.dp),
                )
            } else if (message.attachment?.state != TransferState.WaitingForPeer) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp))
            } else {
                Icon(Icons.Rounded.Download, contentDescription = null)
            }
        }
    }
}

/** Any other file, as a card: its kind's icon, its name, its size and where it is. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FileContent(message: Message, file: Attachment, peer: String) {
    Column(Modifier.widthIn(min = 220.dp, max = 320.dp).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialShapes.Cookie6Sided.toShape(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(FileKind.of(file.mime).icon, contentDescription = null)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f, fill = false)) {
                Text(
                    file.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 3,
                    overflow = TextOverflow.MiddleEllipsis,
                )
                val line = message.transferLine(peer)?.text()
                Text(
                    listOfNotNull(fileSize(file.size), line).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.8f),
                )
            }
        }
        if (message.isTransferring) {
            Spacer(Modifier.size(10.dp))
            val moving =
                file.state == TransferState.Sending || file.state == TransferState.Receiving
            if (moving) {
                LinearWavyProgressIndicator(
                    progress = { file.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** What can be done with a file on its way: download or decline an offer, cancel a transfer. */
@Composable
private fun TransferButtons(message: Message, actions: BubbleActions) {
    val file = message.attachment ?: return
    val offered = !message.outgoing && file.state == TransferState.Offered
    if (!offered && !message.isTransferring) return
    Row(
        Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (offered) {
            FilledTonalButton(
                onClick = { actions.intent(ConversationIntent.Download(message.id)) },
            ) {
                Icon(
                    Icons.Rounded.Download,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.conversation_download, fileSize(file.size)))
            }
            TextButton(onClick = { actions.intent(ConversationIntent.Decline(message.id)) }) {
                Text(stringResource(R.string.conversation_decline))
            }
        } else {
            TextButton(onClick = { actions.intent(ConversationIntent.Cancel(message.id)) }) {
                Text(stringResource(R.string.conversation_cancel_transfer))
            }
        }
    }
}

/** A long press: copy, retry, delete, details. */
@Composable
private fun MessageMenu(
    message: Message,
    expanded: Boolean,
    onDismiss: () -> Unit,
    actions: BubbleActions,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (message.text.isNotBlank()) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.conversation_copy)) },
                leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                onClick = {
                    onDismiss()
                    actions.copy(message.text)
                },
            )
        }
        if (message.canRetry) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.conversation_retry)) },
                leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                onClick = {
                    onDismiss()
                    actions.intent(ConversationIntent.Retry(message.id))
                },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.conversation_details)) },
            leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
            onClick = {
                onDismiss()
                actions.intent(ConversationIntent.Details(message.id))
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.conversation_delete)) },
            leadingIcon = {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            onClick = {
                onDismiss()
                actions.intent(ConversationIntent.Delete(message.id))
            },
        )
    }
}

/**
 * A message's details: where it is, when, its file, and, when it failed, why with the SIP line, the
 * same as under its bubble, for whoever reads it here.
 */
@Composable
internal fun MessageDetailsDialog(
    message: Message,
    subject: FailureSubject,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.conversation_details)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailLine(R.string.details_status, message.status.label.text())
                DetailLine(R.string.details_when, formatFull(message.at))
                message.attachment?.let { file ->
                    DetailLine(R.string.details_file, file.name)
                    DetailLine(R.string.details_size, "${fileSize(file.size)} · ${file.mime}")
                }
                message.failure?.let { failure ->
                    val words = failure.explain(subject)
                    DetailLine(R.string.details_problem, words.sentence)
                    words.technical?.let { DetailLine(R.string.details_sip, it, mono = true) }
                }
                message.envelopeId?.let { DetailLine(R.string.details_id, it, mono = true) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.conversation_close)) }
        },
    )
}

@Composable
private fun DetailLine(label: Int, value: String, mono: Boolean = false) {
    Column {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (mono) SonaTheme.typography.monospace else null,
        )
    }
}

private fun formatTime(at: Long): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))

private fun formatFull(at: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))
