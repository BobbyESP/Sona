/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * What every place that starts a call needs first: the microphone, and notifications to show the
 * call. Asked for in context, at the first call, never at start. Whatever the user answers, the
 * call goes on: without the microphone it is silent on this side, and the call screen says so.
 */
@Stable
class CallStarter internal constructor(private val hasMicrophone: () -> Boolean) {
    internal var launch: () -> Unit = {}
    private var pending: (() -> Unit)? by mutableStateOf(null)

    /** Runs [call] now when the permissions are there, after the user answers when they are not. */
    fun start(call: () -> Unit) {
        if (hasMicrophone()) call()
        else {
            pending = call
            launch()
        }
    }

    internal fun answered() {
        pending?.invoke()
        pending = null
    }
}

@Composable
fun rememberCallStarter(): CallStarter {
    val context = LocalContext.current
    val starter = remember { CallStarter { context.canRecord() } }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            starter.answered()
        }
    starter.launch = { launcher.launch(callPermissions()) }
    return starter
}

private fun Context.canRecord() =
    checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun callPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    else arrayOf(Manifest.permission.RECORD_AUDIO)
