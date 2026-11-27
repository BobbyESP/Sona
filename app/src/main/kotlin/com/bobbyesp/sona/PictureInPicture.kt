/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona

import com.bobbyesp.sona.calling.domain.Call
import com.bobbyesp.sona.calling.domain.CallState
import com.bobbyesp.sona.core.model.CallId
import com.bobbyesp.sona.sip.api.VideoSize

/**
 * The call Picture-in-Picture would show, and what its window needs: an answered call with video on
 * screen. `null` when there is none, and then the call screen never goes to Picture-in-Picture.
 */
internal data class PipTarget(val call: CallId, val aspect: PipAspect, val muted: Boolean)

/** A width to height ratio, as Android's `Rational` takes it. */
internal data class PipAspect(val width: Int, val height: Int)

internal fun pipTarget(calls: List<Call>): PipTarget? {
    val call =
        calls.firstOrNull { it.isVideo && it.state == CallState.Active && it.showsRemoteVideo }
            ?: calls.firstOrNull { it.isVideo && it.state == CallState.Active }
            ?: return null
    return PipTarget(call.id, pipAspect(call.video?.receivedSize), call.muted)
}

/**
 * The window's shape for the other side's picture: its own, within what Android accepts (from
 * 1:2.39 to 2.39:1, the limits `setAspectRatio` throws beyond); upright 9:16 while the size is not
 * known yet, the way a phone sends.
 */
internal fun pipAspect(size: VideoSize?): PipAspect {
    if (size == null || size.width <= 0 || size.height <= 0) return PipAspect(9, 16)
    val ratio = size.width.toFloat() / size.height
    return when {
        ratio > MAX_RATIO -> PipAspect(239, 100)
        ratio < 1f / MAX_RATIO -> PipAspect(100, 239)
        else -> PipAspect(size.width, size.height)
    }
}

/** Android's widest Picture-in-Picture: 2.39:1, either way up. */
private const val MAX_RATIO = 2.39f
