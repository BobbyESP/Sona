/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.diagnostics

import com.bobbyesp.sona.calling.domain.EngineConfigBuilder
import com.bobbyesp.sona.core.model.settings.DiagnosticsSettings
import com.bobbyesp.sona.core.testing.InMemorySettingsStore
import com.bobbyesp.sona.core.testing.MainDispatcherRule
import com.bobbyesp.sona.feature.diagnostics.trace.TraceIntent
import com.bobbyesp.sona.feature.diagnostics.trace.TraceViewModel
import com.bobbyesp.sona.feature.diagnostics.trace.asText
import com.bobbyesp.sona.sip.fake.FakeSipEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TraceViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val settings = InMemorySettingsStore()

    /** The engine, following the settings as the app makes it. */
    private suspend fun TestScope.engine(): FakeSipEngine {
        val engine = FakeSipEngine(this)
        val config = EngineConfigBuilder(settings).config
        engine.start(config.first())
        backgroundScope.launch { config.collect { engine.apply(it) } }
        return engine
    }

    /**
     * Until nothing is left to do, the engine following the settings included: that runs in the
     * background scope, which `advanceUntilIdle` alone does not wait for.
     */
    private fun TestScope.settle() {
        advanceUntilIdle()
        testScheduler.runCurrent()
        advanceUntilIdle()
    }

    @Test
    fun `nothing is kept until the capture is turned on, then the newest message is first`() =
        runTest(main.dispatcher) {
            val engine = engine()
            val trace = TraceViewModel(engine, settings)
            settle()
            assertTrue(trace.state.value.supported)
            assertFalse(trace.state.value.capturing)

            engine.traced(true, "OPTIONS sip:lab.local SIP/2.0")
            settle()
            assertTrue(trace.state.value.messages.isEmpty())

            trace.onSendIntent(TraceIntent.Capture(true))
            settle()
            engine.traced(true, "REGISTER sip:lab.local SIP/2.0", "CSeq: 1 REGISTER")
            engine.traced(false, "SIP/2.0 401 Unauthorized", "CSeq: 1 REGISTER")
            settle()

            assertTrue(trace.state.value.capturing)
            assertEquals(true, settings.observe(DiagnosticsSettings.TraceCapture).first())
            assertEquals(
                listOf("401 Unauthorized", "REGISTER"),
                trace.state.value.messages.map { it.summary },
            )
            assertEquals("REGISTER", trace.state.value.messages.first().method)
        }

    @Test
    fun `clearing empties the trace, and the capture goes on`() =
        runTest(main.dispatcher) {
            val engine = engine()
            val trace = TraceViewModel(engine, settings)
            trace.onSendIntent(TraceIntent.Capture(true))
            settle()
            engine.traced(true, "REGISTER sip:lab.local SIP/2.0")

            trace.onSendIntent(TraceIntent.Clear)
            settle()
            assertTrue(trace.state.value.messages.isEmpty())

            engine.traced(false, "SIP/2.0 200 OK")
            settle()
            assertEquals(1, trace.state.value.messages.size)
        }

    @Test
    fun `the shared text is in the order things happened, each message under who and when`() =
        runTest(main.dispatcher) {
            val engine = engine()
            val trace = TraceViewModel(engine, settings)
            trace.onSendIntent(TraceIntent.Capture(true))
            settle()
            engine.traced(true, "REGISTER sip:lab.local SIP/2.0", "CSeq: 1 REGISTER")
            engine.traced(false, "SIP/2.0 200 OK", "CSeq: 1 REGISTER")
            settle()

            assertEquals(
                """
                --- t0 sent to lab.local:5060 (UDP)
                REGISTER sip:lab.local SIP/2.0
                CSeq: 1 REGISTER

                --- t0 received from lab.local:5060 (UDP)
                SIP/2.0 200 OK
                CSeq: 1 REGISTER
                """
                    .trimIndent(),
                trace.state.value.messages.asText { "t$it" },
            )
        }
}
