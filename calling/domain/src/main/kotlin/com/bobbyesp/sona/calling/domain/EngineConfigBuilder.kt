/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.calling.domain

import com.bobbyesp.sona.accounts.domain.toCodecMimes
import com.bobbyesp.sona.accounts.domain.toEngine
import com.bobbyesp.sona.core.model.settings.AudioSettings
import com.bobbyesp.sona.core.model.settings.CallSettings
import com.bobbyesp.sona.core.model.settings.DiagnosticsSettings
import com.bobbyesp.sona.core.model.settings.LogLevel
import com.bobbyesp.sona.core.model.settings.NetworkSettings
import com.bobbyesp.sona.core.model.settings.SettingsStore
import com.bobbyesp.sona.sip.api.EngineConfig
import com.bobbyesp.sona.sip.api.EngineLogLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The user's settings as the engine takes them. The one place that says which option becomes which
 * field of [EngineConfig]: an option the engine must know is mapped here, and nowhere else reads it
 * for the engine. What an account overrides travels with the account instead (`AccountSpec`),
 * through the same two translations.
 */
class EngineConfigBuilder(
    private val settings: SettingsStore,
    private val userAgent: String = "Sona",
) {
    /** The configuration as the settings are now, then again at every change of one of them. */
    val config: Flow<EngineConfig> =
        combine(
                settings.observe(AudioSettings.Codecs),
                settings.observe(CallSettings.Dtmf),
                settings.observe(AudioSettings.EchoCancellation),
                settings.observe(NetworkSettings.MobileData),
                settings.observe(DiagnosticsSettings.Logging),
            ) { codecs, dtmf, echo, mobileData, logging ->
                EngineConfig(
                    userAgent = userAgent,
                    logLevel = logging.toEngine(),
                    codecs = codecs.toCodecMimes(),
                    dtmf = dtmf.toEngine(),
                    echoCancellation = echo,
                    wifiOnly = !mobileData,
                )
            }
            .combine(settings.observe(DiagnosticsSettings.TraceCapture)) { config, trace ->
                config.copy(captureTrace = trace)
            }
            .distinctUntilChanged()
}

private fun LogLevel.toEngine(): EngineLogLevel =
    when (this) {
        LogLevel.Errors -> EngineLogLevel.Errors
        LogLevel.Warnings -> EngineLogLevel.Warnings
        LogLevel.Messages -> EngineLogLevel.Messages
        LogLevel.Debug -> EngineLogLevel.Debug
    }
