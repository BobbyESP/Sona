/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.sona.core.designsystem.components.ConnectedChoice
import com.bobbyesp.sona.core.designsystem.components.ExpressiveSearch
import com.bobbyesp.sona.core.designsystem.components.SectionHeader
import com.bobbyesp.sona.core.designsystem.components.segmentedItems
import com.bobbyesp.sona.core.designsystem.components.segmentedListColors
import com.bobbyesp.sona.core.model.settings.SettingsStore
import com.bobbyesp.sona.core.ui.contribution.SettingItem
import com.bobbyesp.sona.core.ui.contribution.SettingsArea

/**
 * The settings root: every area as a segmented group, and a search that opens full screen and shows
 * the matching options with their controls, so a setting can be found and changed without knowing
 * where it lives.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsRootScreen(
    state: SettingsState,
    store: SettingsStore,
    onIntent: (SettingsIntent) -> Unit,
    onOpenArea: (SettingsArea) -> Unit,
    onOpenKey: (NavKey) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.fillMaxHeight().widthIn(max = 840.dp),
                contentPadding =
                    PaddingValues(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() + 24.dp,
                    ),
            ) {
                item(key = "search", contentType = "search") {
                    ExpressiveSearch(
                        placeholder = stringResource(R.string.settings_search),
                        backDescription = stringResource(R.string.settings_search_close),
                        clearDescription = stringResource(R.string.settings_search_clear),
                        onQueryChange = { onIntent(SettingsIntent.Query(it)) },
                    ) {
                        SearchResults(state, store, onIntent, onOpenKey)
                    }
                }
                segmentedItems(state.areas, key = { it.area.id }, contentType = { "area" }) {
                    entry,
                    shapes,
                    rowModifier ->
                    AreaRow(entry.area, shapes, rowModifier, onClick = { onOpenArea(entry.area) })
                }
            }
        }
    }
}

/** What the search found, grouped under the area each option belongs to. */
@Composable
private fun SearchResults(
    state: SettingsState,
    store: SettingsStore,
    onIntent: (SettingsIntent) -> Unit,
    onOpenKey: (NavKey) -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
        if (state.query.isNotBlank() && state.results.isEmpty()) {
            item(key = "none") {
                Text(
                    stringResource(R.string.settings_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        state.results.forEach { entry ->
            item(key = "h-${entry.area.id}") { SectionHeader(stringResource(entry.area.title)) }
            settingRows(entry, store, onIntent, onOpenKey)
        }
    }
}

/** One area: every item the features contributed to it, as one segmented group. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsAreaScreen(
    entry: AreaItems?,
    store: SettingsStore,
    onIntent: (SettingsIntent) -> Unit,
    onOpenKey: (NavKey) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { entry?.let { Text(stringResource(it.area.title)) } },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.fillMaxHeight().widthIn(max = 840.dp),
                contentPadding =
                    PaddingValues(
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 24.dp,
                    ),
            ) {
                if (entry != null) settingRows(entry, store, onIntent, onOpenKey)
            }
        }
    }
}

private fun LazyListScope.settingRows(
    entry: AreaItems,
    store: SettingsStore,
    onIntent: (SettingsIntent) -> Unit,
    onOpenKey: (NavKey) -> Unit,
) {
    segmentedItems(
        entry.items,
        key = { "${entry.area.id}-${it.title}" },
        contentType = { it::class },
    ) { item, shapes, rowModifier ->
        SettingRow(item, shapes, rowModifier, store, onIntent, onOpenKey)
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(
            Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.settings_back),
        )
    }
}

/** An area on the root: its icon on a shape of its own, its name, and what it holds. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AreaRow(
    area: SettingsArea,
    shapes: ListItemShapes,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape =
        when (area.id.hashCode().mod(4)) {
            0 -> MaterialShapes.Cookie9Sided
            1 -> MaterialShapes.Clover4Leaf
            2 -> MaterialShapes.Flower
            else -> MaterialShapes.Pentagon
        }.toShape()
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier,
        colors = segmentedListColors(),
        leadingContent = {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = shape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(area.icon, contentDescription = null)
                }
            }
        },
        supportingContent = { Text(stringResource(area.summary)) },
        trailingContent = {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        },
        content = { Text(stringResource(area.title)) },
    )
}

@Composable
private fun SettingRow(
    item: SettingItem,
    shapes: ListItemShapes,
    modifier: Modifier,
    store: SettingsStore,
    onIntent: (SettingsIntent) -> Unit,
    onOpenKey: (NavKey) -> Unit,
) {
    when (item) {
        is SettingItem.Switch -> SwitchRow(item, shapes, modifier, store, onIntent)
        is SettingItem.Choice<*> -> ChoiceRow(item, shapes, modifier, store, onIntent)
        is SettingItem.Action -> ActionRow(item, shapes, modifier, onIntent)
        is SettingItem.Link -> LinkRow(item, shapes, modifier, onOpenKey)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LinkRow(
    item: SettingItem.Link,
    shapes: ListItemShapes,
    modifier: Modifier,
    onOpenKey: (NavKey) -> Unit,
) {
    SegmentedListItem(
        onClick = { onOpenKey(item.key) },
        shapes = shapes,
        modifier = modifier,
        colors = segmentedListColors(),
        supportingContent = item.summary?.let { { Text(stringResource(it)) } },
        trailingContent = {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        },
        content = { Text(stringResource(item.title)) },
    )
}

/** A switch: the whole row toggles, and the thumb shows a check when it is on. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SwitchRow(
    item: SettingItem.Switch,
    shapes: ListItemShapes,
    modifier: Modifier,
    store: SettingsStore,
    onIntent: (SettingsIntent) -> Unit,
) {
    val on by store.observe(item.setting).collectAsStateWithLifecycle(item.setting.default)
    SegmentedListItem(
        checked = on,
        onCheckedChange = { onIntent(SettingsIntent.Write(item.setting, it)) },
        shapes = shapes,
        modifier = modifier,
        colors = segmentedListColors(),
        supportingContent = item.summary?.let { { Text(stringResource(it)) } },
        trailingContent = {
            Switch(
                checked = on,
                onCheckedChange = null,
                thumbContent =
                    if (on) {
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
        content = { Text(stringResource(item.title)) },
    )
}

/**
 * A choice among a few values: shown right there as a connected group of buttons when there are up
 * to three (one touch changes it), and as a list in a dialog when there are more.
 */
@Composable
private fun <E : Enum<E>> ChoiceRow(
    item: SettingItem.Choice<E>,
    shapes: ListItemShapes,
    modifier: Modifier,
    store: SettingsStore,
    onIntent: (SettingsIntent) -> Unit,
) {
    val current by store.observe(item.setting).collectAsStateWithLifecycle(item.setting.default)
    if (item.setting.options.size <= 3) {
        InlineChoice(item, current, shapes, modifier, onIntent)
    } else {
        DialogChoice(item, current, shapes, modifier, onIntent)
    }
}

/** A row that is not itself pressed: its title and text, and the buttons that are. */
@Composable
private fun <E : Enum<E>> InlineChoice(
    item: SettingItem.Choice<E>,
    current: E,
    shapes: ListItemShapes,
    modifier: Modifier,
    onIntent: (SettingsIntent) -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shapes.shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(stringResource(item.title), style = MaterialTheme.typography.bodyLarge)
            item.summary?.let {
                Text(
                    stringResource(it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(12.dp))
            ConnectedChoice(
                options = item.setting.options,
                selected = current,
                onSelect = { onIntent(SettingsIntent.Write(item.setting, it)) },
                label = { stringResource(item.label(it)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <E : Enum<E>> DialogChoice(
    item: SettingItem.Choice<E>,
    current: E,
    shapes: ListItemShapes,
    modifier: Modifier,
    onIntent: (SettingsIntent) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    SegmentedListItem(
        onClick = { open = true },
        shapes = shapes,
        modifier = modifier,
        colors = segmentedListColors(),
        supportingContent = { Text(stringResource(item.label(current))) },
        content = { Text(stringResource(item.title)) },
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(item.title)) },
            text = {
                Column {
                    item.setting.options.forEach { option ->
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(
                                    selected = option == current,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onIntent(SettingsIntent.Write(item.setting, option))
                                        open = false
                                    },
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == current, onClick = null)
                            Spacer(Modifier.size(12.dp))
                            Text(stringResource(item.label(option)))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActionRow(
    item: SettingItem.Action,
    shapes: ListItemShapes,
    modifier: Modifier,
    onIntent: (SettingsIntent) -> Unit,
) {
    var asking by rememberSaveable { mutableStateOf(false) }
    val tint = if (item.destructive) MaterialTheme.colorScheme.error else Color.Unspecified
    SegmentedListItem(
        onClick = {
            if (item.confirm != null) asking = true else onIntent(SettingsIntent.Run(item))
        },
        shapes = shapes,
        modifier = modifier,
        colors = segmentedListColors(),
        supportingContent = item.summary?.let { { Text(stringResource(it)) } },
        content = { Text(stringResource(item.title), color = tint) },
    )
    val confirm = item.confirm
    if (asking && confirm != null) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(item.title)) },
            text = { Text(stringResource(confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        asking = false
                        onIntent(SettingsIntent.Run(item))
                    },
                ) {
                    Text(stringResource(R.string.settings_confirm), color = tint)
                }
            },
            dismissButton = {
                TextButton(onClick = { asking = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
}
