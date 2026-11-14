/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.sip.linphone

import com.bobbyesp.sona.sip.api.DtmfMode
import com.bobbyesp.sona.sip.api.EngineConfig
import com.bobbyesp.sona.sip.api.EngineLogLevel
import org.linphone.core.Core
import org.linphone.core.Factory
import org.linphone.core.LogLevel

/**
 * Puts an [EngineConfig] into Linphone: the one place that knows which Linphone switch each of its
 * fields is. Called on the core thread, at the start and at every change of the user's settings.
 * What is decided here (which codecs, in which order) is a pure function of `LinphoneMapper`,
 * tested on the JVM.
 */
internal class LinphoneConfigurer(private val appVersion: String) {

    /** The part that needs no Core, so the Core's own first lines are logged at the right level. */
    fun logging(config: EngineConfig) {
        val factory = Factory.instance()
        factory.setDebugMode(config.logLevel >= EngineLogLevel.Messages, LOG_TAG)
        // The trace is read from the log, where SIP messages are written at the Message level:
        // capturing it needs that much logged, whatever the user asked to see.
        val level =
            if (config.captureTrace) maxOf(config.logLevel, EngineLogLevel.Messages)
            else config.logLevel
        factory.loggingService.setLogLevel(level.toLinphone())
    }

    fun apply(core: Core, config: EngineConfig) {
        logging(config)
        core.setUserAgent(config.userAgent, appVersion)

        core.isEchoCancellationEnabled = config.echoCancellation
        core.isWifiOnlyEnabled = config.wifiOnly
        dtmf(core, config.dtmf)
        config.codecs?.let { codecs(core, it) }
    }

    /**
     * How digits are sent from now on. Linphone has one answer for the whole Core, so the engine
     * sets it again for the account of the call a digit is for.
     */
    fun dtmf(core: Core, mode: DtmfMode) {
        // In-band is what Linphone does when neither signalling way is on: the tone is played into
        // the audio it sends.
        core.useRfc2833ForDtmf = mode == DtmfMode.Rfc4733
        core.useInfoForDtmf = mode == DtmfMode.SipInfo
    }

    /**
     * The codecs the next offer or answer is built from: [wanted] in their order, the rest off. One
     * list for the whole Core too, set again before each call for its account.
     */
    fun codecs(core: Core, wanted: List<String>) {
        val plan = codecPlan(core.audioPayloadTypes.toList(), wanted) { it.mimeType }
        plan.forEach { (payload, enabled) -> payload.enable(enabled) }
        // The order of this list is the order of the SDP offer.
        core.setAudioPayloadTypes(plan.map { it.first }.toTypedArray())
    }

    private fun EngineLogLevel.toLinphone(): LogLevel =
        when (this) {
            EngineLogLevel.Errors -> LogLevel.Error
            EngineLogLevel.Warnings -> LogLevel.Warning
            EngineLogLevel.Messages -> LogLevel.Message
            EngineLogLevel.Debug -> LogLevel.Debug
        }

    private companion object {
        const val LOG_TAG = "SonaSip"
    }
}
