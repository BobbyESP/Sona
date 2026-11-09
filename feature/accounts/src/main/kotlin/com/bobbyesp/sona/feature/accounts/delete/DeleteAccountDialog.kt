/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.accounts.delete

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.sona.accounts.domain.AccountsRepository
import com.bobbyesp.sona.accounts.domain.DeleteAccount
import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.navigation.overlay.OverlayForm
import com.bobbyesp.sona.core.ui.events.CollectEventsEffect
import com.bobbyesp.sona.core.ui.viewmodel.BaseViewModel
import com.bobbyesp.sona.feature.accounts.R

internal data class DeleteState(val label: String = "")

internal sealed interface DeleteIntent {
    data object Confirm : DeleteIntent
}

internal sealed interface DeleteEffect {
    data object Deleted : DeleteEffect
}

internal class DeleteAccountViewModel(
    accountId: String,
    repository: AccountsRepository,
    private val deleteAccount: DeleteAccount,
) : BaseViewModel<DeleteIntent, DeleteState, DeleteEffect>(DeleteState()) {

    private val id = AccountId(accountId)

    init {
        launch { repository.get(id)?.let { setState { copy(label = it.label) } } }
    }

    override fun onHandleIntent(intent: DeleteIntent) {
        when (intent) {
            DeleteIntent.Confirm ->
                launch {
                    deleteAccount(id)
                    sendEffect(DeleteEffect.Deleted)
                }
        }
    }
}

/** Deleting asks first: the account's password goes with it and cannot be brought back. */
@Composable
internal fun DeleteAccountRoute(
    viewModel: DeleteAccountViewModel,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    viewModel.effects.CollectEventsEffect { effect ->
        when (effect) {
            DeleteEffect.Deleted -> onDeleted()
        }
    }
    OverlayForm(
        title = stringResource(R.string.delete_title, state.label),
        icon = Icons.Rounded.Delete,
        onDismiss = onDismiss,
        description = stringResource(R.string.delete_description),
        confirmText = stringResource(R.string.delete_confirm),
        onConfirm = { viewModel.onSendIntent(DeleteIntent.Confirm) },
        destructive = true,
    ) {}
}
