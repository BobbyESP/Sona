/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * What Sona's list screens are made of, so Recents, Contacts, Settings and Diagnostics read as one
 * family: Material 3 Expressive's segmented lists (items that are one rounded group, with their
 * corners morphing as they are pressed), connected button groups, and a swipe that says what it
 * does. Each takes the theme's shapes, colors and motion; none owns a color or a duration.
 */

/** The widest a list reads comfortably: wider windows get margins, not longer lines. */
val ReadableWidth: Dp = 840.dp

/** Centers content in at most [ReadableWidth], and lets a narrower window have all of it. */
fun Modifier.readableWidth(): Modifier = this.fillMaxWidth().widthIn(max = ReadableWidth)

/**
 * Lays out [items] as one segmented group: the first and last have the group's outer corners, the
 * ones between are squarer, and a small gap shows the segments are separate.
 *
 * [itemContent] gets the shapes for the item's place in the group and a modifier carrying the
 * group's horizontal margin, the gap, and the placement animation, which it must apply to the item.
 */
fun <T> LazyListScope.segmentedItems(
    items: List<T>,
    key: ((T) -> Any)? = null,
    contentType: (T) -> Any? = { null },
    itemContent:
        @Composable
        LazyItemScope.(item: T, shapes: ListItemShapes, modifier: Modifier) -> Unit,
) {
    val count = items.size
    items(
        count = count,
        key = key?.let { k -> { index -> k(items[index]) } },
        contentType = { contentType(items[it]) },
    ) { index ->
        val shapes = ListItemDefaults.segmentedShapes(index = index, count = count)
        itemContent(
            items[index],
            shapes,
            Modifier.animateItem()
                .padding(horizontal = 16.dp, vertical = ListItemDefaults.SegmentedGap / 2),
        )
    }
}

/** A heading for a group of items: read as a heading by TalkBack, in the primary color. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            modifier.padding(start = 32.dp, end = 16.dp, top = 20.dp, bottom = 8.dp).semantics {
                heading()
            },
    )
}

/**
 * One choice among a few, as a connected group of toggle buttons: the chosen one is filled and
 * round, the others square off against it, and each morphs as it is pressed. For two to four short
 * options; more belong in a menu or a dialog.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ConnectedChoice(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().semantics { role = Role.RadioButton },
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val shapes =
                when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                }
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { if (it) onSelect(option) },
                shapes = shapes,
                modifier = Modifier.weight(1f),
            ) {
                Text(label(option), maxLines = 1)
            }
        }
    }
}

/**
 * Swipe a row from its end to delete it. The background says what the swipe does (an error
 * container and a bin), the row snaps back unless [onDelete] is taken, and TalkBack gets the same
 * thing as a custom action named [deleteLabel], since a swipe cannot be made without sight.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteRow(
    onDelete: () -> Unit,
    deleteLabel: String,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        modifier =
            modifier.semantics {
                customActions =
                    listOf(
                        CustomAccessibilityAction(deleteLabel) {
                            onDelete()
                            true
                        },
                    )
            },
        enableDismissFromStartToEnd = false,
        onDismiss = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDelete()
        },
        backgroundContent = {
            Box(
                Modifier.fillMaxSize()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(end = 24.dp),
                )
            }
        },
        content = { content() },
    )
}

/**
 * The colors of every segmented list in Sona. The container is one tone above the page, so a group
 * reads as a surface in the light theme and the dark one alike, with no border or shadow.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun segmentedListColors(): ListItemColors =
    ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
