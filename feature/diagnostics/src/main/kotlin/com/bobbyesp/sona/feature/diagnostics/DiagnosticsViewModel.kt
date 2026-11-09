/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.diagnostics

import com.bobbyesp.sona.accounts.domain.AccountStatus
import com.bobbyesp.sona.accounts.domain.AccountsRepository
import com.bobbyesp.sona.accounts.domain.observeAccountStatuses
import com.bobbyesp.sona.core.ui.viewmodel.BaseViewModel
import com.bobbyesp.sona.sip.api.Capability
import com.bobbyesp.sona.sip.api.EngineState
import com.bobbyesp.sona.sip.api.SipEngine
import kotlinx.coroutines.flow.combine

internal data class DiagnosticsState(
    val loaded: Boolean = false,
    val engine: EngineState = EngineState.Stopped,
    val capabilities: Set<Capability> = emptySet(),
    val accounts: List<AccountStatus> = emptyList(),
)

internal sealed interface DiagnosticsIntent

/** What Sona sees of its servers right now: the engine, and each account's registration. */
internal class DiagnosticsViewModel(
    accounts: AccountsRepository,
    private val engine: SipEngine,
) : BaseViewModel<DiagnosticsIntent, DiagnosticsState, Nothing>(DiagnosticsState()) {

    init {
        launch {
            combine(engine.state, observeAccountStatuses(accounts, engine)) { state, statuses ->
                    DiagnosticsState(
                        loaded = true,
                        engine = state,
                        capabilities = engine.capabilities,
                        accounts = statuses,
                    )
                }
                .collect { next -> setState { next } }
        }
    }

    override fun onHandleIntent(intent: DiagnosticsIntent) = Unit
}
