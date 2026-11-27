/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.calling.domain

import com.bobbyesp.sona.sip.api.CallStatistics
import com.bobbyesp.sona.sip.api.StreamStatistics

/**
 * How well a call gets through, for the quality indicator's bars: the worst of its loss, jitter and
 * round trip, each graded on the limits below.
 *
 * - **Loss** counts on both streams and both ends (the share lost here, and the share the other
 *   side reports lost of what is sent): the voice breaks up, the picture smears.
 * - **Jitter** is the voice's only. A video frame leaves as a burst of packets, so the video's
 *   jitter is high on any network and says nothing about it.
 * - **Round trip** is the path's; past 300 ms people start talking over each other (ITU-T G.114
 *   puts one way at 150 ms).
 *
 * The badge shows the grade as it is; holding on to *Poor* for several seconds before saying
 * something (the weak connection banner) is the screen's.
 */
enum class CallQuality {
    Good,
    Fair,
    Poor;

    companion object {
        /** Loss, in percent, below which a call is [Good], then [Fair]. */
        const val GOOD_LOSS = 2f
        const val FAIR_LOSS = 6f

        /** The voice's jitter, in milliseconds, below which a call is [Good], then [Fair]. */
        const val GOOD_JITTER_MS = 30f
        const val FAIR_JITTER_MS = 60f

        /** The round trip, in milliseconds, below which a call is [Good], then [Fair]. */
        const val GOOD_ROUND_TRIP_MS = 300f
        const val FAIR_ROUND_TRIP_MS = 600f

        /**
         * The grade of [statistics], or `null` while nothing was measured yet (ringing, or the
         * first packets not counted).
         */
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
