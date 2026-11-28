/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.ContactPage
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bobbyesp.sona.messaging.domain.MessageStatus

enum class FileKind {
    Picture,
    Video,
    Audio,
    Pdf,
    Text,
    Spreadsheet,
    Presentation,
    Archive,
    Contact,
    Other;

    companion object {
        fun of(mime: String): FileKind {
            val type = mime.lowercase().substringBefore(';').trim()
            return when {
                type.startsWith("image/") -> Picture
                type.startsWith("video/") -> Video
                type.startsWith("audio/") -> Audio
                type == "application/pdf" -> Pdf
                type == "text/vcard" || type == "text/x-vcard" -> Contact
                type == "text/csv" ||
                    type == "application/vnd.ms-excel" ||
                    "spreadsheetml" in type ||
                    "opendocument.spreadsheet" in type -> Spreadsheet
                type.startsWith("text/") ||
                    type == "application/msword" ||
                    type == "application/rtf" ||
                    "wordprocessingml" in type ||
                    "opendocument.text" in type -> Text
                type == "application/vnd.ms-powerpoint" ||
                    "presentationml" in type ||
                    "opendocument.presentation" in type -> Presentation
                type in Archives -> Archive
                else -> Other
            }
        }

        private val Archives =
            setOf(
                "application/zip",
                "application/x-zip-compressed",
                "application/gzip",
                "application/x-7z-compressed",
                "application/x-rar-compressed",
                "application/vnd.rar",
                "application/x-tar",
            )
    }
}

val FileKind.icon: ImageVector
    get() =
        when (this) {
            FileKind.Picture -> Icons.Rounded.Image
            FileKind.Video -> Icons.Rounded.Movie
            FileKind.Audio -> Icons.Rounded.AudioFile
            FileKind.Pdf -> Icons.Rounded.PictureAsPdf
            FileKind.Text -> Icons.Rounded.Description
            FileKind.Spreadsheet -> Icons.Rounded.TableChart
            FileKind.Presentation -> Icons.Rounded.Slideshow
            FileKind.Archive -> Icons.Rounded.Archive
            FileKind.Contact -> Icons.Rounded.ContactPage
            FileKind.Other -> Icons.AutoMirrored.Rounded.InsertDriveFile
        }

/**
 * The mark of an outgoing message's status, with its word as content description, so it is never
 * only a shape.
 */
@Composable
fun MessageStatusMark(status: MessageStatus, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val (icon, tint) =
        when (status) {
            MessageStatus.Sending -> Icons.Rounded.Schedule to LocalContentColor.current
            MessageStatus.Sent -> Icons.Rounded.Done to LocalContentColor.current
            MessageStatus.Delivered -> Icons.Rounded.DoneAll to LocalContentColor.current
            MessageStatus.Read -> Icons.Rounded.DoneAll to MaterialTheme.colorScheme.primary
            MessageStatus.Failed -> Icons.Rounded.ErrorOutline to MaterialTheme.colorScheme.error
            MessageStatus.Received -> return
        }
    Icon(
        icon,
        contentDescription = status.label.text(),
        tint = tint,
        modifier = modifier.size(size),
    )
}
