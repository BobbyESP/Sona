/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.calling.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.bobbyesp.sona.directory.domain.CallHistory
import com.bobbyesp.sona.directory.domain.CallRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Tells the user about calls they missed, and goes away once they have looked at Recents.
 *
 * It shows what the history says is unseen and missed, nothing of its own: a call missed while the
 * app was dead is still there to be told about, and opening Recents (which marks the history seen)
 * is what takes the notification down. One missed call has *Call back*; several are one summary
 * that opens the app.
 */
class MissedCallNotifications(
    private val context: Context,
    private val history: CallHistory,
    private val intents: CallScreenIntents,
    private val scope: CoroutineScope,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    /** Follows the unseen missed calls; once, at start. */
    fun start(): Job {
        ensureChannel()
        return scope.launch {
            history.unseenMissed.distinctUntilChanged().collect { missed -> show(missed) }
        }
    }

    private fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannel(
                    CHANNEL,
                    context.getString(R.string.channel_missed),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
                .apply { description = context.getString(R.string.channel_missed_description) },
        )
    }

    private fun show(missed: List<CallRecord>) {
        if (missed.isEmpty()) {
            manager.cancel(ID)
            return
        }
        val launch =
            PendingIntent.getActivity(
                context,
                REQUEST_OPEN,
                context.packageManager.getLaunchIntentForPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val latest = missed.first()
        val builder =
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_sona_call)
                .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
                .setContentIntent(launch)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setWhen(latest.startedAt)
                .setShowWhen(true)
        if (missed.size == 1) {
            val callBack =
                PendingIntent.getActivity(
                    context,
                    REQUEST_CALL_BACK,
                    intents.callBack(context, latest.address.uri, latest.account?.value),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            builder
                .setContentTitle(latest.title)
                .setContentText(context.getString(R.string.notification_missed))
                .addAction(0, context.getString(R.string.notification_missed_call_back), callBack)
        } else {
            val inbox = NotificationCompat.InboxStyle()
            missed.take(MAX_LINES).forEach { inbox.addLine(it.title) }
            builder
                .setContentTitle(
                    context.resources.getQuantityString(
                        R.plurals.notification_missed_many,
                        missed.size,
                        missed.size,
                    ),
                )
                .setContentText(missed.take(MAX_LINES).joinToString { it.title })
                .setStyle(inbox)
        }
        manager.notify(ID, builder.build())
    }

    private companion object {
        const val CHANNEL = "calls.missed"
        const val ID = 104
        const val REQUEST_OPEN = 6
        const val REQUEST_CALL_BACK = 7
        const val MAX_LINES = 5
    }
}
