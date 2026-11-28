/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.domain.envelope

import java.util.Base64
import kotlin.random.Random
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Sona's envelopes in and out of a `text/plain` body. An envelope is a body whose **last line** is
 * a `sona:1:` line in printable ASCII (formats: docs/messaging.md, Envelopes).
 *
 * Decoding never fails: whatever is not exactly an envelope is [Envelope.Plain]. [textParts],
 * [chunkSize], [missFitting], [receiptsFitting] and [offer] keep bodies within a size limit.
 */
object EnvelopeCodec {
    const val PREFIX = "sona:1:"
    const val ID_LENGTH = 10

    const val MAX_TEXT_PARTS = 99

    fun decode(body: String): Envelope = parse(body) ?: Envelope.Plain(body)

    fun encode(envelope: Envelope): String =
        when (envelope) {
            is Envelope.Plain -> envelope.text
            is Envelope.Text ->
                withAbove(envelope.text, "t:${envelope.id}:${envelope.part}/${envelope.of}")
            is Envelope.Offer -> withAbove(envelope.caption, "offer:${offerJson(envelope.offer)}")
            is Envelope.Accept -> PREFIX + "accept:" + envelope.id
            is Envelope.Decline -> PREFIX + "decline:" + envelope.id
            is Envelope.Chunk ->
                PREFIX + "c:${envelope.id}:${envelope.index}:" + base64(envelope.bytes)
            is Envelope.Miss -> PREFIX + "miss:${envelope.id}:" + ranges(envelope.ranges)
            is Envelope.Ack -> PREFIX + "ack:${envelope.id}:${envelope.received}"
            is Envelope.Done -> PREFIX + "done:" + envelope.id
            is Envelope.Fail -> PREFIX + "fail:${envelope.id}:${envelope.reason}"
            is Envelope.Cancel -> PREFIX + "cancel:" + envelope.id
            is Envelope.Receipt ->
                PREFIX +
                    "rcpt:" +
                    (if (envelope.read) "r:" else "d:") +
                    envelope.ids.joinToString(",")
            Envelope.Typing -> PREFIX + "typing"
            Envelope.Hi -> PREFIX + "hi"
        }

    /** Ten base32 characters: about 50 random bits. */
    fun newId(random: Random = Random.Default): String = buildString {
        repeat(ID_LENGTH) { append(BASE32[random.nextInt(BASE32.length)]) }
    }

    fun isId(text: String): Boolean = text.length == ID_LENGTH && text.all { it in BASE32 }

    // --- Within a size limit ---------------------------------------------------------------

    /**
     * The bodies that carry [text] within [limit] bytes, with an envelope line unless [id] is
     * `null` (a plain peer). `null` past [MAX_TEXT_PARTS] parts.
     */
    fun textParts(text: String, limit: Int, id: String?): List<String>? {
        if (id == null) {
            return TextSplitter.split(text, limit).takeIf { it.size <= MAX_TEXT_PARTS }
        }
        val parts = TextSplitter.split(text, limit - TEXT_OVERHEAD)
        if (parts.size > MAX_TEXT_PARTS) return null
        return parts.mapIndexed { index, part ->
            encode(Envelope.Text(id, index + 1, parts.size, part))
        }
    }

    fun chunkSize(limit: Int): Int = (limit - CHUNK_OVERHEAD) / 4 * 3

    fun chunksOf(size: Long, chunkSize: Int): Int = ((size + chunkSize - 1) / chunkSize).toInt()

    /** As many of [missing], in order, as one `miss` can ask for within [limit] bytes. */
    fun missFitting(id: String, missing: List<IntRange>, limit: Int): Envelope.Miss {
        var budget = limit - (PREFIX.length + "miss:".length + ID_LENGTH + 1)
        val kept = mutableListOf<IntRange>()
        for (range in missing) {
            val size = rangeText(range).length + if (kept.isEmpty()) 0 else 1
            if (size > budget) break
            budget -= size
            kept += range
        }
        return Envelope.Miss(id, kept)
    }

    fun receiptsFitting(read: Boolean, ids: List<String>, limit: Int): List<Envelope.Receipt> {
        val perReceipt = (limit - RECEIPT_OVERHEAD + 1) / (ID_LENGTH + 1)
        return ids.distinct().chunked(perReceipt.coerceAtLeast(1)).map {
            Envelope.Receipt(read, it)
        }
    }

    /**
     * The body offering [offer]. The name is shortened, keeping its extension, until the body fits
     * in [limit] bytes; `null` if it never does.
     *
     * @param caption The words for a plain phone's reader.
     */
    fun offer(offer: FileOffer, limit: Int, caption: (name: String) -> String): String? {
        // The name the receiver will keep: what it reads is what the caption says.
        var name = FileNames.safe(offer.name)
        while (true) {
            val body = encode(Envelope.Offer(offer.copy(name = name), caption(name)))
            if (TextSplitter.utf8Size(body) <= limit) return body
            val shorter = shortened(name)
            if (name.length <= MIN_NAME || shorter.length >= name.length) return null
            name = shorter
        }
    }

    private fun shortened(name: String): String {
        val extension = name.substringAfterLast('.', "").take(MAX_EXTENSION)
        val stem = if (extension.isEmpty()) name else name.dropLast(extension.length + 1)
        val shorter = stem.take((stem.length * 3 / 4).coerceAtLeast(1))
        return if (extension.isEmpty()) "$shorter…" else "$shorter….$extension"
    }

    // --- Decoding ----------------------------------------------------------------------------

    private fun parse(body: String): Envelope? {
        val trimmed = body.trimEnd('\r', '\n')
        val breakAt = trimmed.lastIndexOf('\n')
        val line = trimmed.substring(breakAt + 1)
        if (!line.startsWith(PREFIX) || line.any { it.code !in 0x20..0x7E }) return null
        val above = if (breakAt < 0) "" else trimmed.substring(0, breakAt).removeSuffix("\r")
        val fields = line.removePrefix(PREFIX)
        val kind = fields.substringBefore(':')
        val rest = fields.substringAfter(':', missingDelimiterValue = "")
        return when (kind) {
            "t" -> text(rest, above)
            "offer" -> offer(rest, above)
            "accept" -> rest.idOrNull()?.let(Envelope::Accept)
            "decline" -> rest.idOrNull()?.let(Envelope::Decline)
            "done" -> rest.idOrNull()?.let(Envelope::Done)
            "cancel" -> rest.idOrNull()?.let(Envelope::Cancel)
            "c" -> chunk(rest)
            "miss" -> miss(rest)
            "ack" -> ack(rest)
            "fail" -> fail(rest)
            "rcpt" -> receipt(rest)
            "typing" -> Envelope.Typing.takeIf { fields == "typing" }
            "hi" -> Envelope.Hi.takeIf { fields == "hi" }
            else -> null
        }
    }

    private fun text(rest: String, above: String): Envelope.Text? {
        val (id, count) = rest.split(':').takeIf { it.size == 2 } ?: return null
        val (part, of) = count.split('/').takeIf { it.size == 2 } ?: return null
        val n = part.number() ?: return null
        val total = of.number() ?: return null
        if (!isId(id) || total !in 1..MAX_TEXT_PARTS || n !in 1..total) return null
        return Envelope.Text(id, n, total, above)
    }

    private fun offer(rest: String, above: String): Envelope.Offer? {
        val json =
            runCatching { String(Base64.getUrlDecoder().decode(rest), Charsets.UTF_8) }.getOrNull()
                ?: return null
        val read =
            runCatching { Codec.decodeFromString<OfferJson>(json) }.getOrNull() ?: return null
        return read.toOffer()?.let { Envelope.Offer(it, above) }
    }

    private fun chunk(rest: String): Envelope.Chunk? {
        val fields = rest.split(':')
        if (fields.size != 3 || !isId(fields[0])) return null
        val index = fields[1].number() ?: return null
        val bytes =
            runCatching { Base64.getUrlDecoder().decode(fields[2]) }.getOrNull() ?: return null
        return Envelope.Chunk(fields[0], index, bytes)
    }

    private fun miss(rest: String): Envelope.Miss? {
        val (id, list) = rest.split(':').takeIf { it.size == 2 } ?: return null
        if (!isId(id)) return null
        val items = list.split(',')
        if (items.size > MAX_RANGES) return null
        val ranges = items.map { item ->
            val bounds = item.split('-')
            val first = bounds[0].number() ?: return null
            val last = if (bounds.size == 2) bounds[1].number() ?: return null else first
            if (bounds.size > 2 || last < first) return null
            first..last
        }
        return Envelope.Miss(id, ranges)
    }

    private fun ack(rest: String): Envelope.Ack? {
        val (id, count) = rest.split(':').takeIf { it.size == 2 } ?: return null
        if (!isId(id)) return null
        return Envelope.Ack(id, count.number() ?: return null)
    }

    private fun fail(rest: String): Envelope.Fail? {
        val (id, reason) = rest.split(':').takeIf { it.size == 2 } ?: return null
        if (!isId(id) || reason.isEmpty() || reason.length > MAX_REASON) return null
        if (!reason.all { it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' }) return null
        return Envelope.Fail(id, reason)
    }

    private fun receipt(rest: String): Envelope.Receipt? {
        val (kind, list) = rest.split(':').takeIf { it.size == 2 } ?: return null
        val read =
            when (kind) {
                "d" -> false
                "r" -> true
                else -> return null
            }
        val ids = list.split(',')
        if (ids.isEmpty() || ids.size > MAX_RECEIPT_IDS || !ids.all(::isId)) return null
        return Envelope.Receipt(read, ids)
    }

    private fun String.idOrNull(): String? = takeIf(::isId)

    /** A count or an index: digits only, no sign, no more than an `Int` holds. */
    private fun String.number(): Int? = takeIf {
        it.isNotEmpty() && it.length <= 9 && it.all(Char::isDigit)
    }
        ?.toInt()

    // --- Encoding ----------------------------------------------------------------------------

    private fun withAbove(above: String, line: String): String =
        if (above.isEmpty()) PREFIX + line else above + "\n" + PREFIX + line

    private fun base64(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun ranges(ranges: List<IntRange>) = ranges.joinToString(",", transform = ::rangeText)

    private fun rangeText(range: IntRange) =
        if (range.first == range.last) "${range.first}" else "${range.first}-${range.last}"

    private fun offerJson(offer: FileOffer): String =
        base64(
            Codec.encodeToString(
                    OfferJson(
                        id = offer.id,
                        name = offer.name,
                        mime = offer.mime,
                        size = offer.size,
                        sha256 = offer.sha256,
                        chunk = offer.chunk,
                        chunks = offer.chunks,
                        width = offer.width,
                        height = offer.height,
                    ),
                )
                .toByteArray(Charsets.UTF_8),
        )

    @Serializable
    private data class OfferJson(
        val id: String,
        val name: String,
        val mime: String,
        val size: Long,
        val sha256: String,
        val chunk: Int,
        val chunks: Int,
        @SerialName("w") val width: Int? = null,
        @SerialName("h") val height: Int? = null,
    ) {
        /** The offer, when it describes a file that can be; names and types made safe to keep. */
        fun toOffer(): FileOffer? {
            if (!isId(id) || size !in 0..MAX_FILE_SIZE || chunk !in 1..MAX_CHUNK) return null
            if (chunks != chunksOf(size, chunk)) return null
            val hash = sha256.lowercase()
            if (hash.length != 64 || !hash.all { it in '0'..'9' || it in 'a'..'f' }) return null
            if (width != null && width !in 1..MAX_PIXELS) return null
            if (height != null && height !in 1..MAX_PIXELS) return null
            return FileOffer(
                id = id,
                name = FileNames.safe(name),
                mime = mime.lowercase().takeIf { MimePattern.matches(it) } ?: OCTET_STREAM,
                size = size,
                sha256 = hash,
                chunk = chunk,
                chunks = chunks,
                width = width,
                height = height,
            )
        }
    }

    private val Codec = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private const val BASE32 = "abcdefghijklmnopqrstuvwxyz234567"
    private val MimePattern =
        Regex("[a-z0-9][a-z0-9!#$&^_.+-]{0,63}/[a-z0-9][a-z0-9!#$&^_.+-]{0,63}")
    private const val OCTET_STREAM = "application/octet-stream"

    /** `⏎sona:1:t:` with an id, `:`, `99/99`. */
    private val TEXT_OVERHEAD =
        1 + PREFIX.length + "t:".length + ID_LENGTH + 1 + 2 * "$MAX_TEXT_PARTS".length + 1

    /** `sona:1:c:` with an id, `:`, an index of up to seven digits, `:`. */
    private val CHUNK_OVERHEAD = PREFIX.length + "c:".length + ID_LENGTH + 1 + 7 + 1

    private val RECEIPT_OVERHEAD = PREFIX.length + "rcpt:d:".length

    private const val MAX_RANGES = 1_000
    private const val MAX_RECEIPT_IDS = 1_000
    private const val MAX_REASON = 32
    private const val MAX_CHUNK = 60_000
    private const val MAX_PIXELS = 100_000
    private const val MIN_NAME = 8
    private const val MAX_EXTENSION = 8

    /** Two gigabytes: far beyond what `MESSAGE`s can carry, but not a number that overflows. */
    private const val MAX_FILE_SIZE = 2L * 1024 * 1024 * 1024
}
