/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.accounts.domain

import com.bobbyesp.sona.core.model.settings.AudioCodec
import com.bobbyesp.sona.core.model.settings.DtmfMethod
import com.bobbyesp.sona.core.model.settings.Ranking
import com.bobbyesp.sona.sip.api.DtmfMode

/*
 * The options an account may override, in the engine's words. Here, below both users: the app's
 * values go through `EngineConfigBuilder`, an account's own through its `AccountSpec`, and the two
 * must mean the same.
 */

/** The codecs that are on, by their SDP names, first choice first. */
fun Ranking.toCodecMimes(): List<String> = enabled.mapNotNull { name ->
    AudioCodec.entries.firstOrNull { it.name == name }?.mime
}

fun DtmfMethod.toEngine(): DtmfMode =
    when (this) {
        DtmfMethod.Rfc4733 -> DtmfMode.Rfc4733
        DtmfMethod.SipInfo -> DtmfMode.SipInfo
        DtmfMethod.InBand -> DtmfMode.InBand
    }
