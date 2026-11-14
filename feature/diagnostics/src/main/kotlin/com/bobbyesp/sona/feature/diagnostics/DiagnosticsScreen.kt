/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.diagnostics

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.sona.accounts.domain.AccountStatus
import com.bobbyesp.sona.accounts.ui.AccountMark
import com.bobbyesp.sona.accounts.ui.explain
import com.bobbyesp.sona.accounts.ui.stateText
import com.bobbyesp.sona.core.designsystem.components.segmentedListColors
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme
import com.bobbyesp.sona.sip.api.EngineState
import com.bobbyesp.sona.sip.api.RegistrationState
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun DiagnosticsRoute(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DiagnosticsScreen(state = state, onBack = onBack)
}

/**
 * Diagnostics, for whoever sets up the Asterisk side: the engine, and each account as a card with
 * its registration as a colored status, then where it registers and how, as rows in the face SIP is
 * read in. Any value is one touch from the clipboard, to paste into an `asterisk -rx` session or a
 * message to the administrator.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DiagnosticsScreen(
    state: DiagnosticsState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.diagnostics_copied)
    val copy: (String) -> Unit = { text ->
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("sip", text)))
            snackbar.showSnackbar(copied)
        }
    }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.diagnostics_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            return@Scaffold
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.fillMaxHeight().widthIn(max = 840.dp),
                contentPadding =
                    PaddingValues(
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 24.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "engine", contentType = "engine") { EngineCard(state) }
                if (state.accounts.isEmpty()) {
                    item(key = "none", contentType = "none") {
                        Text(
                            stringResource(R.string.diagnostics_no_accounts),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                }
                items(state.accounts, key = { it.account.id.value }, contentType = { "account" }) {
                    status ->
                    AccountCard(status, copy)
                }
            }
        }
    }
}

@Composable
private fun engineText(state: EngineState): String =
    when (state) {
        EngineState.Running -> stringResource(R.string.diagnostics_engine_running)
        EngineState.Starting -> stringResource(R.string.diagnostics_engine_starting)
        EngineState.Stopped -> stringResource(R.string.diagnostics_engine_stopped)
        is EngineState.Failed ->
            "${stringResource(R.string.diagnostics_engine_failed)}: ${state.detail}"
    }

/** The engine: its state, with a loading indicator while it starts, and what it can do. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EngineCard(state: DiagnosticsState) {
    val failed = state.engine is EngineState.Failed
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color =
            if (failed) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer,
        contentColor =
            if (failed) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.diagnostics_engine),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (state.engine == EngineState.Starting) {
                    LoadingIndicator(Modifier.size(40.dp))
                    Spacer(Modifier.size(8.dp))
                }
                Text(engineText(state.engine), style = MaterialTheme.typography.titleMedium)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.capabilities.forEach { capability ->
                    AssistChip(onClick = {}, enabled = false, label = { Text(capability.name) })
                }
            }
        }
    }
}

/** An account: who and how it stands, and its registration as rows to read and to copy. */
@Composable
private fun AccountCard(status: AccountStatus, copy: (String) -> Unit) {
    val account = status.account
    val failed = status.registration as? RegistrationState.Failed
    val problem = failed?.explain(account)
    val ok = status.registration == RegistrationState.Registered
    val rows = buildList {
        add(stringResource(R.string.diagnostics_identity) to account.identity.uri)
        add(stringResource(R.string.diagnostics_server) to account.server.toString())
        account.outboundProxy?.let {
            add(stringResource(R.string.diagnostics_proxy) to it.toString())
        }
        add(
            stringResource(R.string.diagnostics_expiry) to
                stringResource(
                    R.string.diagnostics_expiry_value,
                    account.expiry.inWholeSeconds.toInt(),
                ),
        )
        problem?.technical?.let {
            add(stringResource(R.string.diagnostics_registration_sip) to it)
        }
    }
    Column(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color =
                when {
                    failed != null -> MaterialTheme.colorScheme.errorContainer
                    ok -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
            contentColor =
                when {
                    failed != null -> MaterialTheme.colorScheme.onErrorContainer
                    ok -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AccountMark(account)
                Column(Modifier.weight(1f)) {
                    Text(
                        account.label,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(status.stateText(), style = MaterialTheme.typography.bodyMedium)
                    problem?.let {
                        Text(it.sentence, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (failed != null) Icon(Icons.Rounded.ErrorOutline, contentDescription = null)
            }
        }
        rows.forEachIndexed { index, (label, value) ->
            SegmentedListItem(
                onClick = { copy(value) },
                shapes = ListItemDefaults.segmentedShapes(index, rows.size),
                colors = segmentedListColors(),
                overlineContent = { Text(label) },
                trailingContent = {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.diagnostics_copy, label),
                    )
                },
                content = { Text(value, fontFamily = SonaTheme.typography.monospace) },
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DiagnosticsPreview() {
    SonaTheme {
        DiagnosticsScreen(
            state = DiagnosticsState(loaded = true, engine = EngineState.Running),
            onBack = {},
        )
    }
}
