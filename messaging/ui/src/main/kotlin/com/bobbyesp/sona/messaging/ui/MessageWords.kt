/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.ui

import androidx.compose.runtime.Composable
import com.bobbyesp.sona.messaging.domain.Message
import com.bobbyesp.sona.messaging.domain.MessageStatus
import com.bobbyesp.sona.messaging.domain.TransferState
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

val MessageStatus.label: Sentence
    get() =
        Sentence(
            when (this) {
                MessageStatus.Sending -> R.string.status_sending
                MessageStatus.Sent -> R.string.status_sent
                MessageStatus.Delivered -> R.string.status_delivered
                MessageStatus.Read -> R.string.status_read
                MessageStatus.Failed -> R.string.status_failed
                MessageStatus.Received -> R.string.status_received
            },
        )

/**
 * The line about a file's transfer; `null` without a file, for one that is simply there, and for a
 * failure, which has its own sentence.
 *
 * @param peer How the other side is named.
 */
fun Message.transferLine(peer: String): Sentence? {
    val file = attachment ?: return null
    val percent = (file.progress * 100).roundToInt()
    return when (file.state) {
        TransferState.Offering -> Sentence(R.string.transfer_offering)
        TransferState.WaitingForPeer -> Sentence(R.string.transfer_waiting, listOf(peer))
        TransferState.Sending -> Sentence(R.string.transfer_sending, listOf(percent))
        TransferState.Confirming -> Sentence(R.string.transfer_confirming)
        TransferState.Offered -> Sentence(R.string.transfer_offered, listOf(fileSize(file.size)))
        TransferState.Receiving -> Sentence(R.string.transfer_receiving, listOf(percent))
        TransferState.Verifying -> Sentence(R.string.transfer_verifying)
        TransferState.Declined ->
            if (outgoing) Sentence(R.string.transfer_declined_by, listOf(peer))
            else Sentence(R.string.transfer_declined)
        TransferState.Cancelled -> Sentence(R.string.transfer_cancelled)
        TransferState.Done,
        TransferState.Failed -> null
    }
}

val Message.isTransferring: Boolean
    get() =
        when (attachment?.state) {
            TransferState.Offering,
            TransferState.WaitingForPeer,
            TransferState.Sending,
            TransferState.Confirming,
            TransferState.Receiving,
            TransferState.Verifying -> true
            else -> false
        }

/**
 * A message on one line: its text, *Photo*, or the file's name (a picture's name is rarely worth
 * reading).
 */
fun Message.preview(): Sentence? {
    val file = attachment ?: return null
    return if (file.isPicture) Sentence(R.string.preview_picture)
    else Sentence(R.string.preview_file, listOf(file.name))
}

@Composable fun Message.previewText(): String = preview()?.text() ?: text

/**
 * A size in [locale]'s numbers (`1.2 MB`, `1,2 MB`), in 1024-byte kilobytes like the limits in the
 * settings.
 */
fun fileSize(bytes: Long, locale: Locale = Locale.getDefault()): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    val number = NumberFormat.getNumberInstance(locale)
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < mb -> "${number.apply { maximumFractionDigits = 0 }.format(bytes / kb)} KB"
        bytes < gb -> "${number.apply { maximumFractionDigits = 1 }.format(bytes / mb)} MB"
        else -> "${number.apply { maximumFractionDigits = 1 }.format(bytes / gb)} GB"
    }
}
