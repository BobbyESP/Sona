/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.bobbyesp.sona.messaging.domain.ConversationId
import com.bobbyesp.sona.messaging.domain.ConversationSummary
import com.bobbyesp.sona.messaging.domain.Message
import com.bobbyesp.sona.messaging.domain.Messenger
import com.bobbyesp.sona.messaging.domain.MessengerEvent
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** How a notification opens its conversation: the app's main activity, told which one. */
fun interface ConversationIntents {
    fun open(context: Context, conversation: ConversationId): Intent
}

/**
 * One messaging-style notification per conversation for what arrives while it is not on screen,
 * with *Reply* and *Mark as read*; taken down once read. Without the notifications permission
 * nothing is shown, and nothing fails.
 */
class MessageNotifications(
    private val context: Context,
    private val messenger: Messenger,
    private val intents: ConversationIntents,
    private val scope: CoroutineScope,
) {
    private val manager = NotificationManagerCompat.from(context)

    private val posted = ConcurrentHashMap.newKeySet<Long>()

    /**
     * Follows the messenger's events. Subscribed before it returns, so start it before the engine:
     * the messenger's events are not replayed.
     */
    fun start(): Job {
        ensureChannel()
        return scope.launch(start = CoroutineStart.UNDISPATCHED) {
            launch(start = CoroutineStart.UNDISPATCHED) {
                messenger.events.collect { event ->
                    if (event is MessengerEvent.Received && !event.visible) {
                        runCatching { show(event.conversation) }
                            .onFailure { Log.w(TAG, "Could not notify a message", it) }
                    }
                }
            }
            launch {
                // Read elsewhere (the app, another notification's action), or deleted: taken down.
                messenger.conversations.collect { all ->
                    val unread = all.filter { it.unread > 0 }.map { it.id.value }.toSet()
                    posted.filter { it !in unread }.forEach { cancel(ConversationId(it)) }
                }
            }
        }
    }

    fun reply(conversation: ConversationId, text: String, done: () -> Unit) {
        scope.launch {
            try {
                if (text.isNotBlank()) messenger.sendText(conversation, text)
                messenger.markRead(conversation)
                cancel(conversation)
            } finally {
                done()
            }
        }
    }

    fun markRead(conversation: ConversationId, done: () -> Unit) {
        scope.launch {
            try {
                messenger.markRead(conversation)
                cancel(conversation)
            } finally {
                done()
            }
        }
    }

    private fun cancel(conversation: ConversationId) {
        posted.remove(conversation.value)
        manager.cancel(TAG, conversation.notificationId)
    }

    private suspend fun show(summary: ConversationSummary) {
        if (!manager.areNotificationsEnabled()) return
        val id = summary.id
        val unread =
            messenger.messages(id).first().filter { !it.outgoing && !it.read }.takeLast(MAX_LINES)
        if (unread.isEmpty()) return
        val peer = summary.conversation.peer
        val name = summary.name ?: peer.user ?: peer.uri
        val sender = Person.Builder().setName(name).setKey(peer.uri).build()
        val me = Person.Builder().setName(context.getString(R.string.notification_you)).build()
        val style = NotificationCompat.MessagingStyle(me).setGroupConversation(false)
        unread.forEach { style.addMessage(it.words(), it.at, sender) }

        val builder =
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_sona_message)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setStyle(style)
                .setContentTitle(name)
                .setContentText(unread.last().words())
                .setNumber(summary.unread)
                .setWhen(unread.last().at)
                .setShowWhen(true)
                .setAutoCancel(true)
                .setContentIntent(open(id))
                .addAction(replyAction(id))
                .addAction(
                    NotificationCompat.Action.Builder(
                            0,
                            context.getString(R.string.notification_mark_read),
                            broadcast(id, MessageActionReceiver.ACTION_READ, mutable = false),
                        )
                        .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                        .setShowsUserInterface(false)
                        .build(),
                )
        try {
            manager.notify(TAG, id.notificationId, builder.build())
            posted += id.value
        } catch (e: SecurityException) {
            // The permission was taken back between the check and now.
            Log.w(TAG, "Not allowed to notify", e)
        }
    }

    private fun replyAction(id: ConversationId): NotificationCompat.Action {
        val input =
            RemoteInput.Builder(MessageActionReceiver.KEY_REPLY)
                .setLabel(context.getString(R.string.notification_reply_hint))
                .build()
        return NotificationCompat.Action.Builder(
                0,
                context.getString(R.string.notification_reply),
                broadcast(id, MessageActionReceiver.ACTION_REPLY, mutable = true),
            )
            .addRemoteInput(input)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
    }

    private fun open(id: ConversationId): PendingIntent =
        PendingIntent.getActivity(
            context,
            id.requestCode(REQUEST_OPEN),
            intents.open(context, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** An action's intent, explicit (a mutable one must be): the reply's text is filled in. */
    private fun broadcast(id: ConversationId, action: String, mutable: Boolean): PendingIntent {
        val intent =
            Intent(context, MessageActionReceiver::class.java)
                .setAction(action)
                .setPackage(context.packageName)
                .putExtra(MessageActionReceiver.EXTRA_CONVERSATION, id.value)
        val mutability =
            if (mutable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_MUTABLE
            else if (mutable) 0 else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(
            context,
            id.requestCode(
                if (action == MessageActionReceiver.ACTION_REPLY) REQUEST_REPLY else REQUEST_READ,
            ),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutability,
        )
    }

    private fun Message.words(): String {
        val file = attachment ?: return text
        return if (file.isPicture) context.getString(R.string.notification_picture)
        else context.getString(R.string.notification_file, file.name)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        system.createNotificationChannel(
            NotificationChannel(
                    CHANNEL,
                    context.getString(R.string.channel_messages),
                    NotificationManager.IMPORTANCE_HIGH,
                )
                .apply { description = context.getString(R.string.channel_messages_description) },
        )
    }

    private val ConversationId.notificationId: Int
        get() = value.toInt()

    /** Each conversation's actions their own request code, so none replaces another's extras. */
    private fun ConversationId.requestCode(kind: Int): Int = (value.toInt() shl 2) or kind

    private companion object {
        const val TAG = "messages"
        const val CHANNEL = "messages"
        const val MAX_LINES = 6
        const val REQUEST_OPEN = 0
        const val REQUEST_REPLY = 1
        const val REQUEST_READ = 2
    }
}
