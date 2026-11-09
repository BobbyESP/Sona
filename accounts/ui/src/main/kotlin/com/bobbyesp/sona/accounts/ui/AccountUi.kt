/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.accounts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bobbyesp.sona.accounts.domain.Account
import com.bobbyesp.sona.accounts.domain.AccountStatus
import com.bobbyesp.sona.accounts.domain.RegistrationProblem
import com.bobbyesp.sona.core.designsystem.components.RegistrationIndicator
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme
import com.bobbyesp.sona.sip.api.RegistrationState

/** What the account chip and rows show for an engine's state. */
fun RegistrationState?.toIndicator(): RegistrationIndicator =
    when (this) {
        RegistrationState.Registered -> RegistrationIndicator.Registered
        RegistrationState.Registering -> RegistrationIndicator.Registering
        is RegistrationState.Failed -> RegistrationIndicator.Failed
        RegistrationState.Offline,
        RegistrationState.Unregistered,
        null -> RegistrationIndicator.Offline
    }

/** The account's color, as Compose draws it. */
val Account.accent: Color
    get() = Color(color)

/** An account's state in a word: *Registered*, *Registering…*, *Turned off*… */
@Composable
fun AccountStatus.stateText(): String =
    stringResource(
        when {
            !account.enabled -> R.string.account_state_off
            else ->
                when (registration) {
                    RegistrationState.Registered -> R.string.account_state_registered
                    RegistrationState.Registering -> R.string.account_state_registering
                    RegistrationState.Offline -> R.string.account_state_offline
                    is RegistrationState.Failed -> R.string.account_state_failed
                    RegistrationState.Unregistered -> R.string.account_state_registering
                }
        },
    )

/**
 * Why a registration failed, as a sentence for anyone, and its technical line (the SIP status, or
 * what was tried) for *Show SIP details*.
 */
data class ProblemText(val sentence: String, val technical: String?)

@Composable
fun RegistrationState.Failed.explain(account: Account): ProblemText {
    val problem = RegistrationProblem.of(this)
    val sentence =
        when (problem) {
            is RegistrationProblem.WrongCredentials ->
                stringResource(R.string.problem_wrong_credentials)
            is RegistrationProblem.Refused -> stringResource(R.string.problem_refused)
            is RegistrationProblem.UnknownExtension ->
                stringResource(R.string.problem_unknown_extension, account.username)
            is RegistrationProblem.NoAnswer ->
                stringResource(R.string.problem_no_answer, account.server.toString())
            is RegistrationProblem.ServerError -> stringResource(R.string.problem_server_error)
            is RegistrationProblem.Other -> stringResource(R.string.problem_other)
        }
    return ProblemText(sentence, problem.status?.toString() ?: detail)
}

/** The account's mark: its initial on a circle of its color. */
@Composable
fun AccountMark(account: Account, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(40.dp).clip(CircleShape).background(account.accent),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = account.label.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "#",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
    }
}

/**
 * An account in a list: its mark, its label, its address (in the monospace face SIP is read in) and
 * its state in a word; failures in the error color.
 */
@Composable
fun AccountStatusRow(
    status: AccountStatus,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    val failed = status.account.enabled && status.registration is RegistrationState.Failed
    Row(
        modifier = modifier.heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountMark(status.account)
        Column(Modifier.weight(1f)) {
            Text(
                text = status.account.label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = status.account.identity.uri,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = SonaTheme.typography.monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = status.stateText(),
                style = MaterialTheme.typography.labelMedium,
                color =
                    if (failed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}

/** The account chip's place when there is no account: a way to add one. */
@Composable
fun AddAccountChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.add_account), style = MaterialTheme.typography.labelLarge)
        }
    }
}
