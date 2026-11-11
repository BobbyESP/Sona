/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bobbyesp.sona.core.designsystem.R
import com.bobbyesp.sona.core.model.settings.Ranking

/**
 * A [Ranking] to change: each entry with its checkbox (on or off) and two arrows that move it one
 * place, so the order is set without dragging, by touch, keyboard or a screen reader alike. Every
 * touch gives the whole new ranking to [onChange].
 *
 * @param label The name shown for an entry's id.
 */
@Composable
fun RankedList(
    ranking: Ranking,
    label: (String) -> String,
    onChange: (Ranking) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        ranking.entries.forEachIndexed { index, entry ->
            val name = label(entry.id)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f)
                        .toggleable(
                            value = entry.enabled,
                            role = Role.Checkbox,
                            onValueChange = { onChange(ranking.switched(entry.id, it)) },
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = entry.enabled, onCheckedChange = null)
                    Spacer(Modifier.size(12.dp))
                    Text(name)
                }
                IconButton(
                    onClick = { onChange(ranking.moved(entry.id, up = true)) },
                    enabled = index > 0,
                ) {
                    Icon(
                        Icons.Rounded.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.ranked_move_up, name),
                    )
                }
                IconButton(
                    onClick = { onChange(ranking.moved(entry.id, up = false)) },
                    enabled = index < ranking.entries.lastIndex,
                ) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.ranked_move_down, name),
                    )
                }
            }
        }
    }
}
