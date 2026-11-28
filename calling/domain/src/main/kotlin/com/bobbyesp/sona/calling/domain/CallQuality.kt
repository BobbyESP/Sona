/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.calling.domain

import com.bobbyesp.sona.sip.api.CallStatistics
import com.bobbyesp.sona.sip.api.StreamStatistics

/**
 * How well a call gets through: the worst grade of its loss, jitter and round trip.
 *
 * - Loss counts on both streams and both ends.
 * - Jitter counts for the voice only: video leaves in bursts, so its jitter is high on any network.
 * - Past a 300 ms round trip, people talk over each other (ITU-T G.114).
 */
enum class CallQuality {
    Good,
    Fair,
    Poor;

    companion object {
        /** In percent. */
        const val GOOD_LOSS = 2f
        const val FAIR_LOSS = 6f

        const val GOOD_JITTER_MS = 30f
        const val FAIR_JITTER_MS = 60f

        const val GOOD_ROUND_TRIP_MS = 300f
        const val FAIR_ROUND_TRIP_MS = 600f

        /** The grade of [statistics], or `null` while nothing was measured yet. */
        fun of(statistics: CallStatistics): CallQuality? {
            val streams = listOfNotNull(statistics.audio, statistics.video).filter { it.flowing }
            if (streams.isEmpty()) return null
            val loss = streams.maxOf { maxOf(it.localLossPercent, it.remoteLossPercent) }
            val jitter = statistics.audio?.takeIf { it.flowing }?.jitterMs
            val roundTrip =
                streams.mapNotNull { it.roundTripMs?.takeIf { ms -> ms > 0f } }.maxOrNull()
            return listOfNotNull(
                    grade(loss, GOOD_LOSS, FAIR_LOSS),
                    jitter?.let { grade(it, GOOD_JITTER_MS, FAIR_JITTER_MS) },
                    roundTrip?.let { grade(it, GOOD_ROUND_TRIP_MS, FAIR_ROUND_TRIP_MS) },
                )
                .max()
        }

        private fun grade(value: Float, good: Float, fair: Float): CallQuality =
            when {
                value < good -> Good
                value < fair -> Fair
                else -> Poor
            }

        private val StreamStatistics.flowing: Boolean
            get() = packetsSent > 0 || packetsReceived > 0
    }
}
