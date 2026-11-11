/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.ui

import com.bobbyesp.sona.core.navigation.keys.AccountSwitcher
import com.bobbyesp.sona.core.ui.contribution.ReadinessCheck
import com.bobbyesp.sona.core.ui.contribution.ReadinessFix
import com.bobbyesp.sona.core.ui.contribution.ReadinessMonitor
import com.bobbyesp.sona.core.ui.contribution.ReadinessState
import com.bobbyesp.sona.core.ui.contribution.problems
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadinessMonitorTest {

    private class Check(
        override val id: String,
        override val order: Int,
        var state: ReadinessState,
        override val changes: Flow<*> = MutableSharedFlow<Unit>(),
    ) : ReadinessCheck {
        override val title = 0
        override val problem = 0
        override val fixLabel = 0
        override val fix = ReadinessFix.Open(AccountSwitcher)

        override suspend fun check() = state
    }

    @Test
    fun `the checks are answered at start, in their order, without those that do not apply`() =
        runTest {
            val monitor =
                ReadinessMonitor(
                    listOf(
                        Check("battery", 30, ReadinessState.Ready),
                        Check("full-screen", 20, ReadinessState.NotApplicable),
                        Check("microphone", 10, ReadinessState.Problem),
                    ),
                    backgroundScope,
                )
            assertNull(monitor.results.value)

            runCurrent()

            val results = monitor.results.value!!
            assertEquals(listOf("microphone", "battery"), results.map { it.check.id })
            assertEquals(listOf("microphone"), results.problems.map { it.check.id })
        }

    @Test
    fun `a refresh asks again, as when the app comes back from the system's settings`() = runTest {
        val microphone = Check("microphone", 10, ReadinessState.Problem)
        val monitor = ReadinessMonitor(listOf(microphone), backgroundScope)
        runCurrent()

        microphone.state = ReadinessState.Ready
        assertEquals(1, monitor.results.value!!.problems.size)
        monitor.refresh()
        runCurrent()

        assertEquals(0, monitor.results.value!!.problems.size)
    }

    @Test
    fun `a check that says it changed is asked again by itself`() = runTest {
        val changes = MutableSharedFlow<Unit>()
        val account = Check("account", 10, ReadinessState.Problem, changes)
        val monitor = ReadinessMonitor(listOf(account), backgroundScope)
        runCurrent()

        account.state = ReadinessState.Ready
        changes.emit(Unit)
        runCurrent()

        assertEquals(ReadinessState.Ready, monitor.results.value!!.single().state)
    }
}
