/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.diagnostics.trace

import com.bobbyesp.sona.core.model.settings.DiagnosticsSettings
import com.bobbyesp.sona.core.model.settings.SettingsStore
import com.bobbyesp.sona.core.ui.viewmodel.BaseViewModel
import com.bobbyesp.sona.sip.api.Capability
import com.bobbyesp.sona.sip.api.SipEngine
import com.bobbyesp.sona.sip.api.SipTraceEntry
import kotlinx.coroutines.flow.combine

internal data class TraceState(
    /** Whether this engine can show its messages at all. */
    val supported: Boolean,
    val capturing: Boolean = false,
    /** The newest first: what just happened is what is being looked for. */
    val messages: List<SipTraceEntry> = emptyList(),
)

internal sealed interface TraceIntent {
    data class Capture(val on: Boolean) : TraceIntent

    data object Clear : TraceIntent
}

/**
 * The SIP messages the engine sent and received, while the capture is on. Turning it on is a
 * setting like any other: it reaches the engine through the configuration, and stays on until
 * turned off.
 */
internal class TraceViewModel(
    private val engine: SipEngine,
    private val settings: SettingsStore,
) :
    BaseViewModel<TraceIntent, TraceState, Nothing>(
        TraceState(supported = Capability.SipTrace in engine.capabilities),
    ) {

    init {
        launch {
            combine(settings.observe(DiagnosticsSettings.TraceCapture), engine.trace) {
                    capturing,
                    messages ->
                    capturing to messages
                }
                .collect { (capturing, messages) ->
                    setState { copy(capturing = capturing, messages = messages.asReversed()) }
                }
        }
    }

    override fun onHandleIntent(intent: TraceIntent) {
        when (intent) {
            is TraceIntent.Capture ->
                launch { settings.set(DiagnosticsSettings.TraceCapture, intent.on) }
            TraceIntent.Clear -> launch { engine.clearTrace() }
        }
    }
}

/**
 * The trace as one text, oldest message first, each under a line that says when, which way and with
 * whom: what is copied or shared, to be read next to the server's own log.
 *
 * @param time How a moment is written (`12:01:02.123`).
 */
internal fun List<SipTraceEntry>.asText(time: (Long) -> String): String = sortedBy {
    it.id
}
    .joinToString("\n\n") { it.asText(time) }

internal fun SipTraceEntry.asText(time: (Long) -> String): String =
    "--- ${time(atMillis)} ${if (outgoing) "sent to" else "received from"} $peer ($transport)\n$raw"
