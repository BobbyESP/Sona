/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.messages.start

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.sona.accounts.ui.accent
import com.bobbyesp.sona.core.designsystem.components.ListHeadline
import com.bobbyesp.sona.core.designsystem.components.SectionHeader
import com.bobbyesp.sona.core.designsystem.components.SonaTextField
import com.bobbyesp.sona.core.designsystem.components.segmentedItems
import com.bobbyesp.sona.core.designsystem.components.segmentedListColors
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme
import com.bobbyesp.sona.core.navigation.pane.LocalPaneContext
import com.bobbyesp.sona.directory.domain.Contact
import com.bobbyesp.sona.directory.domain.ContactNumber
import com.bobbyesp.sona.directory.ui.Avatar
import com.bobbyesp.sona.feature.messages.R
import com.bobbyesp.sona.messaging.domain.ConversationId

@Composable
internal fun NewConversationRoute(
    viewModel: NewConversationViewModel,
    onBack: () -> Unit,
    onOpened: (ConversationId) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.opened) { state.opened?.let(onOpened) }
    NewConversationScreen(state = state, onIntent = viewModel::onSendIntent, onBack = onBack)
}

/**
 * *New message*: a field that takes a name, an extension or an address; under it, what was typed as
 * an address to write to, and the contacts that match, each number a row. The account to write from
 * is a row of chips when there are several.
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)
@Composable
internal fun NewConversationScreen(
    state: NewConversationState,
    onIntent: (NewConversationIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pane = LocalPaneContext.current
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.messages_new)) },
                navigationIcon = {
                    if (pane.providesOwnBackAffordance) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.messages_back),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) {
            Box(Modifier.padding(padding).fillMaxWidth(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            return@Scaffold
        }
        Box(Modifier.padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight().widthIn(max = 840.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item(key = "field", contentType = "field") {
                    SonaTextField(
                        value = state.query,
                        onValueChange = { onIntent(NewConversationIntent.Query(it)) },
                        label = stringResource(R.string.new_to),
                        placeholder = stringResource(R.string.new_to_placeholder),
                        leadingIcon = Icons.Rounded.Search,
                        enabled = !state.opening,
                        error = state.problem?.let { stringResource(it.text) },
                        keyboardOptions =
                            KeyboardOptions(
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Send,
                            ),
                        keyboardActions =
                            KeyboardActions(
                                onSend = { onIntent(NewConversationIntent.WriteTyped) },
                            ),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (state.choosesAccount) {
                    item(key = "accounts", contentType = "accounts") {
                        AccountChoice(state, onIntent)
                    }
                }
                if (state.opening) {
                    item(key = "opening", contentType = "opening") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                            LoadingIndicator()
                        }
                    }
                }
                state.typedAddress?.let { address ->
                    item(key = "typed", contentType = "typed") {
                        SegmentedListItem(
                            onClick = { onIntent(NewConversationIntent.WriteTyped) },
                            shapes = ListItemDefaults.segmentedShapes(0, 1),
                            colors = segmentedListColors(),
                            enabled = !state.opening,
                            leadingContent = {
                                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null)
                            },
                            supportingContent = {
                                Text(address, fontFamily = SonaTheme.typography.monospace)
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            ListHeadline(stringResource(R.string.new_write_to, state.query.trim()))
                        }
                    }
                }
                val rows =
                    state.contacts.flatMap { contact -> contact.numbers.map { contact to it } }
                if (rows.isNotEmpty()) {
                    item(key = "h-contacts", contentType = "heading") {
                        SectionHeader(stringResource(R.string.new_contacts))
                    }
                }
                segmentedItems(
                    rows,
                    key = { (contact, number) -> "contact-${contact.id}-${number.value}" },
                    contentType = { "contact" },
                ) { (contact, number), shapes, itemModifier ->
                    SegmentedListItem(
                        onClick = { onIntent(NewConversationIntent.Pick(number.value)) },
                        shapes = shapes,
                        colors = segmentedListColors(),
                        enabled = !state.opening,
                        modifier = itemModifier,
                        leadingContent = { Avatar(contact.name) },
                        supportingContent = { Text("${number.label} · ${number.value}") },
                    ) {
                        ListHeadline(contact.name)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountChoice(state: NewConversationState, onIntent: (NewConversationIntent) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.new_from),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        state.accounts.forEach { account ->
            FilterChip(
                selected = account.id == state.accountId,
                onClick = { onIntent(NewConversationIntent.ChooseAccount(account.id)) },
                label = { Text(account.label) },
                leadingIcon = {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(account.accent))
                },
            )
        }
    }
}

private val StartProblem.text: Int
    get() =
        when (this) {
            StartProblem.NoAccount -> R.string.new_no_account
            StartProblem.NotAnAddress -> R.string.new_not_an_address
            StartProblem.AccountGone -> R.string.new_account_gone
        }

@Composable
@androidx.compose.ui.tooling.preview.PreviewLightDark
private fun NewConversationPreview() {
    SonaTheme {
        NewConversationScreen(
            state =
                NewConversationState(
                    loaded = true,
                    query = "10",
                    typedAddress = "sip:10@lab.local",
                    contacts =
                        listOf(Contact("local:1", "Ana", listOf(ContactNumber("Lab", "102")))),
                ),
            onIntent = {},
            onBack = {},
        )
    }
}
