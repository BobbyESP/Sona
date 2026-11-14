/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.history

import android.app.KeyguardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.sona.calling.ui.problemText
import com.bobbyesp.sona.core.designsystem.components.ConnectedChoice
import com.bobbyesp.sona.core.designsystem.components.SwipeToDeleteRow
import com.bobbyesp.sona.core.designsystem.components.segmentedListColors
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme
import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.core.model.settings.RecentTapAction
import com.bobbyesp.sona.core.ui.TopLevelEmptyState
import com.bobbyesp.sona.core.ui.TopLevelScaffold
import com.bobbyesp.sona.core.ui.contribution.LocalCallScreen
import com.bobbyesp.sona.core.ui.events.CollectEventsEffect
import com.bobbyesp.sona.core.ui.rememberCallStarter
import com.bobbyesp.sona.directory.domain.CallOutcome
import com.bobbyesp.sona.directory.domain.CallRecord
import com.bobbyesp.sona.directory.domain.RecentGroup
import com.bobbyesp.sona.directory.ui.Avatar
import com.bobbyesp.sona.directory.ui.formatDayHeading
import com.bobbyesp.sona.directory.ui.formatDuration
import com.bobbyesp.sona.directory.ui.formatWhen
import com.bobbyesp.sona.directory.ui.icon
import com.bobbyesp.sona.directory.ui.isProblem
import com.bobbyesp.sona.directory.ui.localDay
import com.bobbyesp.sona.directory.ui.outcomeText
import com.bobbyesp.sona.sip.api.CallDirection
import java.time.LocalDate
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun RecentsRoute(
    onAddContact: (CallRecord) -> Unit,
    viewModel: RecentsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openCall = LocalCallScreen.current
    val callStarter = rememberCallStarter()
    viewModel.effects.CollectEventsEffect { effect ->
        when (effect) {
            RecentsEffect.OpenCall -> openCall()
        }
    }

    val context = LocalContext.current
    LifecycleResumeEffect(viewModel) {
        val locked = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        if (!locked) viewModel.onSendIntent(RecentsIntent.Seen)
        onPauseOrDispose {}
    }
    RecentsScreen(
        state = state,
        onIntent = { intent ->
            val calls =
                intent is RecentsIntent.CallBack ||
                    (intent is RecentsIntent.Tap && state.tapAction == RecentTapAction.CallBack)
            if (calls) callStarter.start { viewModel.onSendIntent(intent) }
            else viewModel.onSendIntent(intent)
        },
        onAddContact = onAddContact,
    )
}

/** What a day of calls is: its heading, and its entries as one segmented group. */
private data class Day(val day: LocalDate, val at: Long, val entries: List<RecentGroup>)

private fun List<RecentGroup>.byDay(): List<Day> = groupBy {
    localDay(it.latest.startedAt)
}
    .map { (day, groups) -> Day(day, groups.first().latest.startedAt, groups) }

/**
 * Recents: the calls made and received, newest first. Each day is a group of segmented list items
 * under a heading that stays at the top while its calls scroll; the filter is a connected button
 * group; a swipe deletes, with a message to undo it; the loading state is Material's loading
 * indicator rather than a blank screen.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RecentsScreen(
    state: RecentsState,
    onIntent: (RecentsIntent) -> Unit,
    onAddContact: (CallRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val deletedMessage =
        pluralStringResource(R.plurals.history_deleted, state.deleted.size, state.deleted.size)
    val undoLabel = stringResource(R.string.history_undo)
    LaunchedEffect(state.deleted) {
        if (state.deleted.isEmpty()) return@LaunchedEffect
        val result =
            snackbar.showSnackbar(
                message = deletedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
        onIntent(
            if (result == SnackbarResult.ActionPerformed) RecentsIntent.Undo
            else RecentsIntent.UndoExpired,
        )
    }

    TopLevelScaffold(
        title = stringResource(R.string.history_title),
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding, contentModifier ->
        when {
            !state.loaded ->
                Box(contentModifier.padding(padding), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            state.total == 0 ->
                TopLevelEmptyState(
                    title = stringResource(R.string.history_empty_title),
                    description = stringResource(R.string.history_empty_description),
                    icon = Icons.Rounded.History,
                    padding = padding,
                    contentModifier = contentModifier,
                )
            else -> {
                val days = remember(state.groups) { state.groups.byDay() }
                val problem = state.problem?.problemText()
                Box(contentModifier, contentAlignment = Alignment.TopCenter) {
                    LazyColumn(
                        modifier = Modifier.fillMaxHeight().widthIn(max = 840.dp),
                        contentPadding =
                            PaddingValues(
                                top = padding.calculateTopPadding(),
                                bottom = padding.calculateBottomPadding() + 24.dp,
                            ),
                    ) {
                        item(key = "filters", contentType = "filters") {
                            ConnectedChoice(
                                options = RecentsFilter.entries,
                                selected = state.filter,
                                onSelect = { onIntent(RecentsIntent.Filter(it)) },
                                label = {
                                    stringResource(
                                        if (it == RecentsFilter.All) R.string.history_filter_all
                                        else R.string.history_filter_missed,
                                    )
                                },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        if (problem != null) {
                            item(key = "problem", contentType = "problem") {
                                Text(
                                    problem,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier =
                                        Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                )
                            }
                        }
                        if (state.groups.isEmpty()) {
                            item(key = "no-missed", contentType = "none") {
                                Column(Modifier.fillMaxWidth().padding(32.dp)) {
                                    Text(
                                        stringResource(R.string.history_empty_missed_title),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        stringResource(R.string.history_empty_missed_description),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        days.forEach { day -> dayGroup(day, onIntent) }
                    }
                }
            }
        }
    }

    state.details?.let { group ->
        DetailsSheet(
            group = group,
            onDismiss = { onIntent(RecentsIntent.CloseDetails) },
            onCall = { onIntent(RecentsIntent.CallBack(group.latest)) },
            onAddContact = { onAddContact(group.latest) },
            onDelete = { onIntent(RecentsIntent.Delete(group)) },
        )
    }
    state.confirm?.let { record ->
        AlertDialog(
            onDismissRequest = { onIntent(RecentsIntent.DismissConfirm) },
            title = { Text(stringResource(R.string.history_call_confirm_title, record.title)) },
            text = { Text(record.address.uri, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(
                    onClick = { onIntent(RecentsIntent.CallBack(record, confirmed = true)) },
                ) {
                    Text(stringResource(R.string.history_call_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(RecentsIntent.DismissConfirm) }) {
                    Text(stringResource(R.string.history_cancel))
                }
            },
        )
    }
}

/** A day: its heading, which sticks, and its calls as one segmented group. */
private fun LazyListScope.dayGroup(day: Day, onIntent: (RecentsIntent) -> Unit) {
    stickyHeader(key = "day-${day.day}", contentType = "heading") { DayHeading(day.at) }
    val count = day.entries.size
    items(
        count = count,
        key = { "call-${day.entries[it].latest.id}" },
        contentType = { "call" },
    ) { index ->
        val group = day.entries[index]
        val shapes = ListItemDefaults.segmentedShapes(index = index, count = count)
        val deleteLabel = stringResource(R.string.history_delete_one)
        SwipeToDeleteRow(
            onDelete = { onIntent(RecentsIntent.Delete(group)) },
            deleteLabel = deleteLabel,
            shape = shapes.shape,
            modifier =
                Modifier.animateItem()
                    .padding(horizontal = 16.dp, vertical = ListItemDefaults.SegmentedGap / 2),
        ) {
            RecentRow(
                group = group,
                shapes = shapes,
                onTap = { onIntent(RecentsIntent.Tap(group)) },
                onCall = { onIntent(RecentsIntent.CallBack(group.latest)) },
            )
        }
    }
}

@Composable
private fun DayHeading(at: Long) {
    Text(
        formatDayHeading(at),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 32.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                .semantics { heading() },
    )
}

@Composable
private fun RecentRow(
    group: RecentGroup,
    shapes: ListItemShapes,
    onTap: () -> Unit,
    onCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val record = group.latest
    val title =
        if (group.count > 1) stringResource(R.string.history_title_count, record.title, group.count)
        else record.title
    val problem = record.isProblem
    val tint =
        if (problem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    SegmentedListItem(
        onClick = onTap,
        shapes = shapes,
        modifier = modifier,
        colors = segmentedListColors(),
        leadingContent = { Avatar(record.title) },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    record.icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = tint,
                )
                Spacer(Modifier.size(4.dp))
                Text(record.summary(), maxLines = 1, overflow = TextOverflow.Ellipsis, color = tint)
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatWhen(record.startedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                FilledTonalIconButton(onClick = onCall, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        Icons.Rounded.Call,
                        contentDescription =
                            stringResource(R.string.history_call_name, record.title),
                    )
                }
            }
        },
        content = {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (problem) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        },
    )
}

/** What happened, in a line: *Outgoing · 2:05*, *Missed*, *Busy*. */
@Composable
private fun CallRecord.summary(): String {
    val text = outcomeText()
    return if (this.outcome == CallOutcome.Answered && durationMillis > 0)
        "$text · ${formatDuration(durationMillis)}"
    else text
}

/**
 * The details of an entry: who, one split button whose main part calls and whose menu holds the
 * rest (add to contacts, delete), and every call of the group with its SIP status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsSheet(
    group: RecentGroup,
    onDismiss: () -> Unit,
    onCall: () -> Unit,
    onAddContact: () -> Unit,
    onDelete: () -> Unit,
) {
    val record = group.latest
    var menu by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState =
            rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                // No half-open state: it opens at its content's height and back closes it.
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(record.title, size = 72.dp)
                Spacer(Modifier.size(16.dp))
                Column {
                    Text(record.title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        record.address.uri,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = SonaTheme.typography.monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box {
                SplitButtonLayout(
                    leadingButton = {
                        SplitButtonDefaults.LeadingButton(onClick = onCall) {
                            Icon(
                                Icons.Rounded.Call,
                                contentDescription = null,
                                modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize),
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.history_call))
                        }
                    },
                    trailingButton = {
                        val more = stringResource(R.string.history_more)
                        SplitButtonDefaults.TrailingButton(
                            checked = menu,
                            onCheckedChange = { menu = it },
                            modifier = Modifier.semantics { stateDescription = more },
                        ) {
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                contentDescription = more,
                                modifier = Modifier.size(SplitButtonDefaults.TrailingIconSize),
                            )
                        }
                    },
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (record.name == null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_add_contact)) },
                            leadingIcon = {
                                Icon(Icons.Rounded.PersonAdd, contentDescription = null)
                            },
                            onClick = {
                                menu = false
                                onAddContact()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history_delete)) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menu = false
                            onDelete()
                        },
                    )
                }
            }
            Text(
                stringResource(R.string.history_details_calls),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                group.records.forEachIndexed { index, call ->
                    SegmentedListItem(
                        onClick = {},
                        shapes = ListItemDefaults.segmentedShapes(index, group.records.size),
                        colors = segmentedListColors(),
                        leadingContent = { Icon(call.icon, contentDescription = null) },
                        trailingContent = {
                            Text(
                                formatWhen(call.startedAt),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        supportingContent =
                            call.statusLine?.let { line ->
                                {
                                    Text(
                                        "${stringResource(R.string.history_details_sip)}: $line",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = SonaTheme.typography.monospace,
                                    )
                                }
                            },
                        content = { Text(call.summary()) },
                    )
                }
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}

@PreviewLightDark
@Composable
private fun RecentsScreenPreview() {
    val ana =
        CallRecord(
            account = null,
            direction = CallDirection.Incoming,
            outcome = CallOutcome.Missed,
            address = SipAddress("102", "lab.local"),
            name = "Ana",
            startedAt = System.currentTimeMillis() - 60_000,
            durationMillis = 0,
        )
    val busy =
        ana.copy(
            id = 2,
            direction = CallDirection.Outgoing,
            outcome = CallOutcome.Answered,
            durationMillis = 125_000,
            startedAt = ana.startedAt - 3_600_000,
        )
    SonaTheme {
        RecentsScreen(
            state =
                RecentsState(
                    loaded = true,
                    total = 2,
                    groups = listOf(RecentGroup(listOf(ana)), RecentGroup(listOf(busy))),
                ),
            onIntent = {},
            onAddContact = {},
        )
    }
}
