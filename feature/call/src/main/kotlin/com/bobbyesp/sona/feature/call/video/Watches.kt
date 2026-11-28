/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.call.video

import com.bobbyesp.sona.calling.domain.Call
import com.bobbyesp.sona.calling.domain.CallQuality
import com.bobbyesp.sona.calling.domain.CallState

/*
 * What the call screen reads out of the statistics, a sample a second: each a small machine fed one
 * sample at a time, so a test plays it out sample by sample, without a clock.
 */

/**
 * Whether a video call has been *Poor* for [samples] seconds in a row. A better sample starts over,
 * so a blip says nothing.
 */
internal class WeakConnectionWatch(private val samples: Int = WEAK_AFTER_SAMPLES) {
    private var poorInARow = 0

    /** Feeds one sample; `true` while the banner is due. */
    fun update(quality: CallQuality?, isVideo: Boolean): Boolean {
        poorInARow = if (isVideo && quality == CallQuality.Poor) poorInARow + 1 else 0
        return poorInARow >= samples
    }

    companion object {
        const val WEAK_AFTER_SAMPLES = 5
    }
}

/**
 * Whether the other side's picture froze: expected, but no frame for [samples] seconds in a row.
 */
internal class FrozenVideoWatch(private val samples: Int = FROZEN_AFTER_SAMPLES) {
    private var stillInARow = 0

    fun update(expected: Boolean, receivedFps: Float?): Boolean {
        stillInARow = if (expected && (receivedFps ?: 0f) <= 0f) stillInARow + 1 else 0
        return stillInARow >= samples
    }

    companion object {
        const val FROZEN_AFTER_SAMPLES = 3
    }
}

/**
 * Whether the other side just turned its camera on during an answered call, while this side's is
 * off: the screen then offers *Turn on camera*. Answering a video offer does not count.
 */
internal fun remoteTurnedOnVideo(before: Call?, now: Call): Boolean =
    before != null &&
        before.id == now.id &&
        before.state == CallState.Active &&
        before.video?.showsRemote != true &&
        now.showsRemoteVideo &&
        !now.cameraOn

/** The last [capacity] values, oldest first, with nothing allocated per sample. */
internal class RingBuffer(val capacity: Int) {
    private val values = FloatArray(capacity)
    private var start = 0

    var size: Int = 0
        private set

    fun add(value: Float) {
        if (size < capacity) {
            values[(start + size) % capacity] = value
            size++
        } else {
            values[start] = value
            start = (start + 1) % capacity
        }
    }

    operator fun get(index: Int): Float {
        require(index in 0 until size) { "$index outside 0 until $size" }
        return values[(start + index) % capacity]
    }

    /** A copy to draw, while the buffer goes on filling. */
    fun toFloatArray(): FloatArray = FloatArray(size) { get(it) }

    fun clear() {
        start = 0
        size = 0
    }
}
