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
 * Whether a video call has been on a *Poor* connection long enough to say so ("Weak connection:
 * video lowered"): [samples] in a row, a second each. One better sample starts the count again, so
 * a blip says nothing.
 */
internal class WeakConnectionWatch(private val samples: Int = WEAK_AFTER_SAMPLES) {
    private var poorInARow = 0

    /** Feeds one sample; `true` while the banner is due. */
    fun update(quality: CallQuality?, isVideo: Boolean): Boolean {
        poorInARow = if (isVideo && quality == CallQuality.Poor) poorInARow + 1 else 0
        return poorInARow >= samples
    }

    companion object {
        /** About five seconds of Poor. */
        const val WEAK_AFTER_SAMPLES = 5
    }
}

/**
 * Whether the other side's picture froze: it should arrive (the other side says it sends, and this
 * side takes it) but no frame came for [samples] seconds in a row. The screen dims the last frame
 * under "Video paused"; the first frame back lifts it.
 */
internal class FrozenVideoWatch(private val samples: Int = FROZEN_AFTER_SAMPLES) {
    private var stillInARow = 0

    /** Feeds one sample: whether a picture is expected, and the frames a second received. */
    fun update(expected: Boolean, receivedFps: Float?): Boolean {
        stillInARow = if (expected && (receivedFps ?: 0f) <= 0f) stillInARow + 1 else 0
        return stillInARow >= samples
    }

    companion object {
        /** Three seconds without a frame. */
        const val FROZEN_AFTER_SAMPLES = 3
    }
}

/**
 * Whether the other side just turned its camera on, between two looks at the same answered call:
 * its picture now arrives, it did not before, and this side's camera is off (so the screen offers
 * *Turn on camera*). A call answered with the caller's video shown is not this: it was never active
 * without it.
 */
internal fun remoteTurnedOnVideo(before: Call?, now: Call): Boolean =
    before != null &&
        before.id == now.id &&
        before.state == CallState.Active &&
        before.video?.showsRemote != true &&
        now.showsRemoteVideo &&
        !now.cameraOn

/**
 * The last [capacity] values, oldest first, in a fixed array: a sparkline's minute of history at a
 * sample a second, with nothing allocated per sample.
 */
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

    /** The [index]th oldest value. */
    operator fun get(index: Int): Float {
        require(index in 0 until size) { "$index outside 0 until $size" }
        return values[(start + index) % capacity]
    }

    /** A copy, oldest first: what a frame draws, while the buffer goes on filling. */
    fun toFloatArray(): FloatArray = FloatArray(size) { get(it) }

    fun clear() {
        start = 0
        size = 0
    }
}
