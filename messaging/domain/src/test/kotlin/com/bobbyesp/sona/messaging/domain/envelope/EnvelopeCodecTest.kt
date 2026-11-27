/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.domain.envelope

import com.bobbyesp.sona.messaging.domain.readableSize
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvelopeCodecTest {

    private val id = "abcde23456"
    private val hash = "a".repeat(64)

    private fun roundTrip(envelope: Envelope) =
        assertEquals(envelope, EnvelopeCodec.decode(EnvelopeCodec.encode(envelope)))

    @Test
    fun `every envelope comes back as it went`() {
        roundTrip(Envelope.Text(id, 1, 1, "hola"))
        roundTrip(Envelope.Text(id, 2, 3, "line one\nline two\n"))
        roundTrip(Envelope.Text(id, 1, 1, ""))
        roundTrip(Envelope.Accept(id))
        roundTrip(Envelope.Decline(id))
        roundTrip(Envelope.Chunk(id, 41, byteArrayOf(0, -1, 7, 127, -128)))
        roundTrip(Envelope.Chunk(id, 0, ByteArray(0)))
        roundTrip(Envelope.Miss(id, listOf(0..12, 40..40, 77..80)))
        roundTrip(Envelope.Ack(id, 1234))
        roundTrip(Envelope.Done(id))
        roundTrip(Envelope.Fail(id, FailReasons.HASH))
        roundTrip(Envelope.Cancel(id))
        roundTrip(Envelope.Receipt(read = false, ids = listOf(id, "zzzzz77777")))
        roundTrip(Envelope.Receipt(read = true, ids = listOf(id)))
        roundTrip(Envelope.Typing)
        roundTrip(Envelope.Hi)
    }

    @Test
    fun `an offer comes back with its caption and its description`() {
        val offer = FileOffer(id, "holiday.jpg", "image/jpeg", 188_416, hash, 501, 377, 2048, 1536)
        val body = EnvelopeCodec.encode(Envelope.Offer(offer, "📎 holiday.jpg · 184 KB"))

        assertEquals(Envelope.Offer(offer, "📎 holiday.jpg · 184 KB"), EnvelopeCodec.decode(body))
        assertTrue(body.lines().last().all { it.code in 0x20..0x7E })
    }

    @Test
    fun `the wire format is the documented one`() {
        assertEquals("sona:1:typing", EnvelopeCodec.encode(Envelope.Typing))
        assertEquals("hi\nsona:1:t:$id:1/2", EnvelopeCodec.encode(Envelope.Text(id, 1, 2, "hi")))
        assertEquals(
            "sona:1:miss:$id:0-12,40,77-80",
            EnvelopeCodec.encode(Envelope.Miss(id, listOf(0..12, 40..40, 77..80))),
        )
        assertEquals(
            "sona:1:rcpt:r:$id,bbbbbbbbbb",
            EnvelopeCodec.encode(Envelope.Receipt(true, listOf(id, "bbbbbbbbbb"))),
        )
        assertEquals(
            "sona:1:c:$id:3:AAE",
            EnvelopeCodec.encode(Envelope.Chunk(id, 3, byteArrayOf(0, 1))),
        )
    }

    @Test
    fun `what is not exactly an envelope is a plain text, shown as it came`() {
        val bodies =
            listOf(
                "hello",
                "",
                "sona:1:",
                "sona:1:nope",
                "sona:2:typing",
                "sona:1:typing:extra",
                "sona:1:hi!",
                "sona:1:accept:short",
                "sona:1:accept:ABCDE23456",
                "sona:1:accept:abcde234561",
                "sona:1:t:$id:0/1",
                "sona:1:t:$id:3/2",
                "sona:1:t:$id:1/100",
                "sona:1:t:$id:-1/2",
                "sona:1:t:$id:1/x",
                "sona:1:c:$id:1:***",
                "sona:1:c:$id:99999999999:AA",
                "sona:1:miss:$id:5-3",
                "sona:1:miss:$id:1-2-3",
                "sona:1:miss:$id:",
                "sona:1:ack:$id:-4",
                "sona:1:fail:$id:Bad Reason",
                "sona:1:rcpt:x:$id",
                "sona:1:rcpt:d:$id,nope",
                "sona:1:offer:not-base64!",
                "sona:1:offer:e30",
                "sona:1:typing é",
                "sona:1:typing\nafter the envelope",
            )
        for (body in bodies) {
            assertEquals(body, Envelope.Plain(body), EnvelopeCodec.decode(body))
        }
    }

    @Test
    fun `an offer that describes no file that can be is a plain text`() {
        fun offerWith(json: String): String =
            "x\nsona:1:offer:" +
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())

        val good =
            """{"id":"$id","name":"a.bin","mime":"application/zip","size":10,"sha256":"$hash","chunk":4,"chunks":3}"""
        assertTrue(EnvelopeCodec.decode(offerWith(good)) is Envelope.Offer)
        // Chunks that do not add up, a short hash, a negative size, a bad id.
        listOf(
                good.replace("\"chunks\":3", "\"chunks\":4"),
                good.replace(hash, "abc"),
                good.replace("\"size\":10", "\"size\":-1"),
                good.replace(id, "bad"),
            )
            .forEach { assertTrue(it, EnvelopeCodec.decode(offerWith(it)) is Envelope.Plain) }
    }

    @Test
    fun `an offered name is only ever a name, and an unknown type is a plain file`() {
        val offer = FileOffer(id, "../../shared_prefs/x.xml", "TEXT/<script>", 1, hash, 1, 1)
        val read =
            EnvelopeCodec.decode(EnvelopeCodec.encode(Envelope.Offer(offer))) as Envelope.Offer

        assertEquals("x.xml", read.offer.name)
        assertEquals("application/octet-stream", read.offer.mime)
    }

    @Test
    fun `a body may end with line breaks the server added`() {
        assertEquals(Envelope.Typing, EnvelopeCodec.decode("sona:1:typing\r\n"))
        assertEquals(Envelope.Text(id, 1, 1, "a"), EnvelopeCodec.decode("a\r\nsona:1:t:$id:1/1\n"))
    }

    @Test
    fun `text parts for a Sona peer fit the limit, and join back to the text`() {
        val text = "Lorem ipsum dolor sit amet, ñandú 🐧 — ".repeat(60)
        for (limit in listOf(700, 1_000, 4_000)) {
            val bodies = EnvelopeCodec.textParts(text, limit, id)!!
            assertTrue(bodies.all { it.toByteArray().size <= limit })
            val parts = bodies.map { EnvelopeCodec.decode(it) as Envelope.Text }
            assertEquals(text, parts.joinToString("") { it.text })
            assertEquals(List(parts.size) { it + 1 }, parts.map { it.part })
            assertTrue(parts.all { it.of == parts.size })
        }
    }

    @Test
    fun `text parts for a plain peer are the text alone`() {
        val text = "word ".repeat(400)
        val bodies = EnvelopeCodec.textParts(text, 700, id = null)!!

        assertTrue(bodies.size > 1)
        assertTrue(bodies.none { it.contains("sona:") })
        assertEquals(text, bodies.joinToString(""))
    }

    @Test
    fun `a text that needs more than 99 parts is refused`() {
        assertNull(EnvelopeCodec.textParts("x".repeat(700 * 100), 700, id))
        assertNotNull(EnvelopeCodec.textParts("x".repeat(600 * 90), 700, id))
    }

    @Test
    fun `a chunk at the largest index fits the limit it was cut for`() {
        for (limit in listOf(700, 1_000, 4_000, 16_000, 60_000)) {
            val size = EnvelopeCodec.chunkSize(limit)
            val body = EnvelopeCodec.encode(Envelope.Chunk(id, 9_999_999, ByteArray(size) { -1 }))
            assertTrue("$limit", body.length <= limit)
            assertTrue("$limit", size > limit / 2)
        }
        assertEquals(504, EnvelopeCodec.chunkSize(700))
        assertEquals(2, EnvelopeCodec.chunksOf(1_008, 504))
        assertEquals(3, EnvelopeCodec.chunksOf(1_009, 504))
        assertEquals(0, EnvelopeCodec.chunksOf(0, 504))
    }

    @Test
    fun `a miss asks for as many ranges as fit, in order`() {
        val ranges = (0 until 1_000).map { (it * 3)..(it * 3) }
        val miss = EnvelopeCodec.missFitting(id, ranges, 700)

        assertTrue(EnvelopeCodec.encode(miss).length <= 700)
        assertEquals(ranges.take(miss.ranges.size), miss.ranges)
        assertTrue(miss.ranges.size > 50)
    }

    @Test
    fun `receipts are split to fit the limit`() {
        val ids = List(200) { EnvelopeCodec.newId(Random(it)) }
        val receipts = EnvelopeCodec.receiptsFitting(read = true, ids, 700)

        assertTrue(receipts.size > 1)
        assertTrue(receipts.all { EnvelopeCodec.encode(it).length <= 700 })
        assertEquals(ids.distinct(), receipts.flatMap { it.ids })
    }

    @Test
    fun `an offer's name is shortened until it fits, keeping its extension`() {
        val offer = FileOffer(id, "a".repeat(400) + ".jpeg", "image/jpeg", 10, hash, 4, 3)
        val body = EnvelopeCodec.offer(offer, 700) { "📎 $it · 10 B — sent with Sona" }!!

        assertTrue(body.toByteArray().size <= 700)
        val read = EnvelopeCodec.decode(body) as Envelope.Offer
        assertTrue(read.offer.name.endsWith(".jpeg"))
        assertTrue(read.caption.contains(read.offer.name))
        assertNull(EnvelopeCodec.offer(offer, 200) { it })
    }

    @Test
    fun `ids are ten characters of base32`() {
        val made = EnvelopeCodec.newId()
        assertTrue(EnvelopeCodec.isId(made))
        assertEquals(10, made.length)
    }

    @Test
    fun `sizes read as people read them`() {
        assertEquals("512 B", readableSize(512))
        assertEquals("184 KB", readableSize(188_416))
        assertEquals("1.2 MB", readableSize(1_258_291))
        assertEquals("10 MB", readableSize(10L * 1024 * 1024))
    }

    @Test
    fun `file names are made safe to keep`() {
        assertEquals("x.xml", FileNames.safe("../../shared_prefs/x.xml"))
        assertEquals("evil.txt", FileNames.safe("C:\\Users\\evil.txt"))
        assertEquals("file", FileNames.safe("..."))
        assertEquals("ab.png", FileNames.safe("a\u0000b.png"))
        val long = FileNames.safe("n".repeat(500) + ".pdf")
        assertTrue(long.length <= 120)
        assertTrue(long.endsWith(".pdf"))
    }
}
