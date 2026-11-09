/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.call

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.sona.calling.domain.CallCoordinator
import org.koin.compose.koinInject

/**
 * The way back to a call from anywhere in the app: a pill floating over the screens while a call is
 * in progress, as long as the call screen is not the one shown.
 */
@Composable
fun OngoingCallBanner(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val coordinator: CallCoordinator = koinInject()
    val calls by coordinator.calls.collectAsStateWithLifecycle()
    val call = calls.firstOrNull { !it.isOver }
    val returnLabel = stringResource(R.string.call_return)

    AnimatedVisibility(
        visible = call != null,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
    ) {
        Surface(
            onClick = onOpen,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 6.dp,
            modifier =
                Modifier.heightIn(min = 48.dp).semantics {
                    contentDescription = returnLabel
                    role = Role.Button
                },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.PhoneInTalk,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    stringResource(R.string.call_back_to, call?.title.orEmpty()),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
