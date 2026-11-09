/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.directory.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bobbyesp.sona.directory.domain.CallOutcome
import com.bobbyesp.sona.directory.domain.CallRecord
import com.bobbyesp.sona.sip.api.CallDirection
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A mark for someone: the first letter of their name on one of the theme's container colors, in one
 * of Material's expressive shapes. Both are chosen by the name, so the same person is always the
 * same cookie or clover in the same color, and a list of people is told apart at a glance.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val scheme = MaterialTheme.colorScheme
    val tones: List<Pair<Color, Color>> =
        listOf(
            scheme.primaryContainer to scheme.onPrimaryContainer,
            scheme.secondaryContainer to scheme.onSecondaryContainer,
            scheme.tertiaryContainer to scheme.onTertiaryContainer,
        )
    val hash = name.hashCode()
    val (container, content) = tones[Math.floorMod(hash, tones.size)]
    val shape = AvatarShapes[Math.floorMod(hash / 3, AvatarShapes.size)].toShape()
    Surface(modifier.size(size), shape = shape, color = container, contentColor = content) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text =
                    name
                        .firstOrNull { it.isLetterOrDigit() }
                        ?.uppercase()
                        .orEmpty()
                        .ifEmpty { "#" },
                style =
                    if (size >= 56.dp) MaterialTheme.typography.titleLarge
                    else MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val AvatarShapes =
    listOf(
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.Flower,
        MaterialShapes.Pentagon,
        MaterialShapes.Sunny,
        MaterialShapes.Puffy,
        MaterialShapes.Cookie6Sided,
    )

/** The arrow that says which way a call went; a missed one has its own. */
val CallRecord.icon: ImageVector
    get() =
        when {
            isMissed -> Icons.AutoMirrored.Rounded.CallMissed
            direction == CallDirection.Incoming -> Icons.AutoMirrored.Rounded.CallReceived
            else -> Icons.AutoMirrored.Rounded.CallMade
        }

/** How the call ended in a word, or its direction when it was answered: *Missed*, *Busy*… */
@Composable
fun CallRecord.outcomeText(): String =
    stringResource(
        when (outcome) {
            CallOutcome.Answered ->
                if (direction == CallDirection.Incoming) R.string.outcome_answered_incoming
                else R.string.outcome_answered_outgoing
            CallOutcome.Missed -> R.string.outcome_missed
            CallOutcome.Declined -> R.string.outcome_declined
            CallOutcome.Cancelled -> R.string.outcome_cancelled
            CallOutcome.Busy -> R.string.outcome_busy
            CallOutcome.NoAnswer -> R.string.outcome_no_answer
            CallOutcome.Unavailable -> R.string.outcome_unavailable
            CallOutcome.NotFound -> R.string.outcome_not_found
            CallOutcome.Failed -> R.string.outcome_failed
        },
    )

/** Whether the record is one the user would want to notice: missed, or one that failed. */
val CallRecord.isProblem: Boolean
    get() = isMissed || outcome == CallOutcome.Failed

/** Talk time as `m:ss`, or `h:mm:ss` from an hour. */
@Composable
fun formatDuration(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = total % 3600 / 60
    val seconds = total % 60
    return if (hours > 0) stringResource(R.string.record_duration_hms, hours, minutes, seconds)
    else stringResource(R.string.record_duration_ms, minutes, seconds)
}

/** The time of day for today, a word for yesterday, the date for anything older. */
@Composable
fun formatWhen(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val today = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone)
    return when (day.toLocalDate()) {
        today -> DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(day)
        today.minusDays(1) -> stringResource(R.string.record_yesterday)
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).format(day)
    }
}

/** The day heading Recents groups under: today, yesterday, or the date. */
@Composable
fun formatDayHeading(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val today = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone)
    return when (day.toLocalDate()) {
        today -> stringResource(R.string.record_today)
        today.minusDays(1) -> stringResource(R.string.record_yesterday)
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(day)
    }
}

/** Both the heading and the row use the local day, so grouping and display cannot disagree. */
fun localDay(epochMillis: Long): LocalDate =
    LocalDate.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
