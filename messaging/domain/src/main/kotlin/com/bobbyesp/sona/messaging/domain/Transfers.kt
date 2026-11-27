/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.domain

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.core.model.settings.MessageSettings
import com.bobbyesp.sona.core.model.settings.SettingsStore
import com.bobbyesp.sona.messaging.domain.envelope.Envelope
import com.bobbyesp.sona.messaging.domain.envelope.EnvelopeCodec
import com.bobbyesp.sona.messaging.domain.envelope.FailReasons
import com.bobbyesp.sona.messaging.domain.envelope.FileOffer
import com.bobbyesp.sona.sip.api.MessageSendResult
import java.util.BitSet
import java.util.TreeSet
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Files over `MESSAGE`, both ways, as docs/messaging.md, Files, describes: the offer is the
 * [Messenger]'s (it goes out in order with the texts), the rest is here.
 *
 * - **Sending**: once accepted, chunks go out with a window of [WINDOW] in flight, the next when
 *   the server answered one; a chunk the server refuses for a while (`408`, `503`, no network) is
 *   sent again with back-off. How far it got is saved, so a restart goes on from there.
 * - **Receiving**: each chunk is written at its place in a file of its own, and which ones arrived
 *   is saved; when none come for [IDLE] the missing ones are asked for again, [MISS_TRIES] times
 *   before the transfer fails. Whole, its SHA-256 is checked before it is the conversation's.
 *
 * Runs in memory are keyed by message ([outgoing]) and by conversation and transfer id
 * ([incoming]): a transfer id is the sender's choice, and two peers may choose the same.
 */
internal class Transfers(
    private val store: MessageStore,
    private val files: AttachmentFiles,
    private val courier: Courier,
    private val lock: Mutex,
    private val scope: CoroutineScope,
    private val settings: SettingsStore,
    private val clock: () -> Long,
    private val sizeLimit: suspend (AccountId) -> Int,
    private val received: suspend (Conversation, Message) -> Unit,
    private val offerAgain: suspend (MessageId) -> Unit,
) {
    private val outgoing = ConcurrentHashMap<MessageId, Outgoing>()
    private val incoming = ConcurrentHashMap<String, Incoming>()

    /** When each finished transfer last reminded a sender that kept sending, by key. */
    private val reminded = ConcurrentHashMap<String, Long>()

    /** The description an offer gives of [attachment]. */
    fun offerOf(attachment: Attachment) =
        FileOffer(
            id = attachment.transferId,
            name = attachment.name,
            mime = attachment.mime,
            size = attachment.size,
            sha256 = attachment.sha256,
            chunk = attachment.chunkSize,
            chunks = attachment.chunks,
            width = attachment.width,
            height = attachment.height,
        )

    // --- What the other side says about a file sent from here -----------------------------------

    suspend fun accepted(conversation: Conversation, id: String) {
        val message = sent(conversation, id) ?: return
        val attachment = message.attachment ?: return
        when (attachment.state) {
            TransferState.Cancelled -> remind(conversation, id, Envelope.Cancel(id))
            TransferState.Declined,
            TransferState.Done -> Unit
            else -> stream(conversation, message, from = attachment.done)
        }
    }

    suspend fun missing(conversation: Conversation, id: String, ranges: List<IntRange>) {
        val message = sent(conversation, id) ?: return
        val attachment = message.attachment ?: return
        when (attachment.state) {
            TransferState.Cancelled -> remind(conversation, id, Envelope.Cancel(id))
            TransferState.Declined,
            TransferState.Done -> Unit
            // Asking for chunks is accepting: the accept itself may have been lost.
            TransferState.Offering,
            TransferState.WaitingForPeer ->
                stream(conversation, message, from = attachment.done, repairs = ranges)
            else -> stream(conversation, message, from = attachment.chunks, repairs = ranges)
        }
    }

    suspend fun acked(conversation: Conversation, id: String, count: Int) {
        val message = sent(conversation, id) ?: return
        changeAttachment(message.id) {
            if (it.state.isFinished) it
            else it.copy(peerReceived = maxOf(it.peerReceived, count.coerceAtMost(it.chunks)))
        }
    }

    suspend fun done(conversation: Conversation, id: String) {
        val message = sent(conversation, id) ?: return
        outgoing.remove(message.id)?.stop()
        change(message.id) {
            val attachment = it.attachment ?: return@change it
            it.copy(
                status = it.status.atLeast(MessageStatus.Delivered),
                failure = null,
                attachment =
                    attachment.copy(
                        state = TransferState.Done,
                        done = attachment.chunks,
                        peerReceived = attachment.chunks,
                    ),
            )
        }
    }

    suspend fun failed(conversation: Conversation, id: String, reason: String) {
        val message = sent(conversation, id) ?: return
        outgoing.remove(message.id)?.stop()
        val failure =
            when (reason) {
                FailReasons.HASH -> MessageFailure(FailureReason.Corrupted)
                FailReasons.TIMEOUT -> MessageFailure(FailureReason.TimedOut)
                else -> MessageFailure(FailureReason.PeerFailed, detail = reason)
            }
        change(message.id) {
            val attachment = it.attachment ?: return@change it
            if (attachment.state.isFinished && attachment.state != TransferState.Failed) it
            else it.failed(failure)
        }
    }

    suspend fun declined(conversation: Conversation, id: String) {
        val message = sent(conversation, id) ?: return
        outgoing.remove(message.id)?.stop()
        changeAttachment(message.id) {
            if (it.state == TransferState.Done) it else it.copy(state = TransferState.Declined)
        }
    }

    /** Either side stopped: the sender of a file coming here, or the receiver of one sent. */
    suspend fun cancelled(conversation: Conversation, id: String) {
        val coming = store.messageByEnvelope(conversation.id, MessageDirection.Incoming, id)
        if (coming?.attachment != null) {
            incoming.remove(keyOf(conversation.id, id))?.stop()
            files.discard(fileKeyOf(coming.id))
            changeAttachment(coming.id) {
                if (it.state.isFinished) it else it.copy(state = TransferState.Cancelled)
            }
            return
        }
        val message = sent(conversation, id) ?: return
        outgoing.remove(message.id)?.stop()
        changeAttachment(message.id) {
            if (it.state.isFinished) it else it.copy(state = TransferState.Cancelled)
        }
    }

    private suspend fun sent(conversation: Conversation, id: String): Message? =
        store.messageByEnvelope(conversation.id, MessageDirection.Outgoing, id)?.takeIf {
            it.attachment != null
        }

    /**
     * Sends [message]'s chunks from [from] on (with [repairs] first), unless they are going out
     * already: then only [repairs] are added.
     */
    private suspend fun stream(
        conversation: Conversation,
        message: Message,
        from: Int,
        repairs: List<IntRange> = emptyList(),
    ) {
        val attachment = message.attachment ?: return
        if (attachment.file == null) {
            change(message.id) { it.failed(MessageFailure(FailureReason.FileMissing)) }
            return
        }
        var created = false
        val run =
            outgoing.computeIfAbsent(message.id) {
                created = true
                Outgoing(message.id, conversation, attachment, from.coerceIn(0, attachment.chunks))
            }
        change(message.id) {
            val now = it.attachment ?: return@change it
            if (now.state.isFinished && now.state != TransferState.Failed) return@change it
            it.copy(
                status = it.status.atLeast(MessageStatus.Sent),
                failure = null,
                partsSent = maxOf(it.partsSent, 1),
                attachment = now.copy(state = TransferState.Sending),
            )
        }
        if (repairs.isNotEmpty()) run.repair(repairs)
        if (created) run.start()
    }

    /** A file going out: which chunks are left, which are in flight, how far it got. */
    private inner class Outgoing(
        val messageId: MessageId,
        val conversation: Conversation,
        val attachment: Attachment,
        start: Int,
    ) {
        private val chunks = attachment.chunks
        private val taken = BitSet(chunks).apply { if (start > 0) set(0, start) }
        private val repairs = TreeSet<Int>()
        private val inFlight = HashSet<Int>()
        private var cursor = start
        private var watermark = start
        private var saved = start
        private var savedAt = clock()
        private val wake = Channel<Unit>(Channel.CONFLATED)
        private var job: Job? = null

        fun start() {
            job = scope.launch { stream() }
        }

        fun stop() {
            job?.cancel()
        }

        fun repair(ranges: List<IntRange>) {
            synchronized(this) {
                for (range in ranges) {
                    val first = range.first.coerceAtLeast(0)
                    val last = range.last.coerceAtMost(chunks - 1)
                    for (index in first..last) repairs.add(index)
                }
            }
            wake.trySend(Unit)
        }

        private fun next(): Int? {
            while (true) {
                val index = repairs.pollFirst() ?: break
                if (inFlight.add(index)) return index
            }
            while (cursor < chunks) {
                val index = cursor++
                if (!taken[index] && inFlight.add(index)) return index
            }
            return null
        }

        private suspend fun stream() = coroutineScope {
            val window = Semaphore(WINDOW)
            var confirming = false
            while (true) {
                window.acquire()
                val index = synchronized(this@Outgoing) { next() }
                if (index == null) {
                    window.release()
                    val idle = synchronized(this@Outgoing) { inFlight.isEmpty() }
                    if (idle && !confirming) {
                        confirming = true
                        allSent()
                    }
                    wake.receive()
                    continue
                }
                confirming = false
                launch {
                    try {
                        send(index)
                    } finally {
                        synchronized(this@Outgoing) { inFlight.remove(index) }
                        window.release()
                        wake.trySend(Unit)
                    }
                }
            }
        }

        private suspend fun send(index: Int) {
            val offset = index.toLong() * attachment.chunkSize
            val length = minOf(attachment.chunkSize.toLong(), attachment.size - offset).toInt()
            val bytes = attachment.file?.let { files.read(it, offset, length) }
            if (bytes == null || bytes.size != length) {
                return fail(MessageFailure(FailureReason.FileMissing))
            }
            val body = EnvelopeCodec.encode(Envelope.Chunk(attachment.transferId, index, bytes))
            var attempt = 0
            while (true) {
                val result = courier.send(conversation.account, conversation.peer, body)
                if (result is MessageSendResult.Accepted) return taken(index)
                val failure = result.toFailure() ?: return
                if (!result.isTransient || attempt >= CHUNK_RETRIES) return fail(failure)
                if (result == MessageSendResult.AccountUnavailable) {
                    courier.awaitRegistered(conversation.account, Courier.backoff(attempt) * 4)
                } else {
                    delay(Courier.backoff(attempt))
                }
                attempt++
            }
        }

        private suspend fun taken(index: Int) {
            val save =
                synchronized(this) {
                    taken.set(index)
                    while (watermark < chunks && taken[watermark]) watermark++
                    val now = clock()
                    val due =
                        watermark - saved >= SAVE_EVERY_CHUNKS ||
                            now - savedAt >= SAVE_EVERY_MS ||
                            watermark == chunks
                    if (due && watermark > saved) {
                        saved = watermark
                        savedAt = now
                        watermark
                    } else {
                        null
                    }
                }
            if (save != null) {
                changeAttachment(messageId) {
                    if (it.state.isFinished) it else it.copy(done = maxOf(it.done, save))
                }
            }
        }

        private suspend fun allSent() {
            changeAttachment(messageId) {
                if (it.state != TransferState.Sending) it
                else it.copy(state = TransferState.Confirming, done = chunks)
            }
        }

        private suspend fun fail(failure: MessageFailure) {
            if (!outgoing.remove(messageId, this)) return
            change(messageId) {
                if (it.attachment?.state?.isFinished == true) it else it.failed(failure)
            }
            stop()
        }
    }

    // --- A file offered here ---------------------------------------------------------------------

    suspend fun offered(conversation: Conversation, offer: FileOffer, at: Long) {
        val known = store.messageByEnvelope(conversation.id, MessageDirection.Incoming, offer.id)
        if (known != null) return offeredAgain(conversation, known)
        val message =
            lock.withLock {
                store.add(
                    Message(
                        conversation = conversation.id,
                        direction = MessageDirection.Incoming,
                        text = "",
                        at = at,
                        status = MessageStatus.Received,
                        envelopeId = offer.id,
                        attachment =
                            Attachment(
                                transferId = offer.id,
                                name = offer.name,
                                mime = offer.mime,
                                size = offer.size,
                                sha256 = offer.sha256,
                                chunkSize = offer.chunk,
                                chunks = offer.chunks,
                                width = offer.width,
                                height = offer.height,
                                state = TransferState.Offered,
                            ),
                    ),
                )
            }
        received(conversation, message)
        if (downloadsByItself(offer.size)) accept(conversation, message)
    }

    /** The sender offered again what it offered before: it did not hear, or it starts over. */
    private suspend fun offeredAgain(conversation: Conversation, message: Message) {
        val attachment = message.attachment ?: return
        val id = attachment.transferId
        when (attachment.state) {
            TransferState.Receiving -> remind(conversation, id, Envelope.Accept(id))
            TransferState.Done -> remind(conversation, id, Envelope.Done(id))
            TransferState.Declined -> remind(conversation, id, Envelope.Decline(id))
            TransferState.Failed,
            TransferState.Cancelled -> {
                files.discard(fileKeyOf(message.id))
                lock.withLock { store.saveReceivedChunks(message.id, ByteArray(0), 0) }
                val reset =
                    change(message.id) {
                        it.copy(
                            failure = null,
                            attachment = attachment.copy(state = TransferState.Offered, done = 0),
                        )
                    } ?: return
                if (downloadsByItself(attachment.size)) accept(conversation, reset)
            }
            else -> Unit
        }
    }

    private suspend fun downloadsByItself(size: Long): Boolean {
        val limit = settings.observe(MessageSettings.AutoDownload).first().bytes
        return limit > 0 && size <= limit
    }

    /** The user tapped *Download* on a file larger than the automatic limit. */
    suspend fun download(id: MessageId) {
        val message = store.message(id) ?: return
        if (message.outgoing || message.attachment?.state != TransferState.Offered) return
        val conversation = store.conversation(message.conversation) ?: return
        accept(conversation, message)
    }

    suspend fun decline(id: MessageId) {
        val message = store.message(id) ?: return
        val attachment = message.attachment ?: return
        if (message.outgoing || attachment.state != TransferState.Offered) return
        val conversation = store.conversation(message.conversation) ?: return
        changeAttachment(id) { it.copy(state = TransferState.Declined) }
        tell(conversation, Envelope.Decline(attachment.transferId))
    }

    /** Takes the file: makes room for it, says so, and waits for its chunks. */
    private suspend fun accept(conversation: Conversation, message: Message) {
        val attachment = message.attachment ?: return
        val id = attachment.transferId
        if (!files.openIncoming(fileKeyOf(message.id), attachment.size)) {
            change(message.id) { it.failed(MessageFailure(FailureReason.Storage)) }
            tell(conversation, Envelope.Fail(id, FailReasons.STORAGE))
            return
        }
        val now =
            change(message.id) {
                it.copy(failure = null, attachment = attachment.copy(state = TransferState.Receiving))
            } ?: return
        tell(conversation, Envelope.Accept(id))
        receive(conversation, now, BitSet())
    }

    suspend fun chunk(conversation: Conversation, chunk: Envelope.Chunk) {
        incoming[keyOf(conversation.id, chunk.id)]?.let {
            it.take(chunk)
            return
        }
        val message =
            store.messageByEnvelope(conversation.id, MessageDirection.Incoming, chunk.id)
        val attachment = message?.attachment
        if (message == null || attachment == null) {
            return stranger(conversation.account, conversation.peer, chunk.id)
        }
        val id = attachment.transferId
        when (attachment.state) {
            TransferState.Receiving -> resumeReceiving(conversation, message)?.take(chunk)
            // The sender went on after a timeout here: so does this side, with what it has.
            TransferState.Failed ->
                if (message.failure?.reason == FailureReason.TimedOut) {
                    resumeReceiving(conversation, message)?.take(chunk)
                }
            TransferState.Done -> remind(conversation, id, Envelope.Done(id))
            TransferState.Cancelled -> remind(conversation, id, Envelope.Cancel(id))
            TransferState.Declined -> remind(conversation, id, Envelope.Decline(id))
            else -> Unit
        }
    }

    /** A chunk of a transfer nobody here knows of: its sender is asked, once, to stop. */
    fun stranger(account: AccountId, peer: SipAddress, id: String) {
        val key = "$account|$peer|$id"
        if (reminded.putIfAbsent(key, clock()) == null) {
            courier.later(account, peer, Envelope.Cancel(id), Courier.Once)
        }
    }

    /** Goes on receiving [message] with the chunks saved. */
    private suspend fun resumeReceiving(conversation: Conversation, message: Message): Incoming? {
        val attachment = message.attachment ?: return null
        if (!files.openIncoming(fileKeyOf(message.id), attachment.size)) return null
        val bits = store.receivedChunks(message.id)?.let(BitSet::valueOf) ?: BitSet()
        val now =
            change(message.id) {
                it.copy(
                    failure = null,
                    attachment =
                        attachment.copy(
                            state = TransferState.Receiving,
                            done = bits.cardinality(),
                        ),
                )
            } ?: return null
        return receive(conversation, now, bits)
    }

    private suspend fun receive(conversation: Conversation, message: Message, bits: BitSet): Incoming {
        val attachment = message.attachment!!
        var created = false
        val run =
            incoming.computeIfAbsent(keyOf(conversation.id, attachment.transferId)) {
                created = true
                Incoming(message.id, conversation, attachment, bits)
            }
        if (created) run.start()
        return run
    }

    /** A file coming in: which chunks are here, and the timers that ask for the rest. */
    private inner class Incoming(
        val messageId: MessageId,
        val conversation: Conversation,
        val attachment: Attachment,
        private val bits: BitSet,
    ) {
        private val key = keyOf(conversation.id, attachment.transferId)
        private val file = fileKeyOf(messageId)
        private var count = bits.cardinality()
        private var dirty = false
        private var acked = count
        private var whole = false
        private val activity = Channel<Unit>(Channel.CONFLATED)
        private var job: Job? = null

        fun start() {
            job = scope.launch {
                launch { watch() }
                launch { tick() }
            }
            // Nothing to wait for: an empty file, or one that was whole when the app stopped.
            if (count >= attachment.chunks) scope.launch { complete() }
        }

        fun stop() {
            job?.cancel()
        }

        /** Asks for what is missing whenever nothing came for a while; gives up after a few. */
        private suspend fun watch() {
            var tries = 0
            while (true) {
                if (withTimeoutOrNull(IDLE) { activity.receive() } != null) {
                    tries = 0
                    continue
                }
                if (tries >= MISS_TRIES) return fail(FailureReason.TimedOut, FailReasons.TIMEOUT)
                // A request that could not even be sent is not one the sender left unanswered.
                if (ask()) tries++
            }
        }

        /** Asks for the missing chunks at once: the user tried again. */
        fun askNow() {
            scope.launch { ask() }
        }

        /** Asks for as many of the missing chunks as one message can; whether the server took it. */
        private suspend fun ask(): Boolean {
            val ranges = synchronized(this) { missing() }
            if (ranges.isEmpty()) return false
            val limit = sizeLimit(conversation.account)
            val miss = EnvelopeCodec.missFitting(attachment.transferId, ranges, limit)
            val result =
                courier.send(conversation.account, conversation.peer, EnvelopeCodec.encode(miss))
            return result is MessageSendResult.Accepted
        }

        /** Every second, saves which chunks are here; every two, tells the sender how many. */
        private suspend fun tick() {
            var seconds = 0
            while (true) {
                delay(1.seconds)
                seconds++
                save()
                val progress =
                    synchronized(this) {
                        if (seconds % 2 == 0 && count != acked) count.also { acked = it } else null
                    }
                if (progress != null) {
                    courier.later(
                        conversation.account,
                        conversation.peer,
                        Envelope.Ack(attachment.transferId, progress),
                        Courier.Once,
                    )
                }
            }
        }

        private suspend fun save() {
            val snapshot =
                synchronized(this) {
                    if (!dirty) return
                    dirty = false
                    bits.toByteArray() to count
                }
            lock.withLock { store.saveReceivedChunks(messageId, snapshot.first, snapshot.second) }
        }

        /** The chunks still missing, as ranges, in order. */
        private fun missing(): List<IntRange> {
            val ranges = mutableListOf<IntRange>()
            var index = bits.nextClearBit(0)
            while (index < attachment.chunks) {
                val end = (bits.nextSetBit(index).takeIf { it >= 0 } ?: attachment.chunks)
                    .coerceAtMost(attachment.chunks)
                ranges += index until end
                index = bits.nextClearBit(end)
            }
            return ranges
        }

        suspend fun take(chunk: Envelope.Chunk) {
            val index = chunk.index
            if (index !in 0 until attachment.chunks) return
            val offset = index.toLong() * attachment.chunkSize
            val expected = minOf(attachment.chunkSize.toLong(), attachment.size - offset).toInt()
            if (chunk.bytes.size != expected) return
            activity.trySend(Unit)
            if (synchronized(this) { bits[index] }) return
            if (!files.write(file, offset, chunk.bytes)) {
                if (incoming[key] === this) fail(FailureReason.Storage, FailReasons.STORAGE)
                return
            }
            val complete =
                synchronized(this) {
                    if (bits[index]) return
                    bits.set(index)
                    count++
                    dirty = true
                    count >= attachment.chunks
                }
            if (complete) complete()
        }

        /** Every chunk came: saved, and the file checked. */
        private suspend fun complete() {
            synchronized(this) {
                if (whole) return
                whole = true
                dirty = true
            }
            if (!incoming.remove(key, this)) return
            stop()
            save()
            val now =
                changeAttachment(messageId) {
                    it.copy(state = TransferState.Verifying, done = attachment.chunks)
                } ?: return
            scope.launch { verify(conversation, now) }
        }

        private suspend fun fail(reason: FailureReason, wire: String) {
            if (!incoming.remove(key, this)) return
            save()
            change(messageId) { it.failed(MessageFailure(reason)) }
            tell(conversation, Envelope.Fail(attachment.transferId, wire))
            stop()
        }
    }

    /** Checks the SHA-256 of a file whose chunks all came, and keeps it, or says why not. */
    private suspend fun verify(conversation: Conversation, message: Message) {
        val attachment = message.attachment ?: return
        val id = attachment.transferId
        val key = fileKeyOf(message.id)
        val hash = files.sha256(key)
        if (hash == null) {
            change(message.id) { it.failed(MessageFailure(FailureReason.Storage)) }
            tell(conversation, Envelope.Fail(id, FailReasons.STORAGE))
            return
        }
        if (hash != attachment.sha256) {
            files.discard(key)
            lock.withLock { store.saveReceivedChunks(message.id, ByteArray(0), 0) }
            change(message.id) {
                it.failed(MessageFailure(FailureReason.Corrupted, detail = "SHA-256 $hash"))
            }
            tell(conversation, Envelope.Fail(id, FailReasons.HASH))
            return
        }
        val path = files.finish(key, attachment.name)
        if (path == null) {
            change(message.id) { it.failed(MessageFailure(FailureReason.Storage)) }
            tell(conversation, Envelope.Fail(id, FailReasons.STORAGE))
            return
        }
        changeAttachment(message.id) {
            it.copy(state = TransferState.Done, done = it.chunks, file = path)
        }
        tell(conversation, Envelope.Done(id))
    }

    // --- What the user does ----------------------------------------------------------------------

    /** Tries a failed transfer again, sent or received. */
    suspend fun retry(message: Message) {
        val attachment = message.attachment ?: return
        if (attachment.state != TransferState.Failed) return
        val conversation = store.conversation(message.conversation) ?: return
        if (!message.outgoing) return retryReceiving(conversation, message)
        when (message.failure?.reason) {
            FailureReason.FileMissing -> Unit
            // What arrived was wrong, or could not be kept: offered again, from the start.
            FailureReason.Corrupted,
            FailureReason.PeerFailed -> {
                change(message.id) {
                    it.copy(
                        status = MessageStatus.Sending,
                        failure = null,
                        partsSent = 0,
                        attachment =
                            attachment.copy(
                                state = TransferState.Offering,
                                done = 0,
                                peerReceived = 0,
                            ),
                    )
                }
                offerAgain(message.id)
            }
            else -> stream(conversation, message, from = attachment.done)
        }
    }

    private suspend fun retryReceiving(conversation: Conversation, message: Message) {
        val attachment = message.attachment ?: return
        val id = attachment.transferId
        if (message.failure?.reason != FailureReason.TimedOut) {
            // What was received cannot be trusted, or kept: from the start.
            files.discard(fileKeyOf(message.id))
            lock.withLock { store.saveReceivedChunks(message.id, ByteArray(0), 0) }
        }
        val run = resumeReceiving(conversation, message) ?: return
        tell(conversation, Envelope.Accept(id))
        run.askNow()
    }

    /** Stops a transfer this side does not want any more; the other side is told. */
    suspend fun cancel(id: MessageId) {
        val message = store.message(id) ?: return
        val attachment = message.attachment ?: return
        if (attachment.state.isFinished) return
        val conversation = store.conversation(message.conversation) ?: return
        stopRuns(conversation, message)
        if (!message.outgoing && attachment.state == TransferState.Offered) {
            changeAttachment(id) { it.copy(state = TransferState.Declined) }
            tell(conversation, Envelope.Decline(attachment.transferId))
            return
        }
        if (!message.outgoing) files.discard(fileKeyOf(id))
        val offerUnsent = message.outgoing && message.partsSent == 0
        change(id) {
            it.copy(
                status = if (it.status == MessageStatus.Sending) MessageStatus.Sent else it.status,
                attachment = it.attachment?.copy(state = TransferState.Cancelled),
            )
        }
        // An offer that never went out needs no word.
        if (!offerUnsent) tell(conversation, Envelope.Cancel(attachment.transferId))
    }

    /** A message is being deleted: its transfer stops, and its files go. */
    suspend fun forget(message: Message) {
        val attachment = message.attachment ?: return
        val conversation = store.conversation(message.conversation)
        if (conversation != null) {
            stopRuns(conversation, message)
            val id = attachment.transferId
            when {
                attachment.state.isFinished -> Unit
                !message.outgoing && attachment.state == TransferState.Offered ->
                    tell(conversation, Envelope.Decline(id))
                !message.outgoing || message.partsSent > 0 ->
                    tell(conversation, Envelope.Cancel(id))
            }
        }
        if (!message.outgoing) files.discard(fileKeyOf(message.id))
        attachment.file?.let { files.delete(it) }
    }

    private fun stopRuns(conversation: Conversation, message: Message) {
        val attachment = message.attachment ?: return
        if (message.outgoing) outgoing.remove(message.id)?.stop()
        else incoming.remove(keyOf(conversation.id, attachment.transferId))?.stop()
    }

    // --- After a restart -------------------------------------------------------------------------

    suspend fun resume(message: Message) {
        val attachment = message.attachment ?: return
        val conversation = store.conversation(message.conversation) ?: return
        if (message.outgoing) {
            when (attachment.state) {
                TransferState.Sending -> stream(conversation, message, from = attachment.done)
                // Every chunk went out: wait for the receiver's word, or its requests.
                TransferState.Confirming ->
                    stream(conversation, message, from = attachment.chunks)
                else -> Unit
            }
            return
        }
        when (attachment.state) {
            TransferState.Offered ->
                if (downloadsByItself(attachment.size)) accept(conversation, message)
            TransferState.Receiving -> resumeReceiving(conversation, message)
            TransferState.Verifying -> verify(conversation, message)
            else -> Unit
        }
    }

    // --- Shared ----------------------------------------------------------------------------------

    private fun tell(conversation: Conversation, envelope: Envelope) =
        courier.later(conversation.account, conversation.peer, envelope, Courier.Important)

    /** Tells a sender that keeps sending how its transfer ended, at most every few seconds. */
    private fun remind(conversation: Conversation, id: String, envelope: Envelope) {
        val key = keyOf(conversation.id, id) + "|" + envelope.javaClass.simpleName
        val now = clock()
        val last = reminded[key]
        if (last != null && now - last < REMIND_EVERY_MS) return
        reminded[key] = now
        courier.later(conversation.account, conversation.peer, envelope, Courier.Once)
    }

    private suspend fun change(id: MessageId, block: (Message) -> Message): Message? =
        lock.withLock {
            val now = store.message(id) ?: return@withLock null
            val next = block(now)
            if (next != now) store.update(next)
            next
        }

    private suspend fun changeAttachment(id: MessageId, block: (Attachment) -> Attachment) =
        change(id) { message -> message.attachment?.let { message.copy(attachment = block(it)) } ?: message }

    private fun Message.failed(failure: MessageFailure): Message =
        copy(
            status = if (outgoing) MessageStatus.Failed else status,
            failure = failure,
            attachment = attachment?.copy(state = TransferState.Failed),
        )

    private fun keyOf(conversation: ConversationId, transferId: String) = "$conversation/$transferId"

    companion object {
        /** Chunks in flight at once. */
        const val WINDOW = 8

        /** Nothing for this long, and the receiver asks for what is missing. */
        val IDLE = 8.seconds

        /** Requests for missing chunks left unanswered before a transfer fails. */
        const val MISS_TRIES = 5

        /** Tries of one chunk the server does not take (about two minutes of back-off). */
        const val CHUNK_RETRIES = 7

        const val SAVE_EVERY_CHUNKS = 64
        const val SAVE_EVERY_MS = 1_000L
        const val REMIND_EVERY_MS = 5_000L

        /** The name an incoming file has in storage while it is not whole. */
        fun fileKeyOf(message: MessageId) = "in-${message.value}"
    }
}
