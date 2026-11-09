/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.calling.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.bobbyesp.sona.calling.domain.Call
import com.bobbyesp.sona.calling.domain.CallState
import com.bobbyesp.sona.core.model.CallId

/**
 * Where the platform opens the call screen. The app implements it: this module does not know its
 * activities.
 */
interface CallScreenIntents {
    /** The call screen, over the lock screen. */
    fun screen(context: Context): Intent

    /** The call screen, answering [call] as it opens: the notification's *Answer*. */
    fun answer(context: Context, call: CallId): Intent
}

/**
 * The calls' notifications. They are Android's `CallStyle`, which the system shows as a call, and
 * the foreground notification of [CallService], as Telecom requires.
 *
 * **Ringing is the incoming channel's sound**, insistent until answered: the system then applies Do
 * not disturb, the ringer volume and vibration itself, and the user can change the ringtone in the
 * channel's settings.
 */
class CallNotifications(private val context: Context, private val intents: CallScreenIntents) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val incoming =
            NotificationChannel(
                    INCOMING,
                    context.getString(R.string.channel_incoming),
                    NotificationManager.IMPORTANCE_HIGH,
                )
                .apply {
                    description = context.getString(R.string.channel_incoming_description)
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 800, 600, 800, 600)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
        val ongoing =
            NotificationChannel(
                    ONGOING,
                    context.getString(R.string.channel_ongoing),
                    NotificationManager.IMPORTANCE_LOW,
                )
                .apply { setSound(null, null) }
        val reachable =
            NotificationChannel(
                    REACHABLE,
                    context.getString(R.string.channel_reachable),
                    NotificationManager.IMPORTANCE_MIN,
                )
                .apply {
                    description = context.getString(R.string.channel_reachable_description)
                    setSound(null, null)
                    setShowBadge(false)
                }
        manager.createNotificationChannels(listOf(incoming, ongoing, reachable))
    }

    /** The notification for [call], and its id: ringing and ongoing calls use different ones. */
    fun forCall(call: Call, accountLabel: String?): Pair<Int, Notification> {
        val person = Person.Builder().setName(call.title).setImportant(true).build()
        val screen = activity(intents.screen(context), REQUEST_SCREEN)
        return if (call.state == CallState.Incoming) {
            val answer = activity(intents.answer(context, call.id), REQUEST_ANSWER)
            val decline = broadcast(CallActionReceiver.ACTION_DECLINE, call.id, REQUEST_DECLINE)
            val notification =
                NotificationCompat.Builder(context, INCOMING)
                    .setSmallIcon(R.drawable.ic_sona_call)
                    .setContentTitle(call.title)
                    .setContentText(
                        accountLabel?.let {
                            context.getString(R.string.notification_incoming_on, it)
                        } ?: context.getString(R.string.notification_incoming),
                    )
                    .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, decline, answer))
                    .setFullScreenIntent(screen, true)
                    .setContentIntent(screen)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setOngoing(true)
                    .build()
                    // Rings until answered, declined or gone.
                    .apply { flags = flags or Notification.FLAG_INSISTENT }
            INCOMING_ID to notification
        } else {
            val hangUp = broadcast(CallActionReceiver.ACTION_HANG_UP, call.id, REQUEST_HANG_UP)
            val builder =
                NotificationCompat.Builder(context, ONGOING)
                    .setSmallIcon(R.drawable.ic_sona_call)
                    .setContentTitle(call.title)
                    .setContentText(context.getString(call.state.textRes))
                    .setStyle(NotificationCompat.CallStyle.forOngoingCall(person, hangUp))
                    .setContentIntent(screen)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setOngoing(true)
                    .setSilent(true)
            call.connectedAt?.let { builder.setWhen(it).setUsesChronometer(true).setShowWhen(true) }
            ONGOING_ID to builder.build()
        }
    }

    fun reachable(labels: List<String>): Notification =
        NotificationCompat.Builder(context, REACHABLE)
            .setSmallIcon(R.drawable.ic_sona_call)
            .setContentTitle(
                if (labels.size == 1)
                    context.getString(R.string.notification_reachable, labels.single())
                else context.getString(R.string.notification_reachable_many, labels.size),
            )
            .setContentText(context.getString(R.string.notification_reachable_text))
            .setContentIntent(activity(intents.screen(context).appLaunch(context), REQUEST_APP))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

    fun cancel(id: Int) = manager.cancel(id)

    private fun activity(intent: Intent, request: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            request,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun broadcast(action: String, call: CallId, request: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            request,
            Intent(context, CallActionReceiver::class.java)
                .setAction(action)
                .putExtra(CallActionReceiver.EXTRA_CALL, call.value),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** The app itself, for the keep-alive notification: its launcher activity. */
    private fun Intent.appLaunch(context: Context): Intent =
        context.packageManager.getLaunchIntentForPackage(context.packageName) ?: this

    private val CallState.textRes: Int
        get() =
            when (this) {
                CallState.Dialing -> R.string.notification_calling
                CallState.RingingRemote -> R.string.notification_ringing
                CallState.OnHold,
                CallState.HeldByRemote -> R.string.notification_on_hold
                is CallState.Ended -> R.string.notification_ended
                else -> R.string.notification_active
            }

    companion object {
        const val INCOMING = "calls.incoming"
        const val ONGOING = "calls.ongoing"
        const val REACHABLE = "calls.reachable"

        const val INCOMING_ID = 101
        const val ONGOING_ID = 102
        const val REACHABLE_ID = 103

        private const val REQUEST_SCREEN = 1
        private const val REQUEST_ANSWER = 2
        private const val REQUEST_DECLINE = 3
        private const val REQUEST_HANG_UP = 4
        private const val REQUEST_APP = 5
    }
}
