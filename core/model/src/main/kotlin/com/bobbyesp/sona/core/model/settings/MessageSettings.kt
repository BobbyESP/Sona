/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.model.settings

/**
 * The longest body the server relays in one `MESSAGE`, in bytes. Asterisk copies a body into a
 * 1024-byte buffer and sends a request over 1300 bytes by TCP, so its safe size is the first; the
 * others are for servers that take more (FreeSWITCH, Kamailio, a direct peer). See
 * docs/messaging.md.
 */
enum class MessageSizeLimit(val bytes: Int) {
    /** Asterisk over UDP: the whole request stays under 1300 bytes. */
    B700(700),

    /** Under Asterisk's 1023-byte body, for a server reached over TCP. */
    Kb1(1_000),
    Kb4(4_000),
    Kb16(16_000),

    /** Under the largest UDP datagram, with room for the headers. */
    Kb60(60_000),
}

/** Files received up to this size are downloaded without asking. */
enum class AutoDownloadLimit(val bytes: Long) {
    Never(0),
    Kb500(500L * 1024),
    Mb2(2L * 1024 * 1024),
    Mb10(10L * 1024 * 1024),
}

/** The largest file that can be sent: every 500 bytes of it is a `MESSAGE` at Asterisk's size. */
enum class AttachmentLimit(val bytes: Long) {
    Mb2(2L * 1024 * 1024),
    Mb5(5L * 1024 * 1024),
    Mb10(10L * 1024 * 1024),
    Mb20(20L * 1024 * 1024),
}

/** Texts, pictures and files between extensions. */
object MessageSettings {
    /** Tell another Sona phone when its messages were read here. */
    val ReadReceipts = Setting.Toggle("messages.read_receipts", default = true)

    /** Tell another Sona phone while a message is being written here. */
    val TypingIndicators = Setting.Toggle("messages.typing_indicators", default = true)

    val AutoDownload = Setting.choice("messages.auto_download", AutoDownloadLimit.Mb2)

    /** Pictures as they are; off, they are resized to 2048 px and compressed first. */
    val OriginalQualityPictures = Setting.Toggle("messages.original_quality", default = false)

    val LargestAttachment = Setting.choice("messages.largest_attachment", AttachmentLimit.Mb10)

    /** Per account: each server relays its own size. */
    val LargestMessage =
        Setting.choice("messages.largest_message", MessageSizeLimit.B700, overridable = true)
}
