/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.accounts

import androidx.lifecycle.SavedStateHandle
import com.bobbyesp.sona.accounts.domain.AccountDraft
import com.bobbyesp.sona.accounts.domain.AccountEngineSync
import com.bobbyesp.sona.accounts.domain.DraftField
import com.bobbyesp.sona.accounts.domain.DraftProblem
import com.bobbyesp.sona.accounts.domain.InMemoryAccountsRepository
import com.bobbyesp.sona.accounts.domain.SaveAccount
import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.testing.MainDispatcherRule
import com.bobbyesp.sona.credentials.api.InMemoryCredentialVault
import com.bobbyesp.sona.feature.accounts.editor.AccountEditorViewModel
import com.bobbyesp.sona.feature.accounts.editor.EditorIntent
import com.bobbyesp.sona.feature.accounts.editor.EditorPhase
import com.bobbyesp.sona.sip.api.EngineConfig
import com.bobbyesp.sona.sip.api.RegistrationState
import com.bobbyesp.sona.sip.api.SipStatus
import com.bobbyesp.sona.sip.fake.FakeSipEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountEditorViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val repository = InMemoryAccountsRepository()
    private val vault = InMemoryCredentialVault()
    private val save = SaveAccount(repository, vault) { AccountId("lab") }

    private fun TestScope.engine(): FakeSipEngine {
        val engine = FakeSipEngine(this)
        launch { engine.start(EngineConfig()) }
        backgroundScope.launch { AccountEngineSync(repository, vault, engine).run() }
        return engine
    }

    private fun editor(engine: FakeSipEngine, id: String? = null) =
        AccountEditorViewModel(id, SavedStateHandle(), repository, save, engine)

    private fun AccountEditorViewModel.fill(server: String, user: String, password: String) {
        onSendIntent(EditorIntent.Edit { copy(server = server, username = user) })
        onSendIntent(EditorIntent.Password(password))
    }

    @Test
    fun `checking a new account waits for the server and reports it registered`() =
        runTest(main.dispatcher) {
            val engine = engine()
            val editor = editor(engine)
            editor.fill("lab.local", "102", "secret")

            editor.onSendIntent(EditorIntent.Save)
            advanceUntilIdle()

            val state = editor.state.value
            assertEquals(EditorPhase.Checked(AccountId("lab")), state.phase)
            assertEquals(RegistrationState.Registered, state.status?.registration)
        }

    /** The bug found on a device: the old "Registered" was taken for the new answer. */
    @Test
    fun `an edit that breaks a working account reports the new failure, not the old success`() =
        runTest(main.dispatcher) {
            val engine = engine()
            editor(engine).run {
                fill("lab.local", "102", "secret")
                onSendIntent(EditorIntent.Save)
            }
            advanceUntilIdle()
            engine.failRegistration(AccountId("lab"), null)
            engine.acceptRegistration(AccountId("lab"))
            advanceUntilIdle()

            val editor = editor(engine, id = "lab")
            advanceUntilIdle()
            engine.failRegistration(AccountId("lab"), SipStatus(401, "Unauthorized"))
            editor.onSendIntent(EditorIntent.Password("wrong"))
            editor.onSendIntent(EditorIntent.Save)
            advanceUntilIdle()

            assertEquals(
                RegistrationState.Failed(SipStatus(401, "Unauthorized")),
                editor.state.value.status?.registration,
            )
            assertTrue(editor.state.value.phase is EditorPhase.Checked)
        }

    @Test
    fun `an invalid draft shows its problems and saves nothing`() =
        runTest(main.dispatcher) {
            val engine = engine()
            val editor = editor(engine)
            editor.fill("lab local", "", "secret")

            editor.onSendIntent(EditorIntent.Save)
            advanceUntilIdle()

            assertEquals(
                mapOf(
                    DraftField.Server to DraftProblem.NotAHost,
                    DraftField.Username to DraftProblem.Required,
                ),
                editor.state.value.problems,
            )
            assertTrue(repository.accounts.first().isEmpty())
        }

    @Test
    fun `a new account without a password asks for it`() =
        runTest(main.dispatcher) {
            val editor = editor(engine())
            editor.fill("lab.local", "102", "")

            editor.onSendIntent(EditorIntent.Save)
            advanceUntilIdle()

            assertTrue(editor.state.value.passwordMissing)
            assertEquals(EditorPhase.Editing, editor.state.value.phase)
        }

    @Test
    fun `the draft survives process death, the password does not`() =
        runTest(main.dispatcher) {
            val saved = SavedStateHandle()
            val engine = engine()
            AccountEditorViewModel(null, saved, repository, save, engine)
                .fill("lab.local", "102", "secret")

            val restored =
                AccountEditorViewModel(
                    null,
                    SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) }),
                    repository,
                    save,
                    engine,
                )
            advanceUntilIdle()

            assertEquals(
                AccountDraft(server = "lab.local", username = "102"),
                restored.state.value.draft,
            )
            assertEquals("", restored.state.value.password)
        }
}
