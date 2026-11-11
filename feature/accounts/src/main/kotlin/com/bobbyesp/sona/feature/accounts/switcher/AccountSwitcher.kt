/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.accounts.switcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.sona.accounts.domain.AccountStatus
import com.bobbyesp.sona.accounts.domain.AccountsRepository
import com.bobbyesp.sona.accounts.domain.SetAccountEnabled
import com.bobbyesp.sona.accounts.domain.observeAccountStatuses
import com.bobbyesp.sona.accounts.ui.AccountStatusRow
import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.navigation.overlay.OverlaySheetSkeleton
import com.bobbyesp.sona.core.ui.contribution.ReadinessMonitor
import com.bobbyesp.sona.core.ui.contribution.problems
import com.bobbyesp.sona.core.ui.viewmodel.BaseViewModel
import com.bobbyesp.sona.feature.accounts.R
import com.bobbyesp.sona.sip.api.SipEngine
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

internal data class SwitcherState(
    val accounts: ImmutableList<AccountStatus> = persistentListOf(),
    /** How many things keep calls from working (see *Call readiness*); the sheet starts with it. */
    val readinessProblems: Int = 0,
)

internal sealed interface SwitcherIntent {
    data class Enable(val id: AccountId, val enabled: Boolean) : SwitcherIntent
}

/** Every account with its state; turning one on or off happens here, editing in the editor. */
internal class AccountSwitcherViewModel(
    repository: AccountsRepository,
    engine: SipEngine,
    private val setEnabled: SetAccountEnabled,
    readiness: ReadinessMonitor,
) : BaseViewModel<SwitcherIntent, SwitcherState, Nothing>(SwitcherState()) {

    init {
        launch {
            readiness.results.collect { results ->
                setState { copy(readinessProblems = results?.problems?.size ?: 0) }
            }
        }
        launch {
            observeAccountStatuses(repository, engine).collect { all ->
                setState { copy(accounts = all.toImmutableList()) }
            }
        }
    }

    override fun onHandleIntent(intent: SwitcherIntent) {
        when (intent) {
            is SwitcherIntent.Enable -> launch { setEnabled(intent.id, intent.enabled) }
        }
    }
}

@Composable
internal fun AccountSwitcherRoute(
    viewModel: AccountSwitcherViewModel,
    onEdit: (AccountId) -> Unit,
    onAdd: () -> Unit,
    onReadiness: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AccountSwitcherSheet(
        state = state,
        onReadiness = onReadiness,
        onEnable = { id, enabled -> viewModel.onSendIntent(SwitcherIntent.Enable(id, enabled)) },
        onEdit = onEdit,
        onAdd = onAdd,
    )
}

/** The accounts as a sheet over what was open, reached from the account chip. */
@Composable
internal fun AccountSwitcherSheet(
    state: SwitcherState,
    onEnable: (AccountId, Boolean) -> Unit,
    onEdit: (AccountId) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    onReadiness: () -> Unit = {},
) {
    OverlaySheetSkeleton(
        headingTitle = stringResource(R.string.accounts_title),
        headingDescription = stringResource(R.string.accounts_description),
        icon = Icons.Rounded.ManageAccounts,
        modifier = modifier,
        footer = {
            Button(
                onClick = onAdd,
                modifier =
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text(stringResource(R.string.accounts_add), Modifier.padding(start = 8.dp))
            }
        },
    ) {
        Column {
            if (state.readinessProblems > 0) {
                ListItem(
                    onClick = onReadiness,
                    supportingContent = {
                        Text(
                            pluralStringResource(
                                R.plurals.accounts_readiness_problems,
                                state.readinessProblems,
                                state.readinessProblems,
                            ),
                        )
                    },
                    leadingContent = { Icon(Icons.Rounded.Warning, contentDescription = null) },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                    shapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.large),
                    colors =
                        ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            leadingContentColor = MaterialTheme.colorScheme.error,
                            trailingContentColor = MaterialTheme.colorScheme.onErrorContainer,
                            supportingContentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.accounts_readiness))
                }
            }
            state.accounts.forEach { status ->
                val label = status.account.label
                val enabledDescription =
                    stringResource(R.string.accounts_enabled_description, label)
                Column(Modifier.clickable { onEdit(status.account.id) }) {
                    AccountStatusRow(
                        status = status,
                        modifier = Modifier.fillMaxWidth(),
                        trailing = {
                            Switch(
                                checked = status.account.enabled,
                                onCheckedChange = { onEnable(status.account.id, it) },
                                modifier =
                                    Modifier.semantics { contentDescription = enabledDescription },
                            )
                        },
                    )
                    if (status.account.isDefault && state.accounts.size > 1) {
                        Text(
                            text = stringResource(R.string.accounts_default),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 72.dp, bottom = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
