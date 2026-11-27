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
 * Texts, pictures and files between extensions, over SIP `MESSAGE`: the one place that sends,
 * receives and keeps them. It lives in the application's scope, like the call coordinator: start
 * it once at app start, **before the engine**, so nothing the engine receives waits unread.
 * Screens observe its flows and call its functions; notifications follow [events]. See
 * docs/messaging.md.
 *
 * - **Plain peers** get the text as typed, cut at word boundaries when longer than the account's
 *   server relays. **Sona peers** (they sent an envelope) get texts with ids, and receipts and
 *   typing when the settings allow; only they can take a file.
 * - **Nothing is lost on a restart.** What is sending, offered or in transfer is in the store, and
 *   [start] resumes it once the account is registered again.
 * - **Every failure is explained**: a [MessageFailure] with the server's status for *Show SIP
 *   details*.
 *
 * Everything that changes a message goes through one lock; the network is never waited for inside
 * it. The texts of a conversation go out one after the other, in the order they were written.
 *
 * @param clock The time messages are stamped with, in epoch milliseconds.
 * @param newId The Sona ids of texts and files: random base32 in the app.
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

    /** Every conversation, the most recent first, with who the peer is and what is unread. */
    val conversations: Flow<List<ConversationSummary>> =
        combine(store.conversations, nameOf) { list, name ->
            list.map { it.copy(name = name(it.conversation.peer) ?: it.conversation.peerName) }
        }

    /** One conversation as [conversations] has it; `null` once it is deleted. */
    fun conversation(id: ConversationId): Flow<ConversationSummary?> =
        conversations.map { list -> list.firstOrNull { it.id == id } }.distinctUntilChanged()

    /** The messages of [id], oldest first. */
    fun messages(id: ConversationId): Flow<List<Message>> = store.messages(id)

    /** What is unread in every conversation: the Messages tab's badge. */
    val totalUnread: Flow<Int> = store.unread

    private val typingPeers = MutableStateFlow<Set<ConversationId>>(emptySet())
    private val typingJobs = ConcurrentHashMap<ConversationId, Job>()
    private val typingSent = ConcurrentHashMap<ConversationId, Long>()

    /** Whether the peer of [id] is writing now (a Sona peer says so; shown for 6 seconds). */
    fun typing(id: ConversationId): Flow<Boolean> =
        typingPeers.map { id in it }.distinctUntilChanged()

    /** Delivered receipts waiting to go together, by conversation. Touched under [lock]. */
    private val deliveredQueue = mutableMapOf<ConversationId, MutableSet<String>>()

    /** The texts of each conversation go out one at a time, in order. */
    private val outboxes = ConcurrentHashMap<ConversationId, Mutex>()

    /**
     * Starts receiving, and resumes what a previous run left unfinished. Once, at app start, before
     * the engine starts.
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

    /**
     * The conversation of [account] with [peer], made if there is none: for *New conversation*, a
     * contact's *Message*, a call's. `null` when the account does not exist.
     */
    suspend fun open(account: AccountId, peer: SipAddress): ConversationId? {
        val owner = accountList.filterNotNull().first().firstOrNull { it.id == account }
        owner ?: return null
        return lock.withLock { store.openConversation(account, peerOf(peer, owner), null, clock()) }
            .id
    }

    /**
     * Says which conversation is on screen, or `null` when none is (the screen left, the app went to
     * the background). What arrives in it is read at once, and its notification is not needed.
     */
    fun setVisibleConversation(id: ConversationId?) {
        visible.value = id
        if (id != null) scope.launch { markRead(id) }
    }

    // --- Texts -----------------------------------------------------------------------------------

    /** Writes [text] in [conversation] and sends it. */
    suspend fun sendText(conversation: ConversationId, text: String): SendOutcome {
        if (text.isBlank()) return SendOutcome.Empty
        val owner = store.conversation(conversation) ?: return SendOutcome.NoConversation
        val limit = sizeLimit(owner.account)
        val id = if (owner.peerKind == PeerKind.Sona) newId() else null
        val parts =
            EnvelopeCodec.textParts(text, limit, id)
                ?: return SendOutcome.TooLong(text.encodeToByteArray().size, limit)
        stoppedTyping(conversation, sent = true)
        val message =
            lock.withLock {
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
     * Prepares the file at [source] (a `content://` address the user picked), offers it in
     * [conversation] and sends it once the other phone accepts.
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
                is PreparedAttachment.Unreadable -> return SendOutcome.FileUnreadable(prepared.detail)
            }
        val sizeLimit = sizeLimit(owner.account)
        val chunk = EnvelopeCodec.chunkSize(sizeLimit)
        val id = newId()
        val message =
            lock.withLock {
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
     * Tries again what failed: a text or an offer is sent again from the part that failed; a file
     * transfer, sent or received, resumes where it stopped.
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

    /** Downloads a file offered in a conversation, larger than the automatic limit. */
    suspend fun download(id: MessageId) = transfers.download(id)

    /** Refuses a file offered: the sender is told. */
    suspend fun decline(id: MessageId) = transfers.decline(id)

    /** Stops a file's transfer, either way: the other side is told. */
    suspend fun cancel(id: MessageId) = transfers.cancel(id)

    /** Deletes messages, their files with them; a transfer in progress is stopped first. */
    suspend fun delete(ids: Collection<MessageId>) {
        for (id in ids) store.message(id)?.let { transfers.forget(it) }
        lock.withLock { store.delete(ids) }
    }

    /** Deletes a conversation, every message and file in it. */
    suspend fun deleteConversation(id: ConversationId) {
        store.messages(id).first().forEach { transfers.forget(it) }
        lock.withLock { store.deleteConversation(id) }
    }

    /**
     * Marks what arrived in [conversation] read, and tells a Sona peer so when the user allows read
     * receipts.
     */
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
     * The user is writing in [conversation]: a Sona peer is told, at most every 4 seconds, when the
     * user allows typing indicators. Call it on every change of the text field.
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

    /** A `content://` address to open or share [attachment]'s file, once it is in storage. */
    fun uriOf(attachment: Attachment): String? = attachment.file?.let(files::uriOf)

    // --- Sending, in order ----------------------------------------------------------------------

    private suspend fun deliver(id: MessageId, resumed: Boolean) {
        val conversation = store.message(id)?.conversation ?: return
        outboxes.getOrPut(conversation) { Mutex() }.withLock { deliverNow(id, resumed) }
    }

    /** Sends what is left of a text or an offer, part by part. */
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

    /** The bodies a text or an offer goes in, as they were first cut. */
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
        val found =
            lock.withLock {
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
     * What [envelope] says of the peer: an envelope makes it a Sona peer, answered once with `hi`
     * (unless it said `hi` itself); a plain text from an unknown one makes it plain.
     */
    private suspend fun learn(
        conversation: Conversation,
        envelope: Envelope,
        name: String?,
    ): Conversation {
        var next = conversation
        if (name != null && name != conversation.peerName) next = next.copy(peerName = name)
        if (envelope is Envelope.Plain) {
            if (conversation.peerKind == PeerKind.Unknown) next = next.copy(peerKind = PeerKind.Plain)
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
        val message =
            lock.withLock {
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
        val known =
            store.messageByEnvelope(conversation.id, MessageDirection.Incoming, text.id)
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

    /** Tells the screens and the notifications; what arrives on screen is read at once. */
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
        val first =
            lock.withLock {
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
            if (message.outgoing && message.status == MessageStatus.Sending &&
                (attachment == null || attachment.state == TransferState.Offering)) {
                scope.launch { deliver(message.id, resumed = true) }
            } else if (attachment != null) {
                transfers.resume(message)
            }
        }
    }

    // --- Shared ----------------------------------------------------------------------------------

    /** The longest body [account]'s server relays: its own setting, or the app's. */
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
 * [address] as conversations tell peers apart: an extension of [account]'s own server is on the
 * account's domain whatever host the server wrote (its address, or its name), without a port;
 * another server's address keeps its host, in lowercase, and its port.
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
