/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.diagnostics.trace

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.sona.core.designsystem.components.segmentedItems
import com.bobbyesp.sona.core.designsystem.components.segmentedListColors
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme
import com.bobbyesp.sona.feature.diagnostics.R
import com.bobbyesp.sona.sip.api.SipTraceEntry
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun TraceRoute(onBack: () -> Unit, viewModel: TraceViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TraceScreen(state = state, onIntent = viewModel::onSendIntent, onBack = onBack)
}

/**
 * The SIP trace: a switch that starts keeping the messages, then each one as a row (which way, its
 * first line, when and with whom), the newest on top. A row opens the whole message, to read, copy
 * or share; the top bar shares or clears them all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TraceScreen(
    state: TraceState,
    onIntent: (TraceIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboard.current
    val copied = stringResource(R.string.diagnostics_copied)
    val shareTitle = stringResource(R.string.trace_share_title)
    val time = rememberTimeFormat()
    var opened by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trace_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.diagnostics_back),
                        )
                    }
                },
                actions = {
                    if (state.messages.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                val text = state.messages.asText(time)
                                scope.launch { context.shareTraceFile(text, shareTitle) }
                            },
                        ) {
                            Icon(
                                Icons.Rounded.Share,
                                contentDescription = stringResource(R.string.trace_share_all),
                            )
                        }
                        IconButton(onClick = { onIntent(TraceIntent.Clear) }) {
                            Icon(
                                Icons.Rounded.DeleteSweep,
                                contentDescription = stringResource(R.string.trace_clear),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.fillMaxHeight().widthIn(max = 840.dp),
                contentPadding =
                    PaddingValues(
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 24.dp,
                    ),
            ) {
                item(key = "capture", contentType = "capture") {
                    if (state.supported) CaptureSwitch(state.capturing, onIntent)
                    Text(
                        stringResource(
                            when {
                                !state.supported -> R.string.trace_unsupported
                                state.messages.isNotEmpty() -> R.string.trace_privacy
                                state.capturing -> R.string.trace_waiting
                                else -> R.string.trace_off
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                    )
                }
                segmentedItems(state.messages, key = { it.id }, contentType = { "message" }) {
                    message,
                    shapes,
                    rowModifier ->
                    MessageRow(message, time, shapes, rowModifier) { opened = message.id }
                }
            }
        }
    }

    state.messages
        .firstOrNull { it.id == opened }
        ?.let { message ->
            MessageDialog(
                message = message,
                time = time,
                onCopy = {
                    scope.launch {
                        clipboard.setClipEntry(
                            ClipEntry(ClipData.newPlainText("sip", message.asText(time))),
                        )
                        snackbar.showSnackbar(copied)
                    }
                },
                onShare = { context.shareText(message.asText(time), shareTitle) },
                onDismiss = { opened = null },
            )
        }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CaptureSwitch(capturing: Boolean, onIntent: (TraceIntent) -> Unit) {
    SegmentedListItem(
        checked = capturing,
        onCheckedChange = { onIntent(TraceIntent.Capture(it)) },
        shapes = segmentedShapes(),
        modifier = Modifier.padding(horizontal = 16.dp),
        colors = segmentedListColors(),
        supportingContent = { Text(stringResource(R.string.trace_capture_summary)) },
        trailingContent = { Switch(checked = capturing, onCheckedChange = null) },
        content = { Text(stringResource(R.string.trace_capture)) },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun segmentedShapes(): ListItemShapes =
    androidx.compose.material3.ListItemDefaults.segmentedShapes(index = 0, count = 1)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MessageRow(
    message: SipTraceEntry,
    time: (Long) -> String,
    shapes: ListItemShapes,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val direction =
        stringResource(if (message.outgoing) R.string.trace_sent else R.string.trace_received)
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier,
        colors = segmentedListColors(),
        leadingContent = {
            Icon(
                if (message.outgoing) Icons.AutoMirrored.Rounded.CallMade
                else Icons.AutoMirrored.Rounded.CallReceived,
                contentDescription = direction,
                tint =
                    if (message.outgoing) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.tertiary,
            )
        },
        supportingContent = {
            Text(
                stringResource(
                    R.string.trace_row_detail,
                    time(message.atMillis),
                    message.peer,
                    message.transport,
                ),
            )
        },
        content = {
            Text(
                message.startLine,
                fontFamily = SonaTheme.typography.monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

/** One message, whole: long lines scroll sideways rather than wrap, as SIP is read. */
@Composable
private fun MessageDialog(
    message: SipTraceEntry,
    time: (Long) -> String,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (message.outgoing) R.string.trace_sent_to else R.string.trace_received_from,
                    message.peer,
                    time(message.atMillis),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            SelectionContainer {
                Text(
                    message.raw,
                    fontFamily = SonaTheme.typography.monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier =
                        Modifier.verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCopy) { Text(stringResource(R.string.trace_copy)) }
        },
        dismissButton = {
            TextButton(onClick = onShare) { Text(stringResource(R.string.trace_share)) }
        },
    )
}

/** A moment as the trace writes it, in the phone's time zone: `12:01:02.123`. */
@Composable
private fun rememberTimeFormat(): (Long) -> String = remember {
    val format = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
    val write: (Long) -> String = { millis -> format.format(Instant.ofEpochMilli(millis)) }
    write
}

private fun Context.shareText(text: String, title: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    startActivity(Intent.createChooser(send, title))
}

/**
 * Shares the whole trace as a file: hundreds of messages are too much for an intent to carry as
 * text. The file is in the cache, replaced at each share, and read by the receiving app through a
 * grant on its address alone.
 */
private suspend fun Context.shareTraceFile(text: String, title: String) {
    val uri =
        withContext(Dispatchers.IO) {
            val folder = File(cacheDir, "diagnostics").apply { mkdirs() }
            val file = File(folder, "sona-sip-trace.txt").apply { writeText(text) }
            FileProvider.getUriForFile(
                this@shareTraceFile,
                "$packageName.diagnostics.files",
                file,
            )
        }
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(title, uri)
    startActivity(Intent.createChooser(send, title))
}
