/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bobbyesp.sona.core.designsystem.blur.blurSource
import com.bobbyesp.sona.core.designsystem.blur.rememberBlurSource
import com.bobbyesp.sona.core.designsystem.components.FrostedLargeTopAppBar
import com.bobbyesp.sona.core.ui.contribution.LocalAccountChip
import com.bobbyesp.sona.core.ui.contribution.LocalOpenSettings

/**
 * The frame of a top-level destination (Recents, Contacts, Keypad): Material's large flexible app
 * bar, frosted once content scrolls beneath it, with the account chip at its end. Sharing it is
 * what makes the tabs read as one app.
 *
 * Where height is short (a phone in landscape), the bar is Material's small one: a large title
 * would take half of what there is. Decided from the room this screen was given, not the window.
 *
 * @param snackbarHost Where short messages with an action (*Call deleted · Undo*) appear.
 * @param floatingActionButton The screen's main action, if it has one.
 * @param content Given the padding to lay out with, and the modifier its scrolling container takes
 *   (it records what passes under the bar, and collapses the bar as it scrolls).
 */
@Composable
fun TopLevelScaffold(
    title: String,
    modifier: Modifier = Modifier,
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (padding: PaddingValues, contentModifier: Modifier) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val behindBar = rememberBlurSource()
    val isContentScrolled by remember {
        derivedStateOf { scrollBehavior.state.overlappedFraction > 0f }
    }
    val accountChip = LocalAccountChip.current
    val openSettings = LocalOpenSettings.current
    val actions: @Composable RowScope.() -> Unit = {
        accountChip()
        if (openSettings != null) {
            IconButton(onClick = openSettings) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.settings_open),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
    }

    BoxWithConstraints(modifier) {
        val shortHeight = maxHeight < CompactHeight
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.surface,
            snackbarHost = snackbarHost,
            floatingActionButton = floatingActionButton,
            topBar = {
                if (shortHeight) {
                    TopAppBar(
                        title = { Text(text = title, maxLines = 1) },
                        actions = actions,
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                    )
                } else {
                    FrostedLargeTopAppBar(
                        title = title,
                        isContentScrolled = isContentScrolled,
                        scrollBehavior = scrollBehavior,
                        blurSource = behindBar,
                        actions = actions,
                    )
                }
            },
        ) { padding ->
            content(padding, Modifier.fillMaxSize().blurSource(behindBar))
        }
    }
}

/** Material's compact height class ends here. */
private val CompactHeight = 480.dp
