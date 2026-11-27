/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.call.video

import androidx.compose.runtime.Immutable
import com.bobbyesp.sona.sip.api.CallStatistics
import com.bobbyesp.sona.sip.api.IceState
import com.bobbyesp.sona.sip.api.IpFamily
import com.bobbyesp.sona.sip.api.Transport
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The statistics panel's last minute, for its sparklines: the bitrate up and down (both streams)
 * and the frame rate sent and received, a sample a second.
 */
internal class StatsHistory(capacity: Int = SECONDS) {
    private val up = RingBuffer(capacity)
    private val down = RingBuffer(capacity)
    private val sentFps = RingBuffer(capacity)
    private val receivedFps = RingBuffer(capacity)

    fun add(statistics: CallStatistics) {
        val streams = listOfNotNull(statistics.audio, statistics.video)
        up.add(streams.sumOf { it.uploadKbps.toDouble() }.toFloat())
        down.add(streams.sumOf { it.downloadKbps.toDouble() }.toFloat())
        val frames = statistics.video?.frames
        sentFps.add(frames?.sentFps ?: 0f)
        receivedFps.add(frames?.receivedFps ?: 0f)
    }

    fun clear() {
        up.clear()
        down.clear()
        sentFps.clear()
        receivedFps.clear()
    }

    fun snapshot(): StatsSeries =
        StatsSeries(
            up = up.toFloatArray(),
            down = down.toFloatArray(),
            sentFps = sentFps.toFloatArray(),
            receivedFps = receivedFps.toFloatArray(),
        )

    companion object {
        /** A minute, at a sample a second. */
        const val SECONDS = 60
    }
}

/** A copy of [StatsHistory] at one moment, oldest first; never changed once made. */
@Immutable
internal class StatsSeries(
    val up: FloatArray = FloatArray(0),
    val down: FloatArray = FloatArray(0),
    val sentFps: FloatArray = FloatArray(0),
    val receivedFps: FloatArray = FloatArray(0),
)

/*
 * The panel's numbers, as text. Units and figures are the same in every language (`kbit/s`, `ms`,
 * `fps`), so they are formatted here and tested, with a fixed locale for the decimal point.
 */

internal object StatsFormat {
    /** `—`, for what is not measured yet. */
    const val NONE = "—"

    fun kbps(value: Float?): String =
        when {
            value == null -> NONE
            value >= 1000f -> String.format(Locale.ROOT, "%.1f Mbit/s", value / 1000f)
            else -> "${value.roundToInt()} kbit/s"
        }

    /** Up and down together: `512 ↑ · 1.2 Mbit/s ↓`. */
    fun upDown(up: Float?, down: Float?): String = "${kbps(up)} ↑ · ${kbps(down)} ↓"

    fun percent(value: Float?): String =
        if (value == null) NONE else String.format(Locale.ROOT, "%.1f %%", value)

    /** Here and at the other side: `0.4 % · 1.2 %`. */
    fun percentPair(local: Float?, remote: Float?): String =
        "${percent(local)} · ${percent(remote)}"

    fun ms(value: Float?): String = if (value == null) NONE else "${value.roundToInt()} ms"

    fun fps(value: Float?): String =
        if (value == null) NONE else String.format(Locale.ROOT, "%.0f fps", value)

    fun count(value: Long?): String = value?.toString() ?: NONE

    /** A codec and its clock rate: `opus · 48 kHz`, `VP8 · 90 kHz`. */
    fun codec(name: String?, clockRate: Int?): String {
        if (name == null) return NONE
        if (clockRate == null || clockRate <= 0) return name
        val khz = clockRate / 1000f
        val rate =
            if (khz == khz.roundToInt().toFloat()) "${khz.roundToInt()} kHz"
            else String.format(Locale.ROOT, "%.1f kHz", khz)
        return "$name · $rate"
    }

    /** Size and frame rate: `1280×720 · 30 fps`. */
    fun picture(size: Any?, fps: Float?): String =
        listOfNotNull(size?.toString(), fps?.takeIf { it > 0f }?.let(::fps))
            .joinToString(" · ")
            .ifEmpty { NONE }

    fun transport(value: Transport?): String =
        when (value) {
            null -> NONE
            Transport.Udp -> "UDP"
            Transport.Tcp -> "TCP"
            Transport.Tls -> "TLS"
        }

    fun ipFamily(value: IpFamily?): String =
        when (value) {
            null -> NONE
            IpFamily.V4 -> "IPv4"
            IpFamily.V6 -> "IPv6"
        }

    /** ICE's own words, as a SIP person reads them in a trace. */
    fun ice(value: IceState): String =
        when (value) {
            IceState.NotUsed -> NONE
            IceState.InProgress -> "checking"
            IceState.Failed -> "failed"
            IceState.Host -> "host"
            IceState.Reflexive -> "srflx"
            IceState.Relay -> "relay"
        }

    /** `SRTP · AES_CM_128_HMAC_SHA1_80`; `null` for media in clear (the panel says so in words). */
    fun encryption(name: String?, suite: String?): String? = name?.let {
        listOfNotNull(it, suite).joinToString(" · ")
    }
}
