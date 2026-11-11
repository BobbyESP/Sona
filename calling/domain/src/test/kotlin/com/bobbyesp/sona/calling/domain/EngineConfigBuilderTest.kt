/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.calling.domain

import com.bobbyesp.sona.core.model.settings.AudioCodec
import com.bobbyesp.sona.core.model.settings.AudioSettings
import com.bobbyesp.sona.core.model.settings.CallSettings
import com.bobbyesp.sona.core.model.settings.DiagnosticsSettings
import com.bobbyesp.sona.core.model.settings.DtmfMethod
import com.bobbyesp.sona.core.model.settings.LogLevel
import com.bobbyesp.sona.core.model.settings.NetworkSettings
import com.bobbyesp.sona.core.testing.InMemorySettingsStore
import com.bobbyesp.sona.sip.api.DtmfMode
import com.bobbyesp.sona.sip.api.EngineConfig
import com.bobbyesp.sona.sip.api.EngineLogLevel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class EngineConfigBuilderTest {
    private val settings = InMemorySettingsStore()
    private val builder = EngineConfigBuilder(settings)

    @Test
    fun `untouched settings give the catalogue's defaults`() = runTest {
        assertEquals(
            EngineConfig(
                userAgent = "Sona",
                logLevel = EngineLogLevel.Warnings,
                codecs = listOf("opus", "G722", "PCMU", "PCMA"),
                dtmf = DtmfMode.Rfc4733,
                echoCancellation = true,
                wifiOnly = false,
                captureTrace = false,
            ),
            builder.config.first(),
        )
    }

    @Test
    fun `the codec order is the user's, without the codecs turned off`() = runTest {
        val codecs = AudioSettings.Codecs
        settings.set(
            codecs,
            codecs.default
                .moved(AudioCodec.G722.name, up = true)
                .switched(AudioCodec.Pcma.name, enabled = false)
                .switched(AudioCodec.Gsm.name, enabled = true),
        )

        assertEquals(listOf("G722", "opus", "PCMU", "GSM"), builder.config.first().codecs)
    }

    @Test
    fun `each option reaches its field`() = runTest {
        settings.set(CallSettings.Dtmf, DtmfMethod.SipInfo)
        settings.set(AudioSettings.EchoCancellation, false)
        settings.set(NetworkSettings.MobileData, false)
        settings.set(DiagnosticsSettings.Logging, LogLevel.Messages)
        settings.set(DiagnosticsSettings.TraceCapture, true)

        val config = builder.config.first()

        assertEquals(DtmfMode.SipInfo, config.dtmf)
        assertEquals(false, config.echoCancellation)
        assertEquals(true, config.wifiOnly)
        assertEquals(EngineLogLevel.Messages, config.logLevel)
        assertEquals(true, config.captureTrace)
    }

    @Test
    fun `a change gives a new configuration, and a setting the engine ignores gives none`() =
        runTest {
            val seen = mutableListOf<EngineConfig>()
            val collecting = launch { builder.config.toList(seen) }
            testScheduler.runCurrent()

            settings.set(CallSettings.CallWaiting, false)
            testScheduler.runCurrent()
            settings.set(CallSettings.Dtmf, DtmfMethod.InBand)
            testScheduler.runCurrent()
            collecting.cancel()

            assertEquals(listOf(DtmfMode.Rfc4733, DtmfMode.InBand), seen.map { it.dtmf })
        }
}
