/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.settings

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.sona.core.designsystem.components.ConnectedChoice
import com.bobbyesp.sona.core.designsystem.components.ExpressiveSearch
import com.bobbyesp.sona.core.designsystem.components.RankedList
import com.bobbyesp.sona.core.designsystem.components.SectionHeader
import com.bobbyesp.sona.core.designsystem.components.SegmentedChoiceList
import com.bobbyesp.sona.core.designsystem.components.segmentedItems
import com.bobbyesp.sona.core.designsystem.components.segmentedListColors
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme
import com.bobbyesp.sona.core.model.settings.SettingsStore
import com.bobbyesp.sona.core.ui.contribution.SettingItem
import com.bobbyesp.sona.core.ui.contribution.SettingsArea
import kotlinx.coroutines.launch

/**
 * The settings root: a search that opens full screen and shows the matching options with their
 * controls, so a setting is found and changed without knowing where it lives; then every area as
 * one segmented group, each on a shape of its own.
 */
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
    SettingsScaffold(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        modifier = modifier,
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
        item(key = "search-gap") { Spacer(Modifier.size(8.dp)) }
        segmentedItems(state.areas, key = { it.area.id }, contentType = { "area" }) {
            entry,
            shapes,
            rowModifier ->
            AreaRow(entry.area, shapes, rowModifier, onClick = { onOpenArea(entry.area) })
        }
    }
}

/** What the search found, under the area each option belongs to. */
@Composable
private fun SearchResults(
    state: SettingsState,
    store: SettingsStore,
    onIntent: (SettingsIntent) -> Unit,
    onOpenKey: (NavKey) -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 16.dp)) {
        if (state.query.isNotBlank() && state.results.isEmpty()) {
            item(key = "none") {
                Text(
                    stringResource(R.string.settings_no_results),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
        }
        state.results.forEach { entry ->
            item(key = "h-${entry.area.id}") { SectionHeader(stringResource(entry.area.title)) }
            settingGroups(entry, store, onIntent, onOpenKey, showTitles = false)
        }
    }
}

/**
 * One area: a segmented group per feature that contributed to it, each under its heading, so an
 * area two features share reads as two groups and not one long list.
 */
@Composable
internal fun SettingsAreaScreen(
    entry: AreaItems?,
    store: SettingsStore,
    onIntent: (SettingsIntent) -> Unit,
    onOpenKey: (NavKey) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(
        title = entry?.let { stringResource(it.area.title) }.orEmpty(),
        subtitle = entry?.let { stringResource(it.area.summary) },
        onBack = onBack,
        modifier = modifier,
    ) {
        if (entry != null) settingGroups(entry, store, onIntent, onOpenKey, showTitles = true)
    }
}

/**
 * The settings screens' frame: Material's large flexible app bar, which collapses into a small one
 * as the list scrolls under it, and the list, at most a readable width.
 */
@Composable
internal fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { SettingsTopBar(title, subtitle, onBack, scrollBehavior) },
    ) { padding ->
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.fillMaxHeight().widthIn(max = 840.dp),
                contentPadding =
                    PaddingValues(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() + 32.dp,
                    ),
                content = content,
            )
        }
    }
}

@Composable
private fun SettingsTopBar(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    LargeFlexibleTopAppBar(
        title = { Text(title) },
        subtitle = subtitle?.let { { Text(it) } },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.settings_back),
                )
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        scrollBehavior = scrollBehavior,
    )
}

/**
 * An area's groups, each its own segmented group. With [showTitles], a group's heading leads it; a
 * group without one is set apart by space alone.
 */
private fun LazyListScope.settingGroups(
    entry: AreaItems,
    store: SettingsStore,
    onIntent: (SettingsIntent) -> Unit,
    onOpenKey: (NavKey) -> Unit,
    showTitles: Boolean,
) {
    entry.groups.forEachIndexed { index, group ->
        val title = group.title
        val groupKey = "${entry.area.id}-$index"
        if (showTitles && title != null) {
            item(key = "$groupKey-title", contentType = "header") {
                SectionHeader(stringResource(title))
            }
        } else if (index > 0 || showTitles) {
            item(key = "$groupKey-gap", contentType = "gap") { Spacer(Modifier.size(12.dp)) }
        }
        segmentedItems(
            group.items,
            key = { "$groupKey-${it.title}" },
            contentType = { it::class },
        ) { item, shapes, rowModifier ->
            SettingRow(item, shapes, rowModifier, store, onIntent, onOpenKey)
        }
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
        is SettingItem.Ranked -> RankedRow(item, shapes, modifier, store, onIntent)
        is SettingItem.Action -> ActionRow(item, shapes, modifier, onIntent)
        is SettingItem.Link -> LinkRow(item, shapes, modifier, onOpenKey)
        is SettingItem.Info -> InfoRow(item, shapes, modifier)
    }
}

/** A row's leading icon, when its item has one. */
private fun rowIcon(
    icon: ImageVector?,
    tint: Color = Color.Unspecified,
): (@Composable () -> Unit)? = icon?.let {
    {
        Icon(
            it,
            contentDescription = null,
            tint = tint.takeOrElse { LocalContentColor.current },
        )
    }
}

/** Something to read: its name and its value in the face versions are read in; a touch copies. */
@Composable
private fun InfoRow(item: SettingItem.Info, shapes: ListItemShapes, modifier: Modifier) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val text = item.text()
    SegmentedListItem(
        onClick = {
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("sona", text))) }
        },
        shapes = shapes,
        modifier = modifier,
        colors = segmentedListColors(),
        leadingContent = rowIcon(item.icon),
        supportingContent = { Text(text, fontFamily = SonaTheme.typography.monospace) },
        trailingContent = {
            Icon(
                Icons.Rounded.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        },
        content = { Text(stringResource(item.title)) },
    )
}

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
        leadingContent = rowIcon(item.icon),
        supportingContent = item.summary?.let { { Text(stringResource(it)) } },
        trailingContent = {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        },
        content = { Text(stringResource(item.title)) },
    )
}

/** A switch: the whole row toggles, and the thumb shows a check when it is on. */
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
        leadingContent = rowIcon(item.icon),
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

/**
 * A row that is not itself pressed: a list item of the group (its shape, color and icon), with the
 * buttons that are pressed under its text.
 */
@Composable
private fun <E : Enum<E>> InlineChoice(
    item: SettingItem.Choice<E>,
    current: E,
    shapes: ListItemShapes,
    modifier: Modifier,
    onIntent: (SettingsIntent) -> Unit,
) {
    SegmentedListItem(
        shapes = shapes,
        modifier = modifier,
        colors = segmentedListColors(),
        verticalAlignment = Alignment.Top,
        leadingContent = rowIcon(item.icon),
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item.summary?.let { Text(stringResource(it)) }
                ConnectedChoice(
                    options = item.setting.options,
                    selected = current,
                    onSelect = { onIntent(SettingsIntent.Write(item.setting, it)) },
                    label = { stringResource(item.label(it)) },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        content = { Text(stringResource(item.title)) },
    )
}

/** More values than fit in a row: the row says which is chosen, and opens them as a list. */
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
        leadingContent = rowIcon(item.icon),
        supportingContent = { Text(stringResource(item.label(current))) },
        trailingContent = {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        },
        content = { Text(stringResource(item.title)) },
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            icon = item.icon?.let { { Icon(it, contentDescription = null) } },
            title = { Text(stringResource(item.title)) },
            text = {
                SegmentedChoiceList(
                    options = item.setting.options,
                    selected = current,
                    onSelect = { option ->
                        onIntent(SettingsIntent.Write(item.setting, option))
                        open = false
                    },
                    label = { stringResource(item.label(it)) },
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
}

/**
 * Things in an order, each on or off: the row says which are on, first choice first, and opens the
 * list where each is switched and moved up or down. Every touch is written at once, as a switch is.
 */
@Composable
private fun RankedRow(
    item: SettingItem.Ranked,
    shapes: ListItemShapes,
    modifier: Modifier,
    store: SettingsStore,
    onIntent: (SettingsIntent) -> Unit,
) {
    val ranking by store.observe(item.setting).collectAsStateWithLifecycle(item.setting.default)
    var open by rememberSaveable { mutableStateOf(false) }
    val chosen = ranking.enabled.joinToString(", ") { item.label(it) }
    SegmentedListItem(
        onClick = { open = true },
        shapes = shapes,
        modifier = modifier,
        colors = segmentedListColors(),
        leadingContent = rowIcon(item.icon),
        supportingContent = {
            Text(chosen.ifEmpty { stringResource(R.string.settings_ranked_none) })
        },
        trailingContent = {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        },
        content = { Text(stringResource(item.title)) },
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            icon = item.icon?.let { { Icon(it, contentDescription = null) } },
            title = { Text(stringResource(item.title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    item.summary?.let {
                        Text(stringResource(it), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.size(8.dp))
                    }
                    RankedList(
                        ranking = ranking,
                        label = item.label,
                        onChange = { onIntent(SettingsIntent.Write(item.setting, it)) },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(R.string.settings_done))
                }
            },
        )
    }
}

/** Something done, not stored; a destructive one is in the error color, icon and words alike. */
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
        leadingContent = rowIcon(item.icon, tint),
        supportingContent = item.summary?.let { { Text(stringResource(it)) } },
        content = { Text(stringResource(item.title), color = tint) },
    )
    val confirm = item.confirm
    if (asking && confirm != null) {
        AlertDialog(
            onDismissRequest = { asking = false },
            icon = item.icon?.let { { Icon(it, contentDescription = null, tint = tint) } },
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
