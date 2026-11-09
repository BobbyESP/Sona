/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.ui

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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Whether Sona may reach the local network, where a lab's Asterisk usually is, and the way to ask.
 *
 * From Android 17 (for apps targeting it) traffic to local addresses is blocked unless the user
 * grants `ACCESS_LOCAL_NETWORK`; Linphone then fails to send with "Operation not permitted".
 * Before, [granted] is always true. Asked for in context (when an account is checked), never at
 * start.
 */
@Stable
class LocalNetworkAccess internal constructor(granted: Boolean) {
    var granted: Boolean by mutableStateOf(granted)
        internal set

    internal var launch: ((Boolean) -> Unit) -> Unit = { it(true) }

    /** Asks the user when needed, then calls [onResult] with the answer; at once when granted. */
    fun request(onResult: (granted: Boolean) -> Unit) {
        if (granted) onResult(true) else launch(onResult)
    }
}

@Composable
fun rememberLocalNetworkAccess(): LocalNetworkAccess {
    val context = LocalContext.current
    val access = remember { LocalNetworkAccess(context.hasLocalNetworkAccess()) }
    var pending by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            access.granted = granted
            pending?.invoke(granted)
            pending = null
        }
    access.launch = { onResult ->
        pending = onResult
        launcher.launch(LOCAL_NETWORK_PERMISSION)
    }
    // The user may grant or revoke it in the system's settings while away.
    LifecycleResumeEffect(context) {
        access.granted = context.hasLocalNetworkAccess()
        onPauseOrDispose {}
    }
    return access
}

private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

/** Android 17, where the permission is enforced for apps that target it. */
private const val ENFORCED_FROM_SDK = 37

private fun Context.hasLocalNetworkAccess(): Boolean =
    Build.VERSION.SDK_INT < ENFORCED_FROM_SDK ||
        ContextCompat.checkSelfPermission(this, LOCAL_NETWORK_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
