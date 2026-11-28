/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.messages.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.sona.accounts.ui.accent
import com.bobbyesp.sona.core.designsystem.components.ExpressiveSearch
import com.bobbyesp.sona.core.designsystem.components.ListHeadline
import com.bobbyesp.sona.core.designsystem.components.ScreenPlaceholderCard
import com.bobbyesp.sona.core.designsystem.components.SwipeToDeleteRow
import com.bobbyesp.sona.core.designsystem.components.segmentedListColors
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme
import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.core.ui.TopLevelEmptyState
import com.bobbyesp.sona.core.ui.TopLevelScaffold
import com.bobbyesp.sona.directory.ui.Avatar
import com.bobbyesp.sona.directory.ui.formatWhen
import com.bobbyesp.sona.feature.messages.R
import com.bobbyesp.sona.messaging.domain.Conversation
import com.bobbyesp.sona.messaging.domain.ConversationId
import com.bobbyesp.sona.messaging.domain.ConversationSummary
import com.bobbyesp.sona.messaging.domain.Message
import com.bobbyesp.sona.messaging.domain.MessageDirection
import com.bobbyesp.sona.messaging.domain.MessageStatus
import com.bobbyesp.sona.messaging.ui.FileKind
import com.bobbyesp.sona.messaging.ui.MessageStatusMark
import com.bobbyesp.sona.messaging.ui.icon
import com.bobbyesp.sona.messaging.ui.previewText
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun ConversationsRoute(
    onOpen: (ConversationId) -> Unit,
    onNew: () -> Unit,
    viewModel: ConversationsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ConversationsScreen(
        state = state,
        onIntent = viewModel::onSendIntent,
        onOpen = onOpen,
        onNew = onNew,
    )
}

/** The conversations, the most recent first. A swipe deletes one, after asking. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ConversationsScreen(
    state: ConversationsState,
    onIntent: (ConversationsIntent) -> Unit,
    onOpen: (ConversationId) -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    TopLevelScaffold(
        title = stringResource(R.string.messages_title),
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                expanded = !listState.canScrollBackward,
                icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                text = { Text(stringResource(R.string.messages_new)) },
            )
        },
    ) { padding, contentModifier ->
        when {
            !state.loaded ->
                Box(contentModifier.padding(padding), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            state.total == 0 ->
                TopLevelEmptyState(
                    title = stringResource(R.string.messages_empty_title),
                    description = stringResource(R.string.messages_empty_description),
                    icon = Icons.AutoMirrored.Rounded.Chat,
                    padding = padding,
                    contentModifier = contentModifier,
                )
            else ->
                Box(contentModifier, contentAlignment = Alignment.TopCenter) {
                    ConversationList(state, listState, padding, onIntent, onOpen)
                }
        }
    }

    state.confirmDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { onIntent(ConversationsIntent.DismissDelete) },
            title = { Text(stringResource(R.string.messages_delete_title, row.title)) },
            text = { Text(stringResource(R.string.messages_delete_text)) },
            confirmButton = {
                TextButton(onClick = { onIntent(ConversationsIntent.ConfirmDelete) }) {
                    Text(
                        stringResource(R.string.messages_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(ConversationsIntent.DismissDelete) }) {
                    Text(stringResource(R.string.messages_cancel))
                }
            },
        )
    }
}

@Composable
private fun ConversationList(
    state: ConversationsState,
    listState: LazyListState,
    padding: PaddingValues,
    onIntent: (ConversationsIntent) -> Unit,
    onOpen: (ConversationId) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxHeight().widthIn(max = 840.dp),
        contentPadding =
            PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
    ) {
        item(key = "search", contentType = "search") {
            ExpressiveSearch(
                placeholder = stringResource(R.string.messages_search),
                backDescription = stringResource(R.string.messages_search_close),
                clearDescription = stringResource(R.string.messages_search_clear),
                onQueryChange = { onIntent(ConversationsIntent.Query(it)) },
            ) {
                LazyColumn(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    if (state.rows.isEmpty()) item { NoResults() }
                    conversationRows(state.rows, onIntent, onOpen, swipe = false)
                }
            }
        }
        if (state.rows.isEmpty()) item(key = "none", contentType = "none") { NoResults() }
        conversationRows(state.rows, onIntent, onOpen, swipe = true)
    }
}

@Composable
private fun NoResults() {
    Text(
        stringResource(R.string.messages_no_results),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp),
    )
}

private fun LazyListScope.conversationRows(
    rows: List<ConversationRow>,
    onIntent: (ConversationsIntent) -> Unit,
    onOpen: (ConversationId) -> Unit,
    swipe: Boolean,
) {
    val count = rows.size
    items(count = count, key = { "conversation-${rows[it].id.value}" }, contentType = { "row" }) {
        index ->
        val row = rows[index]
        val shapes = ListItemDefaults.segmentedShapes(index = index, count = count)
        val itemModifier =
            Modifier.animateItem()
                .padding(horizontal = 16.dp, vertical = ListItemDefaults.SegmentedGap / 2)
        if (swipe) {
            SwipeToDeleteRow(
                onDelete = { onIntent(ConversationsIntent.AskDelete(row)) },
                deleteLabel = stringResource(R.string.messages_delete),
                shape = shapes.shape,
                modifier = itemModifier,
            ) {
                ConversationItem(row, shapes, onClick = { onOpen(row.id) })
            }
        } else {
            ConversationItem(row, shapes, onClick = { onOpen(row.id) }, modifier = itemModifier)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConversationItem(
    row: ConversationRow,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val last = row.summary.last
    val unread = row.summary.unread
    val unreadText =
        if (unread > 0) pluralStringResource(R.plurals.messages_unread, unread, unread) else null
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier,
        colors = segmentedListColors(),
        leadingContent = { Avatar(row.title) },
        supportingContent =
            last?.let { message -> { LastMessageLine(message, emphasized = unread > 0) } },
        trailingContent = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    formatWhen(row.summary.conversation.lastActivityAt),
                    style = MaterialTheme.typography.labelMedium,
                    color =
                        if (unread > 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (unreadText != null) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.semantics { contentDescription = unreadText },
                    ) {
                        Text("$unread")
                    }
                }
            }
        },
        content = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                row.account?.let { account ->
                    Box(
                        Modifier.size(8.dp).clip(CircleShape).background(account.accent).semantics {
                            contentDescription = account.label
                        },
                    )
                    Spacer(Modifier.size(8.dp))
                }
                ListHeadline(
                    row.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        },
    )
}

@Composable
private fun LastMessageLine(message: Message, emphasized: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (message.outgoing) {
            MessageStatusMark(message.status)
            Spacer(Modifier.size(4.dp))
        }
        message.attachment?.let { file ->
            Icon(
                FileKind.of(file.mime).icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(4.dp))
        }
        Text(
            message.previewText(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (emphasized) FontWeight.SemiBold else null,
            color =
                if (emphasized) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun NoConversationOpen() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        ScreenPlaceholderCard(
            title = stringResource(R.string.messages_pick_title),
            description = stringResource(R.string.messages_pick_description),
            icon = Icons.AutoMirrored.Rounded.Chat,
            modifier = Modifier.widthIn(max = 480.dp),
        )
    }
}

@PreviewLightDark
@Composable
private fun ConversationsScreenPreview() {
    val ana =
        ConversationSummary(
            conversation =
                Conversation(
                    id = ConversationId(1),
                    account = AccountId("lab"),
                    peer = SipAddress("102", "lab.local"),
                    lastActivityAt = System.currentTimeMillis(),
                ),
            name = "Ana",
            last =
                Message(
                    conversation = ConversationId(1),
                    direction = MessageDirection.Outgoing,
                    text = "On my way",
                    at = System.currentTimeMillis(),
                    status = MessageStatus.Read,
                ),
            unread = 2,
        )
    SonaTheme {
        ConversationsScreen(
            state =
                ConversationsState(
                    loaded = true,
                    rows = listOf(ana.toRow(null, several = false)),
                    total = 1,
                ),
            onIntent = {},
            onOpen = {},
            onNew = {},
        )
    }
}
