/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.bobbyesp.sona.messaging.domain.ConversationId
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * A message notification's *Reply* and *Mark as read*. The work is the notifications'; this only
 * keeps the process alive for it ([goAsync]) and says which conversation.
 */
class MessageActionReceiver : BroadcastReceiver(), KoinComponent {
    private val notifications: MessageNotifications by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_CONVERSATION, -1L).takeIf { it >= 0 } ?: return
        val conversation = ConversationId(id)
        when (intent.action) {
            ACTION_REPLY -> {
                val text =
                    RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()
                val pending = goAsync()
                notifications.reply(conversation, text.orEmpty()) { pending.finish() }
            }
            ACTION_READ -> {
                val pending = goAsync()
                notifications.markRead(conversation) { pending.finish() }
            }
        }
    }

    internal companion object {
        const val ACTION_REPLY = "com.bobbyesp.sona.messaging.REPLY"
        const val ACTION_READ = "com.bobbyesp.sona.messaging.READ"
        const val EXTRA_CONVERSATION = "conversation"
        const val KEY_REPLY = "reply"
    }
}
