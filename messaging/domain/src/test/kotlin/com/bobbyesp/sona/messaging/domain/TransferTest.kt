/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.domain

import com.bobbyesp.sona.core.model.settings.AttachmentLimit
import com.bobbyesp.sona.core.model.settings.AutoDownloadLimit
import com.bobbyesp.sona.core.model.settings.MessageSettings
import com.bobbyesp.sona.messaging.domain.envelope.Envelope
import com.bobbyesp.sona.messaging.domain.envelope.EnvelopeCodec
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Files between two messengers wired through a scripted server: transfers as scenarios. */
@OptIn(ExperimentalCoroutinesApi::class)
class TransferTest {

    private val picture = Random(7).nextBytes(20_000)

    private class World(val a: Phone, val b: Phone, val conversation: ConversationId)

    private suspend fun TestScope.world(
        lose: (from: Phone, body: String) -> Boolean = { _, _ -> false },
        setUp: suspend (a: Phone, b: Phone) -> Unit = { _, _ -> },
    ): World {
        val a = Phone(this, "101")
        val b = Phone(this, "102")
        setUp(a, b)
        a.boot()
        b.boot()
        link(a, b, lose = lose)
        a.files.pick("content://picked/1", "holiday.jpg", picture)
        return World(a, b, a.with(b))
    }

    private suspend fun World.send(): MessageId =
        (a.messenger.sendFile(conversation, "content://picked/1") as SendOutcome.Queued).message

    private suspend fun World.sent(): Attachment = a.only(conversation).attachment!!

    private suspend fun World.there(): Message {
        val conversation = b.store.findConversation(b.account, a.address)!!
        return b.messages(conversation.id).single()
    }

    private fun World.chunksSent(): List<Envelope.Chunk> =
        a.sentTo(b).filterIsInstance<Envelope.Chunk>()

    @Test
    fun `a file is offered, accepted by itself, sent in chunks and arrives whole`() = runTest {
        val world = world()

        world.send()
        wait(3)

        val offer = world.a.sentTo(world.b).filterIsInstance<Envelope.Offer>().single()
        assertTrue(offer.caption.contains("holiday.jpg"))
        assertEquals(
            EnvelopeCodec.chunksOf(20_000, EnvelopeCodec.chunkSize(700)),
            offer.offer.chunks,
        )
        assertEquals(offer.offer.chunks, world.chunksSent().size)
        assertTrue(world.chunksSent().all { EnvelopeCodec.encode(it).length <= 700 })

        val there = world.there()
        assertEquals(TransferState.Done, there.attachment!!.state)
        assertArrayEquals(picture, world.b.files.stored.getValue(there.attachment.file!!))
        assertEquals(TransferState.Done, world.sent().state)
        assertEquals(MessageStatus.Delivered, world.a.only(world.conversation).status)
        assertTrue(world.b.files.partial.isEmpty())
    }

    @Test
    fun `no more than eight chunks are ever waiting for the server`() = runTest {
        val world = world()
        var waiting = 0
        var most = 0
        val server = world.a.messaging.server
        world.a.messaging.server = { sent ->
            waiting++
            most = maxOf(most, waiting)
            delay(50)
            server(sent).also { waiting-- }
        }

        world.send()
        wait(10)

        assertEquals(TransferState.Done, world.sent().state)
        assertEquals(Transfers.WINDOW, most)
    }

    @Test
    fun `chunks lost on the way are asked for again after eight seconds, and repaired`() = runTest {
        val lost = mutableSetOf<Int>()
        val world =
            world(
                lose = { from, body ->
                    val chunk = EnvelopeCodec.decode(body) as? Envelope.Chunk
                    // Every third chunk is lost the first time it is sent.
                    from.user == "101" &&
                        chunk != null &&
                        chunk.index % 3 == 0 &&
                        lost.add(chunk.index)
                },
            )

        world.send()
        wait(3)
        assertEquals(TransferState.Receiving, world.there().attachment!!.state)

        wait(10)

        val miss = world.b.sentTo(world.a).filterIsInstance<Envelope.Miss>()
        assertTrue(miss.isNotEmpty())
        assertEquals(TransferState.Done, world.there().attachment!!.state)
        assertArrayEquals(picture, world.b.files.stored.getValue(world.there().attachment!!.file!!))
        assertEquals(TransferState.Done, world.sent().state)
    }

    @Test
    fun `a file larger than the automatic limit waits for Download`() = runTest {
        val world =
            world(
                setUp = { _, b ->
                    b.settings.set(MessageSettings.AutoDownload, AutoDownloadLimit.Never)
                },
            )

        world.send()
        wait(20)

        assertEquals(TransferState.Offered, world.there().attachment!!.state)
        assertEquals(TransferState.WaitingForPeer, world.sent().state)
        assertTrue(world.chunksSent().isEmpty())

        world.b.messenger.download(world.there().id)
        wait(3)

        assertEquals(TransferState.Done, world.there().attachment!!.state)
        assertEquals(TransferState.Done, world.sent().state)
    }

    @Test
    fun `a plain phone only ever sees the offer's words`() = runTest {
        val a = Phone(this, "101").apply { boot() }
        val plain = Phone(this, "103").apply { boot() }
        // The plain phone has no messenger answering: its server takes what it is sent.
        a.files.pick("content://picked/1", "holiday.jpg", picture)
        val conversation = a.with(plain)

        a.messenger.sendFile(conversation, "content://picked/1")
        wait(60)

        val bodies = a.messaging.sentTo(plain.address)
        assertEquals(1, bodies.size)
        assertTrue(bodies.single().startsWith("📎 holiday.jpg · 20 KB"))
        assertEquals(TransferState.WaitingForPeer, a.only(conversation).attachment!!.state)
        assertEquals(MessageStatus.Sent, a.only(conversation).status)
    }

    @Test
    fun `a file that does not match its hash fails on both sides, and Retry sends it again`() =
        runTest {
            var corrupt = true
            val world = world()
            val server = world.a.messaging.server
            world.a.messaging.server = { sent ->
                val chunk = EnvelopeCodec.decode(sent.body) as? Envelope.Chunk
                if (corrupt && chunk?.index == 1) {
                    val bytes = chunk.bytes.copyOf().also { it[0] = (it[0] + 1).toByte() }
                    server(
                        sent.copy(body = EnvelopeCodec.encode(Envelope.Chunk(chunk.id, 1, bytes))),
                    )
                } else {
                    server(sent)
                }
            }

            world.send()
            wait(3)

            assertEquals(FailureReason.Corrupted, world.there().failure?.reason)
            assertEquals(TransferState.Failed, world.sent().state)
            assertEquals(FailureReason.Corrupted, world.a.only(world.conversation).failure?.reason)

            corrupt = false
            world.a.messenger.retry(world.a.only(world.conversation).id)
            wait(3)

            assertEquals(TransferState.Done, world.sent().state)
            assertEquals(TransferState.Done, world.there().attachment!!.state)
            assertArrayEquals(
                picture,
                world.b.files.stored.getValue(world.there().attachment!!.file!!),
            )
        }

    @Test
    fun `a sender that goes silent makes the transfer fail after five requests`() = runTest {
        val world = world(lose = { from, body -> from.user == "101" && body.contains(":c:") })

        world.send()
        wait(8 * 7)

        val there = world.there()
        assertEquals(TransferState.Failed, there.attachment!!.state)
        assertEquals(FailureReason.TimedOut, there.failure?.reason)
        assertEquals(5, world.b.sentTo(world.a).count { it is Envelope.Miss })
        assertEquals(FailureReason.TimedOut, world.a.only(world.conversation).failure?.reason)
    }

    @Test
    fun `cancelling a transfer stops it on both sides`() = runTest {
        val world = world()
        world.a.messaging.server.let { server ->
            world.a.messaging.server = { sent ->
                delay(1_000)
                server(sent)
            }
        }

        val id = world.send()
        wait(3)
        assertEquals(TransferState.Sending, world.sent().state)
        world.a.messenger.cancel(id)
        wait(3)

        assertEquals(TransferState.Cancelled, world.sent().state)
        assertEquals(TransferState.Cancelled, world.there().attachment!!.state)
        assertTrue(world.b.files.partial.isEmpty())
        val sent = world.chunksSent().size
        wait(10)
        assertEquals(sent, world.chunksSent().size)
    }

    @Test
    fun `declining a file tells the sender`() = runTest {
        val world =
            world(
                setUp = { _, b ->
                    b.settings.set(MessageSettings.AutoDownload, AutoDownloadLimit.Never)
                },
            )

        world.send()
        wait(1)
        world.b.messenger.decline(world.there().id)
        wait(1)

        assertEquals(TransferState.Declined, world.there().attachment!!.state)
        assertEquals(TransferState.Declined, world.sent().state)
    }

    @Test
    fun `a file larger than the largest attachment is refused before anything is sent`() = runTest {
        val world =
            world(
                setUp = { a, _ ->
                    a.settings.set(MessageSettings.LargestAttachment, AttachmentLimit.Mb2)
                },
            )
        world.a.files.pick("content://picked/2", "big.bin", ByteArray(3 * 1024 * 1024))

        val outcome = world.a.messenger.sendFile(world.conversation, "content://picked/2")

        assertTrue(outcome is SendOutcome.FileTooLarge)
        assertTrue(world.a.messaging.sent.isEmpty())
    }

    @Test
    fun `a receiver closed mid-transfer goes on with the chunks it had kept`() = runTest {
        var cut = true
        val world =
            world(
                lose = { from, body ->
                    cut &&
                        from.user == "101" &&
                        body.contains(":c:") &&
                        EnvelopeCodec.decode(body).let { it is Envelope.Chunk && it.index >= 20 }
                },
            )

        world.send()
        wait(3)
        val before = world.there().attachment!!
        assertEquals(TransferState.Receiving, before.state)
        assertEquals(20, before.done)

        world.b.restart()
        cut = false
        wait(12)

        val there = world.there()
        assertEquals(TransferState.Done, there.attachment!!.state)
        assertArrayEquals(picture, world.b.files.stored.getValue(there.attachment.file!!))
        // Only what was missing was asked for again.
        val asked = world.b.sentTo(world.a).filterIsInstance<Envelope.Miss>().flatMap { it.ranges }
        assertTrue(asked.all { it.first >= 20 })
    }

    @Test
    fun `a sender closed mid-transfer goes on from where it was`() = runTest {
        val world = world()
        // A slow server: a second for each message, eight at a time.
        world.a.messaging.server.let { server ->
            world.a.messaging.server = { sent ->
                delay(1_000)
                server(sent)
            }
        }

        world.send()
        wait(4)
        val before = world.sent()
        assertEquals(TransferState.Sending, before.state)
        world.a.restart()
        wait(20)

        assertEquals(TransferState.Done, world.sent().state)
        assertEquals(TransferState.Done, world.there().attachment!!.state)
        assertArrayEquals(picture, world.b.files.stored.getValue(world.there().attachment!!.file!!))
    }

    @Test
    fun `a received file can be deleted, and goes from storage`() = runTest {
        val world = world()
        world.send()
        wait(3)
        val there = world.there()

        world.b.messenger.delete(listOf(there.id))

        assertTrue(world.b.files.stored.isEmpty())
        assertTrue(world.b.store.all.isEmpty())
    }
}
