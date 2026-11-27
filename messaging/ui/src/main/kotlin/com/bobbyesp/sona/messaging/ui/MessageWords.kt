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

/** Where a message is, in a word: *Sending*, *Delivered*, *Not sent*. */
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
 * Where a message's file is, as a line under it: *Waiting for 102 to download*, *Sending · 40 %*;
 * `null` for a message without a file, or a file that is simply there (sent or received whole). A
 * failure is said by the failure's own sentence, not here.
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

/** Whether the file of [this] is on its way: a progress ring or bar is shown over it. */
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
 * What a list shows of a message on one line: its text, or for a file *Photo* or its name. A
 * picture has no name worth reading (`IMG_2041.jpg`); any other file is known by it.
 */
fun Message.preview(): Sentence? {
    val file = attachment ?: return null
    return if (file.isPicture) Sentence(R.string.preview_picture)
    else Sentence(R.string.preview_file, listOf(file.name))
}

/** [preview], or the text itself. */
@Composable fun Message.previewText(): String = preview()?.text() ?: text

/**
 * A size as people read it, in [locale]'s numbers: `512 B`, `184 KB`, `1.2 MB` (`1,2 MB` in
 * Spanish). Kilobytes are 1024 bytes, as the limits in the settings are.
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
