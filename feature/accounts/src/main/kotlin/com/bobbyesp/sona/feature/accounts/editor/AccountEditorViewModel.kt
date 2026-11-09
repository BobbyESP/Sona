/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.accounts.editor

import androidx.lifecycle.SavedStateHandle
import com.bobbyesp.sona.accounts.domain.AccountDraft
import com.bobbyesp.sona.accounts.domain.AccountStatus
import com.bobbyesp.sona.accounts.domain.AccountsRepository
import com.bobbyesp.sona.accounts.domain.DraftField
import com.bobbyesp.sona.accounts.domain.DraftProblem
import com.bobbyesp.sona.accounts.domain.SaveAccount
import com.bobbyesp.sona.accounts.domain.SaveOutcome
import com.bobbyesp.sona.accounts.domain.observeAccountStatuses
import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.Secret
import com.bobbyesp.sona.core.ui.viewmodel.BaseViewModel
import com.bobbyesp.sona.sip.api.RegistrationState
import com.bobbyesp.sona.sip.api.SipEngine
import com.bobbyesp.sona.sip.api.Transport
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

/** Where the editor is: filling the form, waiting for the server, or showing what it answered. */
internal sealed interface EditorPhase {
    data object Editing : EditorPhase

    data class Checking(val id: AccountId) : EditorPhase

    /** The server answered, or took too long ([timedOut]) and the engine keeps trying. */
    data class Checked(val id: AccountId, val timedOut: Boolean = false) : EditorPhase
}

internal data class EditorState(
    val isNew: Boolean,
    val loaded: Boolean = isNew,
    val draft: AccountDraft = AccountDraft(),
    val password: String = "",
    val passwordVisible: Boolean = false,
    val showMore: Boolean = false,
    val problems: Map<DraftField, DraftProblem> = emptyMap(),
    val passwordMissing: Boolean = false,
    val isDefault: Boolean = false,
    /** The account's live state, once it exists. */
    val status: AccountStatus? = null,
    val phase: EditorPhase = EditorPhase.Editing,
)

internal sealed interface EditorIntent {
    data class Edit(val change: AccountDraft.() -> AccountDraft) : EditorIntent

    data class Password(val value: String) : EditorIntent

    data object TogglePasswordVisible : EditorIntent

    data object ToggleMore : EditorIntent

    data class Default(val value: Boolean) : EditorIntent

    data object Save : EditorIntent

    /** Back from the server's answer to the form, to fix what it said. */
    data object EditAgain : EditorIntent

    data object Done : EditorIntent

    /** Registers again now, after what kept it from working was fixed (a permission granted). */
    data object Retry : EditorIntent

    data object Delete : EditorIntent
}

internal sealed interface EditorEffect {
    /** Leaves the editor, and the first-start flow it may be part of. */
    data object Leave : EditorEffect

    data class ConfirmDelete(val id: AccountId) : EditorEffect
}

/**
 * Adding or editing an account. Saving checks it: the account is saved, the engine registers it (it
 * follows the stored accounts), and the editor waits up to [CheckTimeout] for the server's answer,
 * which it shows in words. A failed account stays saved and the engine keeps trying; the user
 * decides whether to fix it now.
 *
 * The draft survives process death; the password does not, on purpose: it is never written anywhere
 * but the vault.
 */
internal class AccountEditorViewModel(
    accountId: String?,
    private val savedState: SavedStateHandle,
    private val repository: AccountsRepository,
    private val saveAccount: SaveAccount,
    private val engine: SipEngine,
) :
    BaseViewModel<EditorIntent, EditorState, EditorEffect>(
        EditorState(isNew = accountId == null),
    ) {

    private val existingId = accountId?.let(::AccountId)
    private var statusJob: Job? = null

    init {
        val restored = savedState.get<HashMap<String, String>>(DRAFT_KEY)?.toDraft()
        launch {
            val account = existingId?.let { repository.get(it) }
            setState {
                copy(
                    loaded = true,
                    draft = restored ?: account?.let(AccountDraft::of) ?: draft,
                    isDefault = account?.isDefault ?: false,
                    showMore = restored != null && savedState.get<Boolean>(MORE_KEY) == true,
                )
            }
        }
        existingId?.let(::followStatus)
    }

    override fun onHandleIntent(intent: EditorIntent) {
        when (intent) {
            is EditorIntent.Edit -> {
                val draft = currentState.draft.(intent.change)()
                setState { copy(draft = draft, problems = emptyMap()) }
                savedState[DRAFT_KEY] = draft.toMap()
            }
            is EditorIntent.Password ->
                setState { copy(password = intent.value, passwordMissing = false) }
            EditorIntent.TogglePasswordVisible ->
                setState { copy(passwordVisible = !passwordVisible) }
            EditorIntent.ToggleMore -> {
                setState { copy(showMore = !showMore) }
                savedState[MORE_KEY] = currentState.showMore
            }
            is EditorIntent.Default -> setState { copy(isDefault = intent.value) }
            EditorIntent.Save -> save()
            EditorIntent.EditAgain -> setState { copy(phase = EditorPhase.Editing) }
            EditorIntent.Done -> sendEffect(EditorEffect.Leave)
            EditorIntent.Retry -> retry()
            EditorIntent.Delete -> existingId?.let { sendEffect(EditorEffect.ConfirmDelete(it)) }
        }
    }

    private fun save() {
        if (currentState.phase !is EditorPhase.Editing) return
        launch {
            val state = currentState
            val password = state.password.takeIf { it.isNotEmpty() }?.let(::Secret)
            when (val outcome = saveAccount(state.draft, password)) {
                is SaveOutcome.Invalid ->
                    setState {
                        copy(
                            problems = outcome.problems,
                            showMore = showMore || outcome.needsMore(),
                        )
                    }
                SaveOutcome.PasswordRequired -> setState { copy(passwordMissing = true) }
                is SaveOutcome.Saved -> {
                    val id = outcome.account.id
                    if (state.isDefault && !outcome.account.isDefault) repository.setDefault(id)
                    setState {
                        copy(
                            draft = AccountDraft.of(outcome.account),
                            password = "",
                            phase = EditorPhase.Checking(id),
                        )
                    }
                    savedState[DRAFT_KEY] = AccountDraft.of(outcome.account).toMap()
                    followStatus(id)
                    awaitAnswer(id, enabled = outcome.account.enabled)
                }
            }
        }
    }

    private fun retry() {
        val id = (currentState.phase as? EditorPhase.Checked)?.id ?: existingId ?: return
        setState { copy(phase = EditorPhase.Checking(id)) }
        launch {
            engine.refreshRegistrations()
            // The refresh starts from Registering: wait for the answer after it, not the old one.
            engine.registrations.first {
                it[id] == RegistrationState.Registering || it[id].isAnswer()
            }
            awaitAnswer(id, enabled = true)
        }
    }

    private suspend fun awaitAnswer(id: AccountId, enabled: Boolean) {
        // The answer to *this* save: an edited account first goes back to Registering, and what it
        // was before (Registered, with the old password) is no answer. A save that changed nothing
        // the engine uses never re-registers, so that wait is short.
        if (enabled) {
            withTimeoutOrNull(RestartWindow) {
                engine.registrations.first { it[id] == RegistrationState.Registering }
            }
        }
        val answered =
            !enabled ||
                withTimeoutOrNull(CheckTimeout) {
                    engine.registrations.first { it[id].isAnswer() }
                } != null
        setState { copy(phase = EditorPhase.Checked(id, timedOut = !answered)) }
    }

    private fun followStatus(id: AccountId) {
        statusJob?.cancel()
        statusJob = launch {
            observeAccountStatuses(repository, engine)
                .map { all -> all.firstOrNull { it.account.id == id } }
                .collect { status -> setState { copy(status = status) } }
        }
    }

    private companion object {
        const val DRAFT_KEY = "draft"
        const val MORE_KEY = "more"
        val CheckTimeout = 15.seconds
        val RestartWindow = 2.seconds
    }
}

/** A registration that is an answer from the server, or the lack of a network. */
private fun RegistrationState?.isAnswer(): Boolean =
    this == RegistrationState.Registered ||
        this is RegistrationState.Failed ||
        this == RegistrationState.Offline

/** Problems in fields under *More options* open them, so the user sees what is wrong. */
private fun SaveOutcome.Invalid.needsMore(): Boolean =
    problems.keys.any { it != DraftField.Server && it != DraftField.Username }

private fun AccountDraft.toMap(): HashMap<String, String> =
    hashMapOf(
        "id" to (id?.value ?: ""),
        "server" to server,
        "username" to username,
        "label" to label,
        "transport" to transport.name,
        "port" to port,
        "domain" to domain,
        "displayName" to displayName,
        "authUsername" to authUsername,
        "outboundProxy" to outboundProxy,
        "expirySeconds" to expirySeconds,
        "voicemailNumber" to voicemailNumber,
        "color" to (color?.toString() ?: ""),
        "enabled" to enabled.toString(),
    )

private fun Map<String, String>.toDraft(): AccountDraft =
    AccountDraft(
        id = this["id"]?.takeIf { it.isNotEmpty() }?.let(::AccountId),
        server = this["server"].orEmpty(),
        username = this["username"].orEmpty(),
        label = this["label"].orEmpty(),
        transport = Transport.entries.firstOrNull { it.name == this["transport"] } ?: Transport.Udp,
        port = this["port"].orEmpty(),
        domain = this["domain"].orEmpty(),
        displayName = this["displayName"].orEmpty(),
        authUsername = this["authUsername"].orEmpty(),
        outboundProxy = this["outboundProxy"].orEmpty(),
        expirySeconds = this["expirySeconds"].orEmpty(),
        voicemailNumber = this["voicemailNumber"].orEmpty(),
        color = this["color"]?.toIntOrNull(),
        enabled = this["enabled"] != "false",
    )
