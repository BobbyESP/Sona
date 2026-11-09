/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.designsystem.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Material 3 Expressive search: a pill that, when pressed, grows into a full-screen view with the
 * results, and shrinks back when closed. The pill is where the screen puts it (at the top of a
 * list); the results are whatever [results] says, given the text typed.
 *
 * [onQueryChange] reports the text as it is typed, for the screen's state to filter by; closing the
 * view clears it, so the list behind is whole again.
 *
 * @param placeholder What the field says while empty; also its accessibility name.
 * @param backDescription The close button's accessibility name.
 * @param clearDescription The clear button's accessibility name.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveSearch(
    placeholder: String,
    backDescription: String,
    clearDescription: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textFieldState: TextFieldState = rememberTextFieldState(),
    results: @Composable ColumnScope.(query: String) -> Unit,
) {
    val searchBarState = rememberSearchBarState()
    val scope = rememberCoroutineScope()
    val query by remember { derivedStateOf { textFieldState.text.toString() } }

    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }.collect(onQueryChange)
    }

    val inputField: @Composable () -> Unit = {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            onSearch = { scope.launch { searchBarState.animateToCollapsed() } },
            placeholder = { Text(placeholder) },
            leadingIcon = {
                if (searchBarState.currentValue == SearchBarValue.Expanded) {
                    IconButton(
                        onClick = {
                            textFieldState.clearText()
                            scope.launch { searchBarState.animateToCollapsed() }
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = backDescription,
                        )
                    }
                } else {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                }
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { textFieldState.clearText() }) {
                        Icon(Icons.Rounded.Close, contentDescription = clearDescription)
                    }
                }
            },
        )
    }

    SearchBar(
        state = searchBarState,
        inputField = inputField,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
    ExpandedFullScreenSearchBar(state = searchBarState, inputField = inputField) { results(query) }
}
