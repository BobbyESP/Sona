/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.calling.platform

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.bobbyesp.sona.accounts.domain.AccountsRepository
import com.bobbyesp.sona.calling.domain.Call
import com.bobbyesp.sona.calling.domain.CallCoordinator
import com.bobbyesp.sona.calling.domain.CallForeground
import com.bobbyesp.sona.calling.domain.CallState
import com.bobbyesp.sona.core.model.CallId
import com.bobbyesp.sona.core.model.settings.CallSettings
import com.bobbyesp.sona.core.model.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val TAG = "SonaServices"

/**
 * The foreground service of calls in progress, as Telecom requires from Android 14: started when a
 * call begins, with its `CallStyle` notification, and stopped when none is left. It shows the call
 * that matters most: one ringing, else one active, else the latest.
 *
 * Its type is `phoneCall`, plus `microphone` once a call was dialed or answered, plus `camera`
 * while one is in use. Android may refuse the last two: the call goes on without them
 * (docs/calling.md).
 */
class CallService : LifecycleService(), KoinComponent {
    private val coordinator: CallCoordinator by inject()
    private val accounts: AccountsRepository by inject()
    private val notifications: CallNotifications by inject()

    private var shownId: Int? = null

    override fun onCreate() {
        super.onCreate()
        notifications.ensureChannels()
        lifecycleScope.launch {
            combine(coordinator.calls, coordinator.foreground, accounts.accounts) {
                    calls,
                    foreground,
                    all ->
                    Triple(calls, foreground, all)
                }
                .collect { (calls, foreground, all) ->
                    val call = calls.mostImportant()
                    if (call == null) {
                        stop()
                    } else {
                        val label = all.firstOrNull { it.id == call.account }?.label
                        val uses = foreground as? CallForeground.Calls
                        show(call, label, uses?.microphone == true, uses?.camera == true)
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val call = coordinator.calls.value.mostImportant()
        if (call == null) stop() else show(call, null, microphone = false, camera = false)
        return START_NOT_STICKY
    }

    private fun show(call: Call, accountLabel: String?, microphone: Boolean, camera: Boolean) {
        val (id, notification) = notifications.forCall(call, accountLabel)
        val withMicrophone = microphone && granted(Manifest.permission.RECORD_AUDIO)
        val withCamera = camera && granted(Manifest.permission.CAMERA)
        // What Android refuses is dropped, the camera first: the voice matters more.
        val attempts =
            listOf(withMicrophone to withCamera, withMicrophone to false, false to false).distinct()
        for ((index, attempt) in attempts.withIndex()) {
            val (mic, cam) = attempt
            try {
                ServiceCompat.startForeground(this, id, notification, types(mic, cam))
                break
            } catch (error: Exception) {
                if (index == attempts.lastIndex) throw error
                Log.w(TAG, "Foreground with microphone $mic, camera $cam refused", error)
            }
        }
        shownId?.takeIf { it != id }?.let(notifications::cancel)
        shownId = id
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        shownId?.let(notifications::cancel)
        shownId = null
        stopSelf()
    }

    private fun types(microphone: Boolean, camera: Boolean): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return 0
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        if (microphone) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (camera) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        return types
    }

    private fun List<Call>.mostImportant(): Call? =
        firstOrNull { it.state == CallState.Incoming }
            ?: firstOrNull { it.state == CallState.Active }
            ?: lastOrNull { !it.isOver }
            ?: lastOrNull()
}

/**
 * Keeps the accounts registered while the app is closed, with a quiet notification: Asterisk does
 * not push, so the only way to ring is to stay registered. Type `specialUse`, the one Android
 * allows to start on boot (docs/calling.md, Staying reachable).
 */
class KeepAliveService : LifecycleService(), KoinComponent {
    private val accounts: AccountsRepository by inject()
    private val notifications: CallNotifications by inject()

    override fun onCreate() {
        super.onCreate()
        notifications.ensureChannels()
        lifecycleScope.launch {
            accounts.accounts
                .map { all -> all.filter { it.enabled }.map { it.label } }
                .distinctUntilChanged()
                .collect { labels ->
                    this@KeepAliveService.labels = labels
                    if (labels.isNotEmpty()) show(labels)
                }
        }
    }

    private var labels: List<String> = emptyList()

    /**
     * Also when it is already running: the app starts it again as it comes back to the front, so
     * the notification is posted once more. One posted while notifications were not allowed was
     * dropped by the system, and nothing else would bring it back after the user allows them.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        show(labels)
        return START_STICKY
    }

    private fun show(labels: List<String>) {
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            else 0
        ServiceCompat.startForeground(
            this,
            CallNotifications.REACHABLE_ID,
            notifications.reachable(labels.ifEmpty { listOf("Sona") }),
            type,
        )
    }
}

/**
 * Starts and stops the services from the state of things: [CallService] while a call exists,
 * [KeepAliveService] while *Always reachable* is on and an account is enabled. Started once, in the
 * application's scope.
 */
class ServiceController(
    private val context: Context,
    private val coordinator: CallCoordinator,
    private val accounts: AccountsRepository,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            coordinator.foreground
                .map { it != CallForeground.None }
                .distinctUntilChanged()
                // CallService stops itself once the last call is gone.
                .collect { hasCalls -> if (hasCalls) startForeground(CallService::class.java) }
        }
        scope.launch {
            combine(settings.observe(CallSettings.AlwaysReachable), accounts.accounts) { on, all ->
                    on && all.any { it.enabled }
                }
                .distinctUntilChanged()
                .collect { keep ->
                    if (keep) startForeground(KeepAliveService::class.java)
                    else context.stopService(Intent(context, KeepAliveService::class.java))
                }
        }
    }

    /**
     * The app is back in front: the keep-alive notification is posted again when it should be
     * there, in case it could not be shown before (notifications allowed since).
     */
    fun refresh() {
        scope.launch {
            val keep =
                settings.observe(CallSettings.AlwaysReachable).first() &&
                    accounts.accounts.first().any { it.enabled }
            if (keep) startForeground(KeepAliveService::class.java)
        }
    }

    private fun startForeground(service: Class<*>) {
        try {
            ContextCompat.startForegroundService(context, Intent(context, service))
        } catch (error: Exception) {
            // Android 12+ refuses a foreground service started from the background without an
            // exemption: the keep-alive service is what normally provides it.
            Log.w(TAG, "Could not start ${service.simpleName}", error)
        }
    }
}

/**
 * The notification's *Decline* and *Hang up*, and Picture-in-Picture's *Mute* and *Hang up*,
 * carried out by the coordinator.
 */
class CallActionReceiver : BroadcastReceiver(), KoinComponent {
    private val coordinator: CallCoordinator by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val call = intent.getStringExtra(EXTRA_CALL)?.let(::CallId) ?: return
        when (intent.action) {
            ACTION_DECLINE -> coordinator.decline(call)
            ACTION_HANG_UP -> coordinator.hangUp(call)
            ACTION_TOGGLE_MUTE -> {
                val muted = coordinator.calls.value.firstOrNull { it.id == call }?.muted ?: return
                coordinator.setMuted(call, !muted)
            }
        }
    }

    companion object {
        const val ACTION_DECLINE = "com.bobbyesp.sona.call.DECLINE"
        const val ACTION_HANG_UP = "com.bobbyesp.sona.call.HANG_UP"
        const val ACTION_TOGGLE_MUTE = "com.bobbyesp.sona.call.TOGGLE_MUTE"
        const val EXTRA_CALL = "call"

        /**
         * A broadcast of [action] on [call], for surfaces outside the notification; [request] tells
         * their pending intents apart.
         */
        fun pendingIntent(
            context: Context,
            action: String,
            call: CallId,
            request: Int,
        ): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                request,
                Intent(context, CallActionReceiver::class.java)
                    .setAction(action)
                    .putExtra(EXTRA_CALL, call.value),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}

/**
 * Exists to be woken after a restart: starting the app starts the engine and [ServiceController],
 * which starts the keep-alive service. It never starts [CallService]: Android 15 forbids a
 * `phoneCall` service from `BOOT_COMPLETED`.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
