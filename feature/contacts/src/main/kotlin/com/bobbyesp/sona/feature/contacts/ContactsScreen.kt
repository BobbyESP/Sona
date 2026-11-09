/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.contacts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.sona.calling.ui.problemText
import com.bobbyesp.sona.core.designsystem.components.ExpressiveSearch
import com.bobbyesp.sona.core.designsystem.components.segmentedItems
import com.bobbyesp.sona.core.designsystem.components.segmentedListColors
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme
import com.bobbyesp.sona.core.ui.TopLevelEmptyState
import com.bobbyesp.sona.core.ui.TopLevelScaffold
import com.bobbyesp.sona.core.ui.contribution.LocalCallScreen
import com.bobbyesp.sona.core.ui.events.CollectEventsEffect
import com.bobbyesp.sona.core.ui.rememberCallStarter
import com.bobbyesp.sona.core.ui.rememberContactsAccess
import com.bobbyesp.sona.directory.domain.Contact
import com.bobbyesp.sona.directory.domain.ContactNumber
import com.bobbyesp.sona.directory.domain.ContactSource
import com.bobbyesp.sona.directory.ui.Avatar
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun ContactsRoute(
    onNew: () -> Unit,
    onEdit: (Contact) -> Unit,
    viewModel: ContactsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openCall = LocalCallScreen.current
    val callStarter = rememberCallStarter()
    val contactsAccess = rememberContactsAccess {
        viewModel.onSendIntent(ContactsIntent.DeviceAccessAnswered)
    }
    viewModel.effects.CollectEventsEffect { effect ->
        when (effect) {
            ContactsEffect.OpenCall -> openCall()
        }
    }
    ContactsScreen(
        state = state,
        onIntent = { intent ->
            if (intent is ContactsIntent.Call) callStarter.start { viewModel.onSendIntent(intent) }
            else viewModel.onSendIntent(intent)
        },
        onNew = onNew,
        onEdit = onEdit,
        onAllowPhone = contactsAccess::request,
    )
}

/**
 * Contacts, in Material 3 Expressive: favorites as a carousel to call with one touch, everyone else
 * as segmented groups under their letter, search that grows from a pill into a full-screen view,
 * and a floating action that collapses to its icon while the list scrolls.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ContactsScreen(
    state: ContactsState,
    onIntent: (ContactsIntent) -> Unit,
    onNew: () -> Unit,
    onEdit: (Contact) -> Unit,
    onAllowPhone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    TopLevelScaffold(
        title = stringResource(R.string.contacts_title),
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                expanded = !listState.canScrollBackward,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.contacts_new)) },
            )
        },
    ) { padding, contentModifier ->
        when {
            !state.loaded ->
                Box(contentModifier.padding(padding), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            state.total == 0 && !state.offerDevice ->
                TopLevelEmptyState(
                    title = stringResource(R.string.contacts_empty_title),
                    description = stringResource(R.string.contacts_empty_description),
                    icon = Icons.Rounded.Contacts,
                    padding = padding,
                    contentModifier = contentModifier,
                )
            else ->
                Box(contentModifier, contentAlignment = Alignment.TopCenter) {
                    ContactList(state, listState, onIntent, padding, onAllowPhone)
                }
        }
    }

    state.details?.let { contact ->
        ContactSheet(
            contact = contact,
            problem = state.problem?.problemText(),
            onIntent = onIntent,
            onEdit = { onEdit(contact) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ContactList(
    state: ContactsState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onIntent: (ContactsIntent) -> Unit,
    padding: PaddingValues,
    onAllowPhone: () -> Unit,
) {
    val favorites = state.favorites
    val byLetter =
        remember(state.contacts) {
            state.contacts
                .filter { !it.favorite }
                .groupBy { it.name.firstOrNull()?.uppercaseChar()?.takeIf(Char::isLetter) ?: '#' }
        }

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
                placeholder = stringResource(R.string.contacts_search),
                backDescription = stringResource(R.string.contacts_search_close),
                clearDescription = stringResource(R.string.contacts_search_clear),
                onQueryChange = { onIntent(ContactsIntent.Query(it)) },
            ) {
                SearchResults(state.contacts, onIntent)
            }
        }
        if (state.offerDevice) {
            item(key = "phone", contentType = "phone") { PhoneContactsCard(onAllowPhone) }
        }
        if (state.contacts.isEmpty() && state.total > 0) {
            item(key = "none", contentType = "none") {
                Text(
                    stringResource(R.string.contacts_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        if (favorites.isNotEmpty()) {
            item(key = "h-favorites", contentType = "heading") {
                Heading(stringResource(R.string.contacts_favorites))
            }
            item(key = "favorites", contentType = "favorites") {
                FavoritesCarousel(favorites, onIntent)
            }
        }
        byLetter.forEach { (letter, group) ->
            stickyHeader(key = "h-$letter", contentType = "heading") {
                Heading(letter.toString(), Modifier.background(MaterialTheme.colorScheme.surface))
            }
            contactRows(group, onIntent)
        }
    }
}

/** The people behind the search: the same rows, filtered by what was typed. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchResults(contacts: List<Contact>, onIntent: (ContactsIntent) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
        if (contacts.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.contacts_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        contactRows(contacts, onIntent)
    }
}

private fun LazyListScope.contactRows(contacts: List<Contact>, onIntent: (ContactsIntent) -> Unit) {
    segmentedItems(contacts, key = { it.id }, contentType = { "contact" }) {
        contact,
        shapes,
        modifier ->
        ContactRow(contact, shapes, modifier, onIntent)
    }
}

@Composable
private fun Heading(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                .semantics { heading() },
    )
}

/** Favorites, one swipe from each other: tap one to call it, or to choose among its numbers. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FavoritesCarousel(favorites: List<Contact>, onIntent: (ContactsIntent) -> Unit) {
    HorizontalUncontainedCarousel(
        state = rememberCarouselState { favorites.size },
        itemWidth = 96.dp,
        itemSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) { index ->
        val contact = favorites[index]
        val single = contact.numbers.singleOrNull()
        Column(
            modifier =
                Modifier.clip(MaterialTheme.shapes.extraLarge)
                    .clickable {
                        if (single != null) onIntent(ContactsIntent.Call(single.value))
                        else onIntent(ContactsIntent.Open(contact))
                    }
                    .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Avatar(contact.name, size = 72.dp)
            Spacer(Modifier.size(8.dp))
            Text(
                contact.name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The card that offers the phone's contacts: what is read, why, and the button that asks. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PhoneContactsCard(onAllow: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialShapes.Cookie9Sided.toShape(),
                color = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Contacts, contentDescription = null)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.contacts_phone_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.contacts_phone_text),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = onAllow,
                    shapes = ButtonDefaults.shapes(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary,
                        ),
                ) {
                    Text(stringResource(R.string.contacts_phone_allow))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ContactRow(
    contact: Contact,
    shapes: ListItemShapes,
    modifier: Modifier,
    onIntent: (ContactsIntent) -> Unit,
) {
    val first = contact.numbers.firstOrNull()
    SegmentedListItem(
        onClick = { onIntent(ContactsIntent.Open(contact)) },
        shapes = shapes,
        modifier = modifier,
        colors = segmentedListColors(),
        leadingContent = { Avatar(contact.name) },
        supportingContent =
            first?.let {
                {
                    val more = contact.numbers.size - 1
                    Text(
                        buildString {
                            append(first.value)
                            if (more > 0) append("  +").append(more)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        trailingContent =
            if (contact.numbers.size == 1 && first != null) {
                {
                    FilledTonalIconButton(
                        onClick = { onIntent(ContactsIntent.Call(first.value)) },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            Icons.Rounded.Call,
                            contentDescription =
                                stringResource(R.string.contacts_call_name, contact.name),
                        )
                    }
                }
            } else null,
        content = { Text(contact.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}

/**
 * A contact: who, each number with its own call button, and the contact's actions in one button
 * group that moves what does not fit into a menu.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ContactSheet(
    contact: Contact,
    problem: String?,
    onIntent: (ContactsIntent) -> Unit,
    onEdit: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = { onIntent(ContactsIntent.Close) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(contact.name, size = 72.dp)
                Spacer(Modifier.size(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(contact.name, style = MaterialTheme.typography.headlineSmall)
                    if (contact.source == ContactSource.Device) {
                        Text(
                            stringResource(R.string.contacts_from_phone),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                contact.numbers.forEachIndexed { index, number ->
                    SegmentedListItem(
                        onClick = { onIntent(ContactsIntent.Call(number.value)) },
                        shapes = ListItemDefaults.segmentedShapes(index, contact.numbers.size),
                        colors = segmentedListColors(),
                        overlineContent = { Text(number.label) },
                        trailingContent = {
                            FilledTonalIconButton(
                                onClick = { onIntent(ContactsIntent.Call(number.value)) },
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(
                                    Icons.Rounded.Call,
                                    contentDescription = stringResource(R.string.contacts_call),
                                )
                            }
                        },
                        content = { Text(number.value) },
                    )
                }
            }
            if (problem != null) {
                Text(problem, color = MaterialTheme.colorScheme.error)
            }
            if (contact.editable) {
                val favoriteLabel = stringResource(R.string.contacts_favorite)
                val editLabel = stringResource(R.string.contacts_edit)
                val deleteLabel = stringResource(R.string.contacts_delete)
                ButtonGroup(
                    overflowIndicator = { menu ->
                        FilledIconButton(
                            onClick = { if (menu.isShowing) menu.dismiss() else menu.show() },
                        ) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.contacts_more),
                            )
                        }
                    },
                ) {
                    toggleableItem(
                        checked = contact.favorite,
                        label = favoriteLabel,
                        onCheckedChange = { onIntent(ContactsIntent.Favorite(contact, it)) },
                        icon = {
                            Icon(
                                if (contact.favorite) Icons.Rounded.Star
                                else Icons.Rounded.StarBorder,
                                contentDescription = null,
                            )
                        },
                    )
                    clickableItem(
                        onClick = onEdit,
                        label = editLabel,
                        icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    )
                    clickableItem(
                        onClick = { confirmDelete = true },
                        label = deleteLabel,
                        icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                    )
                }
            }
            Spacer(Modifier.size(16.dp))
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.contacts_delete_title, contact.name)) },
            text = { Text(stringResource(R.string.contacts_delete_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onIntent(ContactsIntent.Delete(contact))
                    },
                ) {
                    Text(
                        stringResource(R.string.contacts_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.contacts_cancel))
                }
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun ContactsScreenPreview() {
    val ana = Contact("local:1", "Ana Gómez", listOf(ContactNumber("Lab", "102")), favorite = true)
    val luna =
        Contact(
            "local:2",
            "Luna",
            listOf(ContactNumber("Lab", "103"), ContactNumber("Mobile", "600111")),
        )
    SonaTheme {
        ContactsScreen(
            state = ContactsState(loaded = true, contacts = listOf(ana, luna), total = 2),
            onIntent = {},
            onNew = {},
            onEdit = {},
            onAllowPhone = {},
        )
    }
}
