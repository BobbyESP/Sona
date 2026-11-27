/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.di

import android.content.Context
import com.bobbyesp.sona.R
import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.messaging.domain.envelope.Envelope
import com.bobbyesp.sona.messaging.domain.envelope.EnvelopeCodec
import com.bobbyesp.sona.messaging.domain.envelope.FailReasons
import com.bobbyesp.sona.messaging.domain.envelope.FileOffer
import com.bobbyesp.sona.sip.fake.FakeMessaging
import java.security.MessageDigest
import java.util.BitSet
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The other phones of the demo, answering `MESSAGE`s on the fake engine as a server and a phone
 * would. They speak Sona's envelopes through the domain's own codec, so what the demo shows is what
 * two real phones would do:
 * - **100** is a Sona phone: it says hello, answers every text with receipts, typing and a reply,
 *   and accepts, downloads and confirms every file.
 * - **101** is a plain SIP phone: it echoes what it is sent as text, and nothing else.
 *
 * Any other extension's server takes the message (`202`) and nothing comes back, as Asterisk does
 * for a phone that is not registered.
 */
class DemoPeers(
    private val context: Context,
    private val messaging: FakeMessaging,
    private val scope: CoroutineScope,
) {
    private val lock = Mutex()
    private val transfers = mutableMapOf<String, Download>()
    private var greeted = false

    /** A file 100 is downloading: in memory, as the demo's files are small. */
    private class Download(val offer: FileOffer) {
        val bytes = ByteArray(offer.size.toInt())
        val have = BitSet(offer.chunks)
        var acked = 0
    }

    fun install() {
        messaging.server = { sent ->
            delay(ServerTime)
            when (sent.to.user) {
                SONA -> scope.launch { lock.withLock { sona(sent) } }
                PLAIN -> scope.launch { plain(sent) }
            }
            FakeMessaging.ACCEPTED
        }
    }

    private suspend fun plain(sent: FakeMessaging.Sent) {
        val envelope = EnvelopeCodec.decode(sent.body)
        if (envelope !is Envelope.Plain) return
        delay(1.seconds)
        reply(sent, context.getString(R.string.demo_plain_echo, envelope.text))
    }

    private suspend fun sona(sent: FakeMessaging.Sent) {
        when (val envelope = EnvelopeCodec.decode(sent.body)) {
            is Envelope.Plain -> answerText(sent, envelope.text, id = null)
            is Envelope.Text ->
                if (envelope.part == envelope.of) answerText(sent, envelope.text, envelope.id)
            is Envelope.Offer -> {
                hello(sent)
                if (envelope.offer.size > MAX_DEMO_FILE) {
                    later(sent) { send(sent, Envelope.Decline(envelope.offer.id)) }
                    return
                }
                transfers[envelope.offer.id] = Download(envelope.offer)
                send(sent, Envelope.Accept(envelope.offer.id))
                if (envelope.offer.chunks == 0) finish(sent, envelope.offer.id)
            }
            is Envelope.Chunk -> chunk(sent, envelope)
            is Envelope.Cancel -> transfers.remove(envelope.id)
            else -> Unit
        }
    }

    /** 100 heard a text: it says it got it, reads it, writes, and answers. */
    private fun answerText(sent: FakeMessaging.Sent, text: String, id: String?) {
        hello(sent)
        if (id != null) send(sent, Envelope.Receipt(read = false, ids = listOf(id)))
        later(sent, 1.seconds) {
            if (id != null) send(sent, Envelope.Receipt(read = true, ids = listOf(id)))
            send(sent, Envelope.Typing)
            delay(2.seconds)
            val answer = context.getString(R.string.demo_sona_reply, text.take(200))
            EnvelopeCodec.textParts(answer, 700, EnvelopeCodec.newId())?.forEach {
                reply(sent, it)
            }
        }
    }

    private fun chunk(sent: FakeMessaging.Sent, chunk: Envelope.Chunk) {
        val download = transfers[chunk.id] ?: return send(sent, Envelope.Cancel(chunk.id))
        val offset = chunk.index.toLong() * download.offer.chunk
        if (chunk.index !in 0 until download.offer.chunks) return
        if (offset + chunk.bytes.size > download.bytes.size) return
        chunk.bytes.copyInto(download.bytes, offset.toInt())
        download.have.set(chunk.index)
        val count = download.have.cardinality()
        if (count - download.acked >= ACK_EVERY) {
            download.acked = count
            send(sent, Envelope.Ack(chunk.id, count))
        }
        if (count == download.offer.chunks) finish(sent, chunk.id)
    }

    private fun finish(sent: FakeMessaging.Sent, id: String) {
        val download = transfers.remove(id) ?: return
        val hash =
            MessageDigest.getInstance("SHA-256").digest(download.bytes).joinToString("") {
                "%02x".format(it)
            }
        send(
            sent,
            if (hash == download.offer.sha256) Envelope.Done(id)
            else Envelope.Fail(id, FailReasons.HASH),
        )
    }

    private fun hello(sent: FakeMessaging.Sent) {
        if (greeted) return
        greeted = true
        send(sent, Envelope.Hi)
    }

    private fun send(sent: FakeMessaging.Sent, envelope: Envelope) =
        reply(sent, EnvelopeCodec.encode(envelope))

    private fun reply(sent: FakeMessaging.Sent, body: String) {
        messaging.deliver(
            from = SipAddress(sent.to.user, sent.to.host),
            body = body,
            account = sent.account,
            name =
                context.getString(
                    if (sent.to.user == SONA) R.string.demo_sona_name else R.string.demo_plain_name,
                ),
            atMillis = System.currentTimeMillis(),
        )
    }

    private fun later(
        sent: FakeMessaging.Sent,
        after: Duration = Duration.ZERO,
        block: suspend () -> Unit,
    ) {
        scope.launch {
            delay(after)
            block()
        }
    }

    private companion object {
        const val SONA = "100"
        const val PLAIN = "101"
        val ServerTime = 120.milliseconds
        const val ACK_EVERY = 64
        const val MAX_DEMO_FILE = 20L * 1024 * 1024
    }
}
