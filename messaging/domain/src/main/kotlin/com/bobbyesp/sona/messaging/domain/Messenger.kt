/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.domain

import com.bobbyesp.sona.accounts.domain.Account
import com.bobbyesp.sona.accounts.domain.AccountsRepository
import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.core.model.settings.EffectiveSettings
import com.bobbyesp.sona.core.model.settings.MessageSettings
import com.bobbyesp.sona.core.model.settings.Overrides
import com.bobbyesp.sona.core.model.settings.SettingsStore
import com.bobbyesp.sona.messaging.domain.envelope.Envelope
import com.bobbyesp.sona.messaging.domain.envelope.EnvelopeCodec
import com.bobbyesp.sona.messaging.domain.envelope.FileNames
import com.bobbyesp.sona.sip.api.ReceivedMessage
import com.bobbyesp.sona.sip.api.SipEngine
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one place that sends, receives and keeps texts and files over SIP `MESSAGE`
 * (docs/messaging.md). It lives in the application's scope: [start] it once, **before the engine**,
 * so nothing received waits unread.
 *
 * - Plain peers get the text as typed, in parts if long. Sona peers (they sent an envelope) also
 *   get ids, receipts and typing as the settings allow, and only they take files.
 * - What is unfinished is in the store, and resumed after a restart.
 *
 * Changes to messages go through one lock, which never waits for the network. A conversation's
 * texts go out one at a time, in order.
 *
 * @param clock The time in epoch milliseconds.
 * @param newId The Sona ids of texts and files.
 */
class Messenger(
    private val engine: SipEngine,
    private val store: MessageStore,
    private val files: AttachmentFiles,
    private val preparer: AttachmentPreparer,
    private val accounts: AccountsRepository,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    names: PeerNames = PeerNames.None,
    private val captions: OfferCaption = OfferCaption.English,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { EnvelopeCodec.newId() },
) {
    private val lock = Mutex()
    private val courier = Courier(engine, scope)
    private val effective = EffectiveSettings(settings)

    private val accountList: StateFlow<List<Account>?> =
        accounts.accounts.stateIn(scope, SharingStarted.Eagerly, null)

    private val nameOf: StateFlow<(SipAddress) -> String?> =
        names.lookups().stateIn(scope, SharingStarted.Eagerly) { null }

    private val _events = MutableSharedFlow<MessengerEvent>(extraBufferCapacity = 64)

    /** New messages as they arrive, for notifications. Dropped while nobody listens. */
    val events: SharedFlow<MessengerEvent> = _events.asSharedFlow()

    private val visible = MutableStateFlow<ConversationId?>(null)

    private val transfers =
        Transfers(
            store = store,
            files = files,
            courier = courier,
            lock = lock,
            scope = scope,
            settings = settings,
            clock = clock,
            sizeLimit = ::sizeLimit,
            received = ::announce,
            offerAgain = { deliver(it, resumed = false) },
        )

    val conversations: Flow<List<ConversationSummary>> =
        combine(store.conversations, nameOf) { list, name ->
            list.map { it.copy(name = name(it.conversation.peer) ?: it.conversation.peerName) }
        }

    fun conversation(id: ConversationId): Flow<ConversationSummary?> =
        conversations.map { list -> list.firstOrNull { it.id == id } }.distinctUntilChanged()

    fun messages(id: ConversationId): Flow<List<Message>> = store.messages(id)

    val totalUnread: Flow<Int> = store.unread

    private val typingPeers = MutableStateFlow<Set<ConversationId>>(emptySet())
    private val typingJobs = ConcurrentHashMap<ConversationId, Job>()
    private val typingSent = ConcurrentHashMap<ConversationId, Long>()

    /** Whether the peer of [id] is writing; shown for 6 seconds after it says so. */
    fun typing(id: ConversationId): Flow<Boolean> =
        typingPeers.map { id in it }.distinctUntilChanged()

    /** Delivered receipts waiting to go together. Touched under [lock]. */
    private val deliveredQueue = mutableMapOf<ConversationId, MutableSet<String>>()

    private val outboxes = ConcurrentHashMap<ConversationId, Mutex>()

    /**
     * Receives, and resumes what was left unfinished. Once, at app start, before the engine starts.
     */
    fun start(): Job = scope.launch {
        launch {
            engine.messaging.received.collect { message ->
                // One body that cannot be handled must not stop the ones after it.
                runCatching { receive(message) }
            }
        }
        launch { resume() }
    }

    /** The conversation with [peer], made if needed; `null` when the account does not exist. */
    suspend fun open(account: AccountId, peer: SipAddress): ConversationId? {
        val owner = accountList.filterNotNull().first().firstOrNull { it.id == account }
        owner ?: return null
        return lock
            .withLock { store.openConversation(account, peerOf(peer, owner), null, clock()) }
            .id
    }

    /** [id] is the conversation on screen, if any: what arrives in it is read at once. */
    fun setVisibleConversation(id: ConversationId?) {
        visible.value = id
        if (id != null) scope.launch { markRead(id) }
    }

    // --- Texts -----------------------------------------------------------------------------------

    suspend fun sendText(conversation: ConversationId, text: String): SendOutcome {
        if (text.isBlank()) return SendOutcome.Empty
        val owner = store.conversation(conversation) ?: return SendOutcome.NoConversation
        val limit = sizeLimit(owner.account)
        val id = if (owner.peerKind == PeerKind.Sona) newId() else null
        val parts =
            EnvelopeCodec.textParts(text, limit, id)
                ?: return SendOutcome.TooLong(text.encodeToByteArray().size, limit)
        stoppedTyping(conversation, sent = true)
        val message = lock.withLock {
            store.add(
                Message(
                    conversation = conversation,
                    direction = MessageDirection.Outgoing,
                    text = text,
                    at = clock(),
                    status = MessageStatus.Sending,
                    envelopeId = id,
                    parts = parts.size,
                    sizeLimit = limit,
                ),
            )
        }
        scope.launch { deliver(message.id, resumed = false) }
        return SendOutcome.Queued(message.id)
    }

    /**
     * Offers the file at [source], a `content://` address the user picked, and sends it once the
     * other phone accepts.
     */
    suspend fun sendFile(conversation: ConversationId, source: String): SendOutcome {
        val owner = store.conversation(conversation) ?: return SendOutcome.NoConversation
        val limit = settings.observe(MessageSettings.LargestAttachment).first().bytes
        val original = settings.observe(MessageSettings.OriginalQualityPictures).first()
        val ready =
            when (val prepared = preparer.prepare(source, original, limit)) {
                is PreparedAttachment.Ready -> prepared
                is PreparedAttachment.TooLarge ->
                    return SendOutcome.FileTooLarge(prepared.size, prepared.limit)
                is PreparedAttachment.Unreadable ->
                    return SendOutcome.FileUnreadable(prepared.detail)
            }
        val sizeLimit = sizeLimit(owner.account)
        val chunk = EnvelopeCodec.chunkSize(sizeLimit)
        val id = newId()
        val message = lock.withLock {
            store.add(
                Message(
                    conversation = conversation,
                    direction = MessageDirection.Outgoing,
                    text = "",
                    at = clock(),
                    status = MessageStatus.Sending,
                    envelopeId = id,
                    sizeLimit = sizeLimit,
                    attachment =
                        Attachment(
                            transferId = id,
                            name = FileNames.safe(ready.name),
                            mime = ready.mime,
                            size = ready.size,
                            sha256 = ready.sha256,
                            chunkSize = chunk,
                            chunks = EnvelopeCodec.chunksOf(ready.size, chunk),
                            width = ready.width,
                            height = ready.height,
                            state = TransferState.Offering,
                            file = ready.file,
                        ),
                ),
            )
        }
        scope.launch { deliver(message.id, resumed = false) }
        return SendOutcome.Queued(message.id)
    }

    /**
     * A text or an offer is sent again from the part that failed; a file transfer resumes where it
     * stopped.
     */
    suspend fun retry(id: MessageId) {
        val message = store.message(id) ?: return
        // A file whose offer went out, or one received, is the transfer's to resume.
        if (message.attachment != null && (!message.outgoing || message.partsSent > 0)) {
            transfers.retry(message)
            return
        }
        if (message.status != MessageStatus.Failed) return
        lock.withLock {
            val now = store.message(id) ?: return
            store.update(
                now.copy(
                    status = MessageStatus.Sending,
                    failure = null,
                    attachment = now.attachment?.copy(state = TransferState.Offering),
                ),
            )
        }
        deliver(id, resumed = false)
    }

    suspend fun download(id: MessageId) = transfers.download(id)

    suspend fun decline(id: MessageId) = transfers.decline(id)

    suspend fun cancel(id: MessageId) = transfers.cancel(id)

    /** A transfer in progress is stopped first. */
    suspend fun delete(ids: Collection<MessageId>) {
        for (id in ids) store.message(id)?.let { transfers.forget(it) }
        lock.withLock { store.delete(ids) }
    }

    suspend fun deleteConversation(id: ConversationId) {
        store.messages(id).first().forEach { transfers.forget(it) }
        lock.withLock { store.deleteConversation(id) }
    }

    /** A Sona peer is told, if read receipts are on. */
    suspend fun markRead(conversation: ConversationId) {
        val newly = lock.withLock { store.markRead(conversation) }
        if (newly.isEmpty()) return
        val owner = store.conversation(conversation) ?: return
        if (owner.peerKind != PeerKind.Sona) return
        if (!settings.observe(MessageSettings.ReadReceipts).first()) return
        val ids = newly.filter { it.attachment == null }.mapNotNull { it.envelopeId }
        sendReceipts(owner, read = true, ids)
    }

    /**
     * Call on every change of the text field; the peer is told at most every 4 seconds, if typing
     * indicators are on.
     */
    fun notifyTyping(conversation: ConversationId) {
        val now = clock()
        val last = typingSent[conversation]
        if (last != null && now - last < TYPING_EVERY.inWholeMilliseconds) return
        typingSent[conversation] = now
        scope.launch {
            val owner = store.conversation(conversation) ?: return@launch
            if (owner.peerKind != PeerKind.Sona) return@launch
            if (!settings.observe(MessageSettings.TypingIndicators).first()) return@launch
            courier.later(owner.account, owner.peer, Envelope.Typing, Courier.Once)
        }
    }

    fun uriOf(attachment: Attachment): String? = attachment.file?.let(files::uriOf)

    // --- Sending, in order ----------------------------------------------------------------------

    private suspend fun deliver(id: MessageId, resumed: Boolean) {
        val conversation = store.message(id)?.conversation ?: return
        outboxes.getOrPut(conversation) { Mutex() }.withLock { deliverNow(id, resumed) }
    }

    private suspend fun deliverNow(id: MessageId, resumed: Boolean) {
        val message = store.message(id) ?: return
        if (message.status != MessageStatus.Sending) return
        if (message.attachment?.state?.isFinished == true) return
        val owner = store.conversation(message.conversation) ?: return
        // Right after a start, or with the account registering, a moment is worth waiting.
        when {
            resumed -> courier.awaitRegistered(owner.account, RESUME_WAIT)
            courier.isRegistering(owner.account) ->
                courier.awaitRegistered(owner.account, REGISTERING_WAIT)
        }
        val bodies = bodiesOf(message, owner)
        for (index in message.partsSent until bodies.size) {
            val result = courier.send(owner.account, owner.peer, bodies[index], Courier.Texts)
            val failure = result.toFailure()
            lock.withLock {
                val now = store.message(id) ?: return
                if (failure != null) {
                    store.update(
                        now.copy(
                            status = MessageStatus.Failed,
                            failure = failure,
                            attachment = now.attachment?.copy(state = TransferState.Failed),
                        ),
                    )
                    return
                }
                val last = index == bodies.lastIndex
                store.update(
                    now.copy(
                        partsSent = index + 1,
                        status = if (last) now.status.atLeast(MessageStatus.Sent) else now.status,
                        attachment =
                            now.attachment?.let {
                                if (last && it.state == TransferState.Offering)
                                    it.copy(state = TransferState.WaitingForPeer)
                                else it
                            },
                    ),
                )
            }
        }
    }

    /** As first cut, so a resumed text goes on with the same parts. */
    private fun bodiesOf(message: Message, owner: Conversation): List<String> {
        val limit = message.sizeLimit ?: DEFAULT_LIMIT
        val attachment = message.attachment
        if (attachment != null) {
            val body =
                EnvelopeCodec.offer(transfers.offerOf(attachment), limit) { name ->
                    captions.caption(name, attachment.size)
                }
            return listOfNotNull(body)
        }
        return EnvelopeCodec.textParts(message.text, limit, message.envelopeId)
            ?: listOf(message.text)
    }

    // --- Receiving -------------------------------------------------------------------------------

    private suspend fun receive(received: ReceivedMessage) {
        val type = received.contentType.lowercase()
        if (!type.startsWith("text/")) return
        val all = accountList.filterNotNull().first()
        val owner =
            all.firstOrNull { it.id == received.account }
                ?: all.firstOrNull { it.isDefault }
                ?: all.firstOrNull()
                ?: return
        val peer = peerOf(received.from, owner)
        val envelope = EnvelopeCodec.decode(received.body)
        val content =
            envelope is Envelope.Plain || envelope is Envelope.Text || envelope is Envelope.Offer
        val found = lock.withLock {
            if (content) {
                store.openConversation(owner.id, peer, received.fromName, received.atMillis)
            } else {
                store.findConversation(owner.id, peer)
            }
        }
        if (found == null) {
            // A transfer nobody here knows of (its conversation was deleted): stop it.
            if (envelope is Envelope.Chunk) transfers.stranger(owner.id, peer, envelope.id)
            return
        }
        val conversation = learn(found, envelope, received.fromName)
        when (envelope) {
            is Envelope.Plain -> receivePlain(conversation, envelope.text, received.atMillis)
            is Envelope.Text -> receiveText(conversation, envelope, received.atMillis)
            is Envelope.Offer -> {
                stoppedTyping(conversation.id, sent = false)
                transfers.offered(conversation, envelope.offer, received.atMillis)
            }
            is Envelope.Accept -> transfers.accepted(conversation, envelope.id)
            is Envelope.Decline -> transfers.declined(conversation, envelope.id)
            is Envelope.Chunk -> transfers.chunk(conversation, envelope)
            is Envelope.Miss -> transfers.missing(conversation, envelope.id, envelope.ranges)
            is Envelope.Ack -> transfers.acked(conversation, envelope.id, envelope.received)
            is Envelope.Done -> transfers.done(conversation, envelope.id)
            is Envelope.Fail -> transfers.failed(conversation, envelope.id, envelope.reason)
            is Envelope.Cancel -> transfers.cancelled(conversation, envelope.id)
            is Envelope.Receipt -> receipts(conversation, envelope)
            Envelope.Typing -> typingFrom(conversation.id)
            Envelope.Hi -> Unit
        }
    }

    /**
     * An envelope makes the peer Sona, answered once with `hi` (unless it said `hi`); a plain text
     * from an unknown peer makes it plain.
     */
    private suspend fun learn(
        conversation: Conversation,
        envelope: Envelope,
        name: String?,
    ): Conversation {
        var next = conversation
        if (name != null && name != conversation.peerName) next = next.copy(peerName = name)
        if (envelope is Envelope.Plain) {
            if (conversation.peerKind == PeerKind.Unknown)
                next = next.copy(peerKind = PeerKind.Plain)
        } else if (conversation.peerKind != PeerKind.Sona) {
            next = next.copy(peerKind = PeerKind.Sona)
            if (envelope != Envelope.Hi) {
                courier.later(conversation.account, conversation.peer, Envelope.Hi, Courier.Once)
            }
        }
        if (next != conversation) lock.withLock { store.updateConversation(next) }
        return next
    }

    private suspend fun receivePlain(conversation: Conversation, text: String, at: Long) {
        stoppedTyping(conversation.id, sent = false)
        val message = lock.withLock {
            store.add(
                Message(
                    conversation = conversation.id,
                    direction = MessageDirection.Incoming,
                    text = text,
                    at = at,
                    status = MessageStatus.Received,
                ),
            )
        }
        announce(conversation, message)
    }

    private suspend fun receiveText(conversation: Conversation, text: Envelope.Text, at: Long) {
        val known = store.messageByEnvelope(conversation.id, MessageDirection.Incoming, text.id)
        if (known != null) {
            // Sent again: the sender did not hear the server's answer. It is here already.
            queueDelivered(conversation, text.id)
            return
        }
        val message =
            lock.withLock {
                val whole =
                    Message(
                        conversation = conversation.id,
                        direction = MessageDirection.Incoming,
                        text = text.text,
                        at = at,
                        status = MessageStatus.Received,
                        envelopeId = text.id,
                        parts = text.of,
                    )
                if (text.of == 1) return@withLock store.add(whole)
                val parts =
                    store.addPart(conversation.id, text.id, text.part, text.of, text.text, at)
                if (parts.size < text.of) return@withLock null
                store.completeText(
                    whole.copy(text = (1..text.of).joinToString("") { parts[it].orEmpty() }),
                )
            } ?: return
        stoppedTyping(conversation.id, sent = false)
        queueDelivered(conversation, text.id)
        announce(conversation, message)
    }

    private suspend fun announce(conversation: Conversation, message: Message) {
        val onScreen = visible.value == conversation.id
        _events.emit(
            MessengerEvent.Received(
                conversation =
                    ConversationSummary(
                        conversation = conversation,
                        name = nameOf.value(conversation.peer) ?: conversation.peerName,
                        last = message,
                    ),
                message = message,
                visible = onScreen,
            ),
        )
        if (onScreen) markRead(conversation.id)
    }

    private suspend fun receipts(conversation: Conversation, receipt: Envelope.Receipt) {
        val to = if (receipt.read) MessageStatus.Read else MessageStatus.Delivered
        lock.withLock {
            for (id in receipt.ids) {
                val message =
                    store.messageByEnvelope(conversation.id, MessageDirection.Outgoing, id)
                        ?: continue
                if (message.attachment != null) continue
                if (message.status.atLeast(to) != message.status) {
                    store.update(message.copy(status = to, failure = null))
                }
            }
        }
    }

    /** Delivered receipts go together, a moment after the texts they are for. */
    private suspend fun queueDelivered(conversation: Conversation, id: String) {
        val first = lock.withLock {
            val queue = deliveredQueue.getOrPut(conversation.id) { mutableSetOf() }
            queue.add(id)
            queue.size == 1
        }
        if (!first) return
        scope.launch {
            delay(RECEIPT_BATCH)
            val ids = lock.withLock { deliveredQueue.remove(conversation.id)?.toList() }
            if (!ids.isNullOrEmpty()) sendReceipts(conversation, read = false, ids)
        }
    }

    private suspend fun sendReceipts(conversation: Conversation, read: Boolean, ids: List<String>) {
        if (ids.isEmpty()) return
        val limit = sizeLimit(conversation.account)
        EnvelopeCodec.receiptsFitting(read, ids, limit).forEach {
            courier.later(conversation.account, conversation.peer, it, Courier.Receipts)
        }
    }

    private fun typingFrom(conversation: ConversationId) {
        typingJobs.remove(conversation)?.cancel()
        typingPeers.update { it + conversation }
        typingJobs[conversation] = scope.launch {
            delay(TYPING_SHOWN)
            typingPeers.update { it - conversation }
        }
    }

    /** The peer stopped typing (its message came), or the user did ([sent]: say so again soon). */
    private fun stoppedTyping(conversation: ConversationId, sent: Boolean) {
        if (sent) {
            typingSent.remove(conversation)
            return
        }
        typingJobs.remove(conversation)?.cancel()
        typingPeers.update { it - conversation }
    }

    // --- After a restart -------------------------------------------------------------------------

    private suspend fun resume() {
        for (message in store.unfinished()) {
            val attachment = message.attachment
            if (
                message.outgoing &&
                    message.status == MessageStatus.Sending &&
                    (attachment == null || attachment.state == TransferState.Offering)
            ) {
                scope.launch { deliver(message.id, resumed = true) }
            } else if (attachment != null) {
                transfers.resume(message)
            }
        }
    }

    // --- Shared ----------------------------------------------------------------------------------

    private suspend fun sizeLimit(account: AccountId): Int {
        val owner = accountList.value?.firstOrNull { it.id == account } ?: accounts.get(account)
        val overrides = owner?.overrides ?: Overrides()
        return effective.observe(MessageSettings.LargestMessage, overrides).first().bytes
    }

    private companion object {
        val TYPING_EVERY = 4.seconds
        val TYPING_SHOWN = 6.seconds
        val RECEIPT_BATCH = 400.milliseconds
        val RESUME_WAIT = 60.seconds
        val REGISTERING_WAIT = 10.seconds

        /** What a body was cut for when nothing says: Asterisk's safe size. */
        const val DEFAULT_LIMIT = 700
    }
}

/**
 * [address] as conversations tell peers apart. An extension of [account]'s server goes on the
 * account's domain without a port, whatever host the server wrote; any other keeps its port and its
 * host, lowercased.
 */
internal fun peerOf(address: SipAddress, account: Account): SipAddress {
    val host = address.host
    val own =
        host.equals(account.domain, ignoreCase = true) ||
            host.equals(account.server.host, ignoreCase = true)
    return if (own) SipAddress(user = address.user, host = account.domain)
    else SipAddress(user = address.user, host = host.lowercase(), port = address.port)
}

/** The later of the two, as an outgoing message only moves forward. */
internal fun MessageStatus.atLeast(other: MessageStatus): MessageStatus {
    fun rank(status: MessageStatus) =
        when (status) {
            MessageStatus.Sending -> 0
            MessageStatus.Failed -> 1
            MessageStatus.Sent -> 2
            MessageStatus.Delivered -> 3
            MessageStatus.Read -> 4
            MessageStatus.Received -> 5
        }
    return if (rank(other) > rank(this)) other else this
}
