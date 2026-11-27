/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.call.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bobbyesp.sona.calling.domain.CallQuality
import com.bobbyesp.sona.calling.ui.label
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme
import com.bobbyesp.sona.feature.call.R
import com.bobbyesp.sona.sip.api.CallStatistics
import com.bobbyesp.sona.sip.api.IceState
import com.bobbyesp.sona.sip.api.StreamStatistics

/**
 * The call's numbers, a second at a time: what the engine measures of each stream and how the call
 * travels, with the bitrate and the frame rate of the last minute drawn as sparklines. The host
 * puts it on a frosted surface; it is only composed while open, and only then are the numbers kept.
 */
@Composable
internal fun StatisticsPanel(
    statistics: CallStatistics?,
    history: StatsSeries,
    quality: CallQuality?,
    measured: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.call_statistics),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    quality?.label()
                        ?: stringResource(
                            if (measured) R.string.stats_waiting else R.string.stats_none,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.stats_close))
            }
        }
        if (statistics == null) return@Column
        statistics.video?.let { video ->
            Section(R.string.stats_video) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Spark(
                        title = R.string.stats_bitrate,
                        hint = R.string.stats_bitrate_hint,
                        first = history.up,
                        second = history.down,
                        modifier = Modifier.weight(1f),
                    )
                    Spark(
                        title = R.string.settings_video_frame_rate,
                        hint = R.string.stats_frame_rate_hint,
                        first = history.sentFps,
                        second = history.receivedFps,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Line(R.string.stats_codec, StatsFormat.codec(video.codec, video.clockRate))
                Line(
                    R.string.stats_sent,
                    StatsFormat.picture(video.frames?.sentSize, video.frames?.sentFps),
                )
                Line(
                    R.string.stats_received,
                    StatsFormat.picture(video.frames?.receivedSize, video.frames?.receivedFps),
                )
                StreamLines(video)
                Line(R.string.stats_jitter_buffer, StatsFormat.ms(video.jitterBufferMs))
                Line(R.string.stats_fec, StatsFormat.count(video.fecRepairedPackets))
                Line(R.string.stats_bandwidth, StatsFormat.kbps(video.estimatedDownloadKbps))
            }
        }
        statistics.audio?.let { audio ->
            Section(R.string.stats_audio) {
                if (statistics.video == null) {
                    Spark(
                        title = R.string.stats_bitrate,
                        hint = R.string.stats_bitrate_hint,
                        first = history.up,
                        second = history.down,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Line(R.string.stats_codec, StatsFormat.codec(audio.codec, audio.clockRate))
                StreamLines(audio)
                Line(R.string.stats_jitter_buffer, StatsFormat.ms(audio.jitterBufferMs))
            }
        }
        Section(R.string.stats_transport) {
            Line(R.string.stats_sip_transport, StatsFormat.transport(statistics.transport))
            if (statistics.ice != IceState.NotUsed) {
                Line(R.string.stats_ice, StatsFormat.ice(statistics.ice))
            }
            Line(R.string.stats_ip, StatsFormat.ipFamily(statistics.ipFamily))
            Line(
                R.string.stats_encryption,
                StatsFormat.encryption(statistics.encryption, statistics.srtpSuite)
                    ?: stringResource(R.string.stats_unencrypted),
            )
        }
    }
}

@Composable
private fun StreamLines(stream: StreamStatistics) {
    Line(R.string.stats_bitrate, StatsFormat.upDown(stream.uploadKbps, stream.downloadKbps))
    Line(
        R.string.stats_loss,
        StatsFormat.percentPair(stream.localLossPercent, stream.remoteLossPercent),
    )
    Line(R.string.stats_jitter, StatsFormat.ms(stream.jitterMs))
    Line(R.string.stats_round_trip, StatsFormat.ms(stream.roundTripMs))
}

@Composable
private fun Section(title: Int, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(20.dp))
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() }.padding(bottom = 8.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
}

/**
 * One number and what it is. The two wrap onto two lines rather than cut either at a large font: a
 * label never pushes its value out of sight.
 */
@Composable
private fun Line(label: Int, value: String) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = SonaTheme.typography.monospace,
            overflow = TextOverflow.Visible,
        )
    }
}

/** A sparkline of two series (up and down, sent and received), each in its own color. */
@Composable
private fun Spark(
    title: Int,
    hint: Int,
    first: FloatArray,
    second: FloatArray,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val hintText = stringResource(hint)
    Column(modifier) {
        Text(stringResource(title), style = MaterialTheme.typography.labelLarge)
        Sparkline(
            first = first,
            second = second,
            firstColor = colors.primary,
            secondColor = colors.tertiary,
            baseline = colors.outlineVariant,
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(vertical = 4.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Legend(colors.primary)
            Legend(colors.tertiary)
            Text(
                hintText,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Legend(color: Color) {
    Canvas(Modifier.padding(end = 4.dp).size(8.dp)) { drawCircle(color) }
    Spacer(Modifier.width(2.dp))
}

/**
 * The lines of [first] and [second] on one scale (their highest value at the top), the newest at
 * the right edge, a minute wide whatever is filled yet. Drawn, never composed per point.
 */
@Composable
internal fun Sparkline(
    first: FloatArray,
    second: FloatArray,
    firstColor: Color,
    secondColor: Color,
    baseline: Color,
    modifier: Modifier = Modifier,
    capacity: Int = StatsHistory.SECONDS,
) {
    Canvas(modifier) {
        val top = maxOf(first.maxOrNull() ?: 0f, second.maxOrNull() ?: 0f).coerceAtLeast(1f)
        val step = size.width / (capacity - 1).coerceAtLeast(1)
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawLine(
            baseline,
            Offset(0f, size.height),
            Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx(),
        )
        for ((series, color) in listOf(first to firstColor, second to secondColor)) {
            if (series.size < 2) continue
            val path = Path()
            val offset = capacity - series.size
            series.forEachIndexed { index, value ->
                val x = (offset + index) * step
                val y = size.height - (value / top) * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = stroke)
        }
    }
}
