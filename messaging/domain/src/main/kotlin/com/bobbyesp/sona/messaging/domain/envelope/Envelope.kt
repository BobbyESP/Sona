/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.domain.envelope

/**
 * What a `MESSAGE` body means: a plain text, or one of Sona's envelopes. The wire format is
 * [EnvelopeCodec]'s (docs/messaging.md, Envelopes).
 */
sealed interface Envelope {
    /** A body that is not an envelope: shown as it came. */
    data class Plain(val text: String) : Envelope

    /**
     * Part [part] of [of], from 1, of the text [id]. It travels above the envelope line, so it may
     * hold anything.
     */
    data class Text(val id: String, val part: Int, val of: Int, val text: String) : Envelope

    /** A file offered; [caption] is what the lines above say about it, for a person. */
    data class Offer(val offer: FileOffer, val caption: String = "") : Envelope

    data class Accept(val id: String) : Envelope

    data class Decline(val id: String) : Envelope

    /** Chunk [index] (from 0) of the file [id]. */
    class Chunk(val id: String, val index: Int, val bytes: ByteArray) : Envelope {
        override fun equals(other: Any?): Boolean =
            other is Chunk &&
                other.id == id &&
                other.index == index &&
                other.bytes.contentEquals(bytes)

        override fun hashCode(): Int = (id.hashCode() * 31 + index) * 31 + bytes.contentHashCode()

        override fun toString(): String = "Chunk($id, $index, ${bytes.size} bytes)"
    }

    data class Miss(val id: String, val ranges: List<IntRange>) : Envelope

    data class Ack(val id: String, val received: Int) : Envelope

    /** The file [id] arrived whole: its SHA-256 matched. */
    data class Done(val id: String) : Envelope

    /** The file [id] could not be received; [reason] is a short word (`hash`, `timeout`). */
    data class Fail(val id: String, val reason: String) : Envelope

    data class Cancel(val id: String) : Envelope

    /** The texts (or files) [ids] were delivered to, or [read] on, the other phone. */
    data class Receipt(val read: Boolean, val ids: List<String>) : Envelope

    data object Typing : Envelope

    /** "I am Sona": the answer to the first envelope from a peer not yet known. */
    data object Hi : Envelope
}

/**
 * A file as its offer describes it.
 *
 * @property sha256 Lowercase hex, 64 characters.
 * @property chunk The bytes in each chunk but the last.
 */
data class FileOffer(
    val id: String,
    val name: String,
    val mime: String,
    val size: Long,
    val sha256: String,
    val chunk: Int,
    val chunks: Int,
    val width: Int? = null,
    val height: Int? = null,
)

/** Why a receiver could not take a file, as [Envelope.Fail] says it. */
object FailReasons {
    const val HASH = "hash"

    const val TIMEOUT = "timeout"

    const val STORAGE = "storage"
}
