/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.domain.envelope

/**
 * What a `MESSAGE` body means: a plain text, or one of Sona's envelopes. A body is an envelope when
 * its last line is a valid `sona:1:` line, all ASCII; anything else, a malformed envelope
 * included, is [Plain] and shown as it came. The wire format is [EnvelopeCodec]'s, and
 * docs/messaging.md, Envelopes.
 */
sealed interface Envelope {
    /** A body that is not an envelope: shown as it came. */
    data class Plain(val text: String) : Envelope

    /**
     * Part [part] of [of] (from 1) of the text [id]: the parts of a text, put together in order, are
     * the text. The part travels above the envelope line, so it may hold anything, new lines
     * included.
     */
    data class Text(val id: String, val part: Int, val of: Int, val text: String) : Envelope

    /** A file offered; [caption] is what the lines above say about it, for a person. */
    data class Offer(val offer: FileOffer, val caption: String = "") : Envelope

    data class Accept(val id: String) : Envelope

    data class Decline(val id: String) : Envelope

    /** Chunk [index] (from 0) of the file [id]. */
    class Chunk(val id: String, val index: Int, val bytes: ByteArray) : Envelope {
        override fun equals(other: Any?): Boolean =
            other is Chunk && other.id == id && other.index == index && other.bytes.contentEquals(bytes)

        override fun hashCode(): Int = (id.hashCode() * 31 + index) * 31 + bytes.contentHashCode()

        override fun toString(): String = "Chunk($id, $index, ${bytes.size} bytes)"
    }

    /** The chunks of [id] the receiver still lacks, asked for again. */
    data class Miss(val id: String, val ranges: List<IntRange>) : Envelope

    /** The receiver has [received] chunks of [id]. */
    data class Ack(val id: String, val received: Int) : Envelope

    /** The file [id] arrived whole: its SHA-256 matched. */
    data class Done(val id: String) : Envelope

    /** The file [id] could not be received; [reason] is a short word (`hash`, `timeout`). */
    data class Fail(val id: String, val reason: String) : Envelope

    /** Either side stopped the transfer of [id]. */
    data class Cancel(val id: String) : Envelope

    /** The texts (or files) [ids] were delivered to, or [read] on, the other phone. */
    data class Receipt(val read: Boolean, val ids: List<String>) : Envelope

    /** The other side is writing. */
    data object Typing : Envelope

    /** "I am Sona": the answer to the first envelope from a peer not yet known. */
    data object Hi : Envelope
}

/**
 * A file as its offer describes it.
 *
 * @property sha256 Lowercase hex, 64 characters.
 * @property chunk The bytes of file in each chunk but the last; [chunks] is `size / chunk`, rounded
 *   up.
 * @property width, [height] A picture's size, when it is one.
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
    /** The SHA-256 of what arrived was not the one offered. */
    const val HASH = "hash"

    /** Chunks stopped coming. */
    const val TIMEOUT = "timeout"

    /** The receiver could not write the file. */
    const val STORAGE = "storage"
}
