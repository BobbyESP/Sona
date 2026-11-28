/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.calling.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bobbyesp.sona.accounts.domain.AccountsRepository
import com.bobbyesp.sona.core.model.settings.CallSettings
import com.bobbyesp.sona.core.model.settings.SettingsStore
import com.bobbyesp.sona.core.navigation.keys.AccountSwitcher
import com.bobbyesp.sona.core.ui.contribution.ReadinessCheck
import com.bobbyesp.sona.core.ui.contribution.ReadinessFix
import com.bobbyesp.sona.core.ui.contribution.ReadinessState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/*
 * What the system must allow for calls to ring and be heard (docs/calling.md, Call readiness).
 * Here because each asks Android something, and the incoming channel is this module's. The app
 * binds them; the readiness screen and the account chip only see `ReadinessCheck`.
 */

/** Every check of the calling side, for the app to bind. */
fun callReadinessChecks(
    context: Context,
    accounts: AccountsRepository,
    settings: SettingsStore,
    notifications: CallNotifications,
): List<ReadinessCheck> =
    listOf(
        AccountCheck(accounts),
        MicrophoneCheck(context),
        NotificationsCheck(context),
        IncomingChannelCheck(context, notifications),
        FullScreenCheck(context),
        BatteryCheck(context, settings),
    )

private fun Boolean.readyOrProblem() = if (this) ReadinessState.Ready else ReadinessState.Problem

private fun Context.packageUri(): Uri = Uri.fromParts("package", packageName, null)

/** Without an account that is on, nothing can ring. */
private class AccountCheck(private val accounts: AccountsRepository) : ReadinessCheck {
    override val id = "account"
    override val order = 10
    override val title = R.string.readiness_account
    override val problem = R.string.readiness_account_problem
    override val fixLabel = R.string.readiness_account_fix
    override val fix = ReadinessFix.Open(AccountSwitcher)
    override val changes: Flow<*> = accounts.accounts

    override suspend fun check() = accounts.accounts.first().any { it.enabled }.readyOrProblem()
}

private class MicrophoneCheck(private val context: Context) : ReadinessCheck {
    override val id = "microphone"
    override val order = 20
    override val title = R.string.readiness_microphone
    override val problem = R.string.readiness_microphone_problem
    override val fixLabel = R.string.readiness_allow
    override val fix = ReadinessFix.AskPermissions(listOf(Manifest.permission.RECORD_AUDIO))

    override suspend fun check() =
        (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED)
            .readyOrProblem()
}

/**
 * Notifications are how a call rings, and how a missed one and the keep-alive service are shown.
 * From Android 13 they are a permission to ask for; before, a switch in the system's settings.
 */
private class NotificationsCheck(private val context: Context) : ReadinessCheck {
    override val id = "notifications"
    override val order = 30
    override val title = R.string.readiness_notifications
    override val problem = R.string.readiness_notifications_problem
    override val fixLabel = R.string.readiness_allow
    override val fix: ReadinessFix =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ReadinessFix.AskPermissions(listOf(Manifest.permission.POST_NOTIFICATIONS))
        } else {
            ReadinessFix.SystemSettings(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }

    override suspend fun check() =
        NotificationManagerCompat.from(context).areNotificationsEnabled().readyOrProblem()
}

/** The user can silence the incoming channel alone, and calls then arrive without a sound. */
private class IncomingChannelCheck(
    private val context: Context,
    private val notifications: CallNotifications,
) : ReadinessCheck {
    override val id = "incoming-channel"
    override val order = 40
    override val title = R.string.readiness_channel
    override val problem = R.string.readiness_channel_problem
    override val fixLabel = R.string.readiness_open_settings
    // A VoIP app that must ring while idle is an accepted use of the exemption.
    @SuppressLint("BatteryLife")
    override val fix =
        ReadinessFix.SystemSettings(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, CallNotifications.INCOMING),
        )

    override suspend fun check(): ReadinessState {
        val manager = NotificationManagerCompat.from(context)
        // With every notification off, that is the problem to show, not this one under it.
        if (!manager.areNotificationsEnabled()) return ReadinessState.NotApplicable
        notifications.ensureChannels()
        val channel =
            manager.getNotificationChannel(CallNotifications.INCOMING)
                ?: return ReadinessState.NotApplicable
        return (channel.importance >= NotificationManager.IMPORTANCE_DEFAULT).readyOrProblem()
    }
}

/**
 * From Android 14 showing a call over the lock screen is a permission of its own, which the user
 * (or the system, for an app that is not a phone) can take away: the call then is only a banner.
 */
private class FullScreenCheck(private val context: Context) : ReadinessCheck {
    override val id = "full-screen"
    override val order = 50
    override val title = R.string.readiness_full_screen
    override val problem = R.string.readiness_full_screen_problem
    override val fixLabel = R.string.readiness_open_settings
    override val fix: ReadinessFix =
        ReadinessFix.SystemSettings(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, context.packageUri())
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, context.packageUri())
            },
        )

    override suspend fun check(): ReadinessState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return ReadinessState.NotApplicable
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.canUseFullScreenIntent().readyOrProblem()
    }
}

/**
 * Battery optimization lets the system stop the service that keeps the accounts registered, and
 * calls then stop ringing some time after the app is closed. Only a question while *Always
 * reachable* is on.
 */
private class BatteryCheck(
    private val context: Context,
    private val settings: SettingsStore,
) : ReadinessCheck {
    override val id = "battery"
    override val order = 60
    override val title = R.string.readiness_battery
    override val problem = R.string.readiness_battery_problem
    override val fixLabel = R.string.readiness_allow
    // A VoIP app that must ring while idle is an accepted use of the exemption.
    @SuppressLint("BatteryLife")
    override val fix =
        ReadinessFix.SystemSettings(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, context.packageUri()),
        )
    override val changes: Flow<*> = settings.observe(CallSettings.AlwaysReachable)

    override suspend fun check(): ReadinessState {
        if (!settings.observe(CallSettings.AlwaysReachable).first()) {
            return ReadinessState.NotApplicable
        }
        val power = context.getSystemService(PowerManager::class.java)
        return power.isIgnoringBatteryOptimizations(context.packageName).readyOrProblem()
    }
}
